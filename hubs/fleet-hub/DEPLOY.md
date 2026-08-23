# Déploiement de Fleet Hub — démo en ligne + vente

Ce guide vous emmène d'un serveur vide à une **démo SaaS en ligne** (HTTPS) prête à
présenter aux acheteurs, puis au **package de vente**. Tout est automatisé sauf les
achats (VPS + domaine) et le paramétrage (Stripe/email éventuels).

## Étape 1 — Acheter un serveur (VPS)

Le besoin est léger : 1 vCPU / 1–2 Go RAM / 20 Go SSD suffisent pour la démo
(Java + nginx + PostgreSQL). Options fiables et économiques :

| Fournisseur | Offre entrée | Prix indicatif | Notes |
|---|---|---|---|
| Hetzner Cloud (EU) | CX22 | ~4 €/mois | excellent rapport qualité/prix, data center UE (RGPD) |
| OVHcloud (EU) | VPS « Starter » | ~5 €/mois | français, UE |
| Scaleway (EU) | DEV1-S | ~5 €/mois | français, UE |
| DigitalOcean | Basic Droplet | ~6 $/mois | très répandu, tutoriels nombreux |
| Contabo (EU) | VPS S | ~5 €/mois | souvent le moins cher |

Recommandation : **Hetzner CX22 (Ubuntu 24.04)** — hébergement UE, pas cher, fiable.

Choisissez un système **Ubuntu 24.04 ou Debian 12**, puis notez l'**IP publique**.

## Étape 2 — Acheter un nom de domaine

Options à ~10–15 €/an : OVHcloud, Gandi, Namecheap, Porkbun. Exemples pertinents
pour la vente : `fleethub-demo.fr`, `demo.fleethub.io`…

> Une démo sous un domaine de marque rassure l'acheteur. Si vous n'avez pas encore
> de marque définitive, un sous-domaine style `fleethub-demo.fr` suffit.

## Étape 3 — Pointer le DNS

Chez votre registrar, créez un enregistrement **A** (et éventuellement AAAA) :

- **Nom** : `@` (ou `fleethub-demo` si sous-domaine)
- **Valeur** : l'IP publique du VPS

Attendez la propagation DNS (typiquement 5 min à 24 h — vérifiez avec
`nslookup fleethub-demo.fr` ou [dnschecker.org](https://dnschecker.org)).

## Étape 4 — Déployer en une commande

Connectez-vous au serveur :

```bash
ssh root@IP_DU_VPS
```

Puis lancez le déploiement (il installe Docker, clone le dépôt, crée un `.env`
avec des secrets aléatoires et démarre tout) :

```bash
sudo DOMAIN=fleethub-demo.fr bash -c "$(curl -fsSL https://raw.githubusercontent.com/Ybarzan/fleet-hub/master/deploy.sh)"
```

> Si vous préférez exécuter le script local : clonez le dépôt et faites
> `sudo DOMAIN=... ./deploy.sh`.

À la fin, le script affiche les **identifiants de démo générés**
(`saasadmin`, `admin`, `gestionnaire`). Conservez-les précieusement.

L'application est alors disponible sur **https://fleethub-demo.fr** (certificat
Let's Encrypt automatique via Caddy).

## Étape 5 — Vérifier la démo

1. Ouvrez https://fleethub-demo.fr et connectez-vous avec `admin` / mot de passe affiché.
2. Parcourez : tableau de bord, chauffeurs, carte temps réel, saisie, alertes,
   abonnement, intégrations, utilisateurs, mes données.
3. Créez un **compte de test** via « Créer ma société » pour montrer l'inscription
   SaaS et l'essai 14 jours.
4. Vérifiez le back-office : connectez-vous avec `saasadmin`, puis **Administration**.
5. Testez la vue mobile (responsive) et, si besoin, l'APK Android de démo.

### Contrôles d'état

```bash
docker compose ps              # tous les services "Up"
docker compose logs -f backend # logs applicatifs (JSON en prod)
curl -fsS https://DOMAIN/actuator/health
ls backups/                    # sauvegardes PostgreSQL quotidiennes
```

### Premier démarrage : valider le schéma (Flyway)

Avant d'ouvrir l'app à un vrai client, vérifiez une fois que les migrations se
sont appliquées proprement (le backend ne démarre qu'avec un schéma conforme) :

```bash
docker exec fleethub-db psql -U fleethub -d fleethub \
  -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

- `success = t` sur chaque ligne : OK. Vous devez voir `1 | 1 | << Flyway Baseline >> | t` puis `V1__baseline`.
- `success = f` : une migration a échoué — consultez `docker compose logs backend`, corrigez,
  et rejouez uniquement la migration fautive (jamais `flyway repair` sans comprendre pourquoi).
- Le backend part en `ddl-auto: validate` : tout écart entre le schéma et les entités JPA
  fait échouer le démarrage (voulu — pas de désynchronisation silencieuse).

Petit contrôle de contenu :

```bash
docker exec fleethub-db psql -U fleethub -d fleethub -c "SELECT count(*) FROM users;"
docker exec fleethub-db psql -U fleethub -d fleethub -c "SELECT name FROM company;"
```

## Étape 6 — Sauvegardes

Le conteneur `backup` fait un `pg_dump` compressé toutes les 24 h dans
`./backups/` (rétention 7 jours par défaut). Pour une démo, c'est suffisant.
Pour de la production, pointez ces dumps vers un stockage externe
(rclone/S3/rsync hors du serveur).

## Étape 7 — Activer Stripe et les emails (facultatif, plus tard)

La démo fonctionne sans. Pour tester la **facturation Stripe** :
- créez un compte Stripe, récupérez `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
  et les 3 `STRIPE_PRICE_*` (produits STARTER/PRO/ENTERPRISE en mode abonnement) ;
- complétez `.env` et relancez : `docker compose up -d` ;
- configurez le webhook Stripe vers `https://DOMAIN/api/webhooks/stripe`.

Pour les **emails** (invitations, rappels d'essai) : renseignez `MAIL_*` dans `.env`.

Pour la **supervision** : créez un projet Sentry (gratuit), collez le DSN dans
`SENTRY_DSN` du `.env` (il sert au backend ET au frontend — le DSN n'est pas
secret), et ajoutez un ping d'uptime (`PING_URL`, ex. healthchecks.io) alerté si
le site répond plus.

## Étape 8 — Mettre à jour la démo

```bash
sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/Ybarzan/fleet-hub/master/deploy.sh)"
```

Le script met à jour le dépôt puis relance le build. Les données (volume
PostgreSQL `pgdata`) sont conservées.

## Étape 9 — Préparer la vente

Une démo en ligne, c'est la vitrine. Le reste du package :

1. **Captures d'écran** : générez-les avec
   `cd frontend && node scripts/mobile-shots.mjs` (desktop + mobile), ou via
   l'émulateur Android.
2. **Vidéo de démo** (2–3 min) : enregistrez un parcours — login, dashboard,
   drill-down chauffeur, carte temps réel, saisie manuelle, inscription d'une société.
3. **Documentation acheteur** : `README.md` (démarrage, API, tests),
   `INTEGRATION.md` (brancher les vraies données), ce guide `DEPLOY.md`.
4. **Choix du canal de vente** — voir `SELLING.md`.

---

## Dépannage

| Symptôme | Cause / solution |
|---|---|
| `https://DOMAIN` ne répond pas | DNS pas propagé, ou Caddy n'a pas pu obtenir le certificat : `docker compose logs caddy` |
| Le login renvoie « Erreur de connexion au serveur » | backend pas encore sain : attendre, puis `docker compose logs backend` |
| Page 402 sur les données | société en essai expiré/suspendue : se connecter en `saasadmin` → Administration pour prolonger |
| 429 sur connexion | trop de tentatives par IP (limite 10/min) : patienter 60 s |
| Redémarrage VPS | tout redémarre automatiquement (`restart: unless-stopped`) |
