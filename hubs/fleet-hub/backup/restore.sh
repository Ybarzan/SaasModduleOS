#!/usr/bin/env bash
#
# Restaure un dump produit par backup/backup.sh dans la base de la stack docker compose.
#
# Usage (depuis hubs/fleet-hub/) :
#   ./backup/restore.sh backups/fleethub-20261105-030000.sql.gz
#
# Étapes : arrêt du backend (plus aucune écriture), sauvegarde de sécurité de la base
# actuelle, remplacement complet de la base par le dump, redémarrage du backend (Flyway
# rejoue alors les migrations plus récentes que le dump, s'il y en a).
#
# Destructif pour les données actuelles : demande confirmation (FORCE=1 pour l'éviter,
# ex. dans un test de restauration automatisé).

set -euo pipefail

DUMP="${1:-}"
DB_USER="${DB_USER:-fleethub}"
DB_NAME="${DB_NAME:-fleethub}"

log()  { echo -e "\033[1;36m[restore]\033[0m $*"; }
die()  { echo -e "\033[1;31m[restore]\033[0m $*" >&2; exit 1; }

[ -n "$DUMP" ] || die "Usage : $0 backups/fleethub-AAAAMMJJ-HHMMSS.sql.gz"
[ -f "$DUMP" ] || die "Fichier introuvable : $DUMP"
gzip -t "$DUMP" 2>/dev/null || die "Archive illisible ou tronquée : $DUMP"
[ -f docker-compose.yml ] || die "À lancer depuis hubs/fleet-hub/ (là où se trouve docker-compose.yml)"

if [ "${FORCE:-0}" != "1" ]; then
  echo "Cette opération REMPLACE toutes les données actuelles de la base « $DB_NAME » par $DUMP."
  read -r -p "Tapez RESTAURER pour continuer : " answer
  [ "$answer" = "RESTAURER" ] || die "Abandon."
fi

log "Arrêt du backend (plus aucune écriture pendant la restauration)..."
docker compose stop backend

SAFETY="backups/avant-restauration-$(date +%Y%m%d-%H%M%S).sql.gz"
log "Sauvegarde de sécurité de la base actuelle : $SAFETY"
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$SAFETY"

log "Recréation de la base « $DB_NAME »..."
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" \
  -c "DROP DATABASE IF EXISTS \"$DB_NAME\";" \
  -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";"

log "Import du dump..."
gunzip -c "$DUMP" | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB_NAME"

log "Redémarrage du backend..."
docker compose start backend
for _ in $(seq 1 60); do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' fleethub-backend 2>/dev/null)" = "healthy" ]; then
    log "Backend sain : restauration terminée. Base précédente conservée dans $SAFETY."
    exit 0
  fi
  sleep 5
done
die "Le backend n'est pas redevenu sain : docker compose logs backend (base précédente : $SAFETY)"
