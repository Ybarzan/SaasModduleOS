#!/usr/bin/env bash
#
# Déploiement one-shot de Fleet Hub sur un serveur Ubuntu/Debian (x86_64).
#
# Usage :
#   sudo DOMAIN=demo.monentreprise.fr ./deploy.sh
#
# Prérequis :
#   - un VPS Ubuntu 22.04/24.04 (ou Debian 12) accessible en SSH
#   - le DNS de DOMAIN pointant vers l'IP du VPS (A / AAAA) AVANT de lancer
#     ce script (Caddy ne peut pas obtenir le certificat Let's Encrypt sinon)
#
# Ce script :
#   1. installe Docker + le plugin compose s'ils manquent
#   2. clone (ou met à jour) le dépôt dans /opt/fleethub
#   3. crée /opt/fleethub/.env s'il n'existe pas, avec des secrets aléatoires
#      et les URLs publiques dérivées de DOMAIN
#   4. docker compose up -d --build (Caddy, PostgreSQL, backend, frontend, backup)
#
# Variables utiles (optionnelles) :
#   DOMAIN         domaine public (défaut : localhost, certificat interne)
#   REPO_URL       URL du dépôt (défaut : https://github.com/Ybarzan/fleet-hub.git)
#   REPO_BRANCH    branche à déployer (défaut : master)
#   ADMIN_PASSWORD mot de passe des comptes saasadmin/admin (généré si absent)
#   GESTIONNAIRE_PASSWORD mot de passe du compte gestionnaire (généré si absent)

set -euo pipefail

DOMAIN="${DOMAIN:-localhost}"
REPO_URL="${REPO_URL:-https://github.com/Ybarzan/fleet-hub.git}"
REPO_BRANCH="${REPO_BRANCH:-master}"
APP_DIR="/opt/fleethub"

log()  { echo -e "\033[1;36m[fleethub]\033[0m $*"; }
warn() { echo -e "\033[1;33m[fleethub]\033[0m $*"; }
die()  { echo -e "\033[1;31m[fleethub]\033[0m $*" >&2; exit 1; }

[ "$(id -u)" = "0" ] || die "Lancer en root : sudo DOMAIN=... ./deploy.sh"

# --- 1. Docker -------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  log "Installation de Docker..."
  curl -fsSL https://get.docker.com | sh
fi
if ! docker compose version >/dev/null 2>&1; then
  log "Installation du plugin docker compose..."
  apt-get update
  apt-get install -y docker-compose-plugin
fi
systemctl enable --now docker >/dev/null 2>&1 || true

# --- 2. Dépôt --------------------------------------------------------------
if [ ! -d "$APP_DIR/.git" ]; then
  log "Clone du dépôt dans $APP_DIR..."
  git clone --branch "$REPO_BRANCH" "$REPO_URL" "$APP_DIR"
else
  log "Mise à jour du dépôt dans $APP_DIR..."
  git -C "$APP_DIR" fetch --all
  git -C "$APP_DIR" checkout "$REPO_BRANCH"
  git -C "$APP_DIR" pull --ff-only
fi
cd "$APP_DIR"

# --- 3. .env ---------------------------------------------------------------
ENV_FILE="$APP_DIR/.env"
if [ ! -f "$ENV_FILE" ]; then
  log "Création de .env (secrets aléatoires) pour DOMAIN=$DOMAIN..."
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(openssl rand -base64 18 | tr -d '/+=')}"
  GESTIONNAIRE_PASSWORD="${GESTIONNAIRE_PASSWORD:-$(openssl rand -base64 18 | tr -d '/+=')}"
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
  JWT_SECRET="$(openssl rand -hex 64)"
  INTEGRATION_SECRET_KEY="$(openssl rand -base64 32 | tr -d '/+=')"
  INTEGRATION_WEBHOOK_API_KEY="$(openssl rand -hex 24)"

  cat > "$ENV_FILE" <<EOF
APP_DOMAIN=$DOMAIN
APP_BASE_URL=https://$DOMAIN
APP_CORS_ALLOWED_ORIGINS=https://$DOMAIN
APP_ENV=production
DB_PASSWORD=$DB_PASSWORD
JWT_SECRET=$JWT_SECRET
ADMIN_PASSWORD=$ADMIN_PASSWORD
GESTIONNAIRE_PASSWORD=$GESTIONNAIRE_PASSWORD
APP_LOGIN_RATE_LIMIT=10
# Données de démonstration (admin + société démo avec données) au premier démarrage.
# Mettre à false pour une production sans données de démo.
APP_SEED_ENABLED=true
INTEGRATION_SECRET_KEY=$INTEGRATION_SECRET_KEY
INTEGRATION_WEBHOOK_API_KEY=$INTEGRATION_WEBHOOK_API_KEY
# --- À compléter vous-même selon vos besoins (voir .env.example) ---
#SENTRY_DSN=https://xxxxxxx@o000000.ingest.sentry.io/0000000
#MAIL_ENABLED=true
#MAIL_HOST=smtp.exemple.fr
#MAIL_PORT=587
#MAIL_USERNAME=
#MAIL_PASSWORD=
#MAIL_FROM=no-reply@fleethub.fr
#STRIPE_ENABLED=true
#STRIPE_SECRET_KEY=sk_live_...
#STRIPE_WEBHOOK_SECRET=whsec_...
#STRIPE_PRICE_STARTER=price_...
#STRIPE_PRICE_PRO=price_...
#STRIPE_PRICE_ENTERPRISE=price_...
#RCLONE_REMOTE=s3:mon-bucket/fleethub
#RCLONE_CONFIG_BASE64=
#BACKUP_PING_URL=https://hc-ping.com/votre-uuid
#PING_URL=https://hc-ping.com/votre-uuid-heroku
EOF
  chmod 600 "$ENV_FILE"
  log "Identifiants de démo générés :"
  log "   saasadmin / $ADMIN_PASSWORD  (opérateur plateforme)"
  log "   admin     / $ADMIN_PASSWORD  (société démo)"
  log "   gestionnaire / $GESTIONNAIRE_PASSWORD"
else
  warn ".env existant conservé (pas de modification)."
fi

# --- 4. Build + démarrage --------------------------------------------------
log "Build et démarrage des conteneurs (démarrage initial : quelques minutes)..."
docker compose up -d --build

log "Attente de la santé du backend..."
for i in $(seq 1 60); do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' fleethub-backend 2>/dev/null)" = "healthy" ]; then
    break
  fi
  sleep 5
done

# --- 5. Vérifications post-déploiement ---------------------------------------
log "Vérification Flyway (historique des migrations)..."
if docker exec fleethub-db psql -U fleethub -d fleethub -c \
     "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;" \
     >/dev/null 2>&1; then
  log "Flyway : historique présent, migrations appliquées (OK)"
else
  warn "Impossible de lire flyway_schema_history. Contrôler :"
  warn "   docker exec fleethub-db psql -U fleethub -d fleethub -c 'select * from flyway_schema_history'"
fi

HEALTH="$(curl -fsS -m 10 http://localhost:8090/actuator/health 2>/dev/null || true)"
if [ -n "$HEALTH" ]; then
  log "Santé backend : $HEALTH"
else
  warn "L'endpoint /actuator/health ne répond pas encore localement (attendre quelques secondes puis : curl localhost:8090/actuator/health)."
fi

# Ping de supervision (uptime) si PING_URL est présent dans .env
PING_URL="$(sed -n 's/^PING_URL=//p' "$ENV_FILE" 2>/dev/null || true)"
if [ -n "$PING_URL" ]; then
  curl -fsS -m 10 "$PING_URL" >/dev/null 2>&1 && \
    log "Ping de supervision envoyé (uptime)" || \
    warn "Ping de supervision échoué : $PING_URL"
fi

if [ "$DOMAIN" = "localhost" ]; then
  warn "Démo locale (pas de certificat public) : http://localhost"
else
  log "Démo en ligne : https://$DOMAIN"
fi
log "Terminé. Voir DEPLOY.md pour la suite (vérifs, sauvegardes, mise à jour)."
log "Avant une mise en service réelle, compléter dans .env : MAIL_* (emails),"
log "   Stripe, RCLONE_REMOTE (sauvegarde hors site), SENTRY_DSN (supervision des erreurs)."
