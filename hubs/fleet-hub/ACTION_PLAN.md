# Plan d'action — Fleet Hub vers la production

Ce document complète `RECAP.md` (historique dev), `DEPLOY.md` (mise en ligne) et `SELLING.md`
(vente). Il liste ce qui reste à faire pour que Fleet Hub soit réellement déployable et
vendable — côté technique et côté fondamental (juridique/business) — avec ce qui a déjà été
traité et ce qui reste à la charge du porteur du projet.

## État au 15/08/2026 — validation de déploiement (session)

- **Stack docker-compose validée de bout en bout sur un vrai PostgreSQL 16** : 5 conteneurs
  healthy (caddy, db, backend, frontend, backup), Flyway `success=t` sur `flyway_schema_history`,
  login `admin`/`gestionnaire`/`saasadmin` OK sur HTTPS, tableau de bord complet (6 couples,
  112 724 km, coûts calculés), `pg_dump` quotidien vérifié. Détail : le seeder prend ~2 min au
  premier démarrage (chaque insert est commité/fsync individuellement) — normal, plus rapide
  sur un VPS.
- **3 correctifs de déploiement** :
  1. healthcheck frontend : `wget http://localhost/` échouait car nginx n'écoute qu'en IPv4
     (→ `127.0.0.1`) ;
  2. `APP_SEED_ENABLED` n'était pas transmis au backend en prod → la démo déployée n'avait ni
     `admin` ni société démo (seul `saasadmin` existait) alors que `deploy.sh`/`DEPLOY.md` le
     promettent → ajouté au compose + `true` dans `deploy.sh` ;
  3. `scripts/mobile-shots.mjs` cliquait « Se connecter » sans identifiants → captures inutiles.
- **E2E Playwright** : 17 tests verts (desktop) + 2 mobiles, 0 échec. **Captures mobiles**
  générées (`screenshots/`) : aucun débordement horizontal sur 390 px (5 pages).
- **npm audit** : aucune vulnérabilité dans les dépendances *livrées* (l'image Docker ne contient
  que le build statique ; les paquets `vite`, `@capacitor/*`, `tar` sont des outils de build).
  Seul `react-router-dom` (moderate) est embarqué ; son correctif impose une montée majeure vers
  v7 — délibérément différée (risque > bénéfice sur un déploiement validé, et notre usage SPA
  n'expose pas les chemins d'attaque : pas de SSR, liens internes codés en dur).

## État au 13/08/2026 — ce qui vient d'être fait

- **Mot de passe oublié** : `POST /api/auth/forgot-password` + `/reset-password`, pages
  `/forgot-password` et `/reset-password`, lien depuis l'écran de connexion. Token à usage
  unique (1h), aucune fuite d'information sur l'existence d'un compte. 4 tests d'intégration.
- **Migrations versionnées (Flyway)** : `V1__baseline.sql` (schéma généré depuis les entités
  JPA), `ddl-auto` passé de `update` à `validate` en profil `prod`. `baseline-on-migrate`
  activé pour absorber en douceur un déploiement existant créé par Hibernate.
- **Bug critique corrigé — erreurs publiques masquées en 401** : toute `ResponseStatusException`
  levée sur une route publique (inscription en doublon, lien d'invitation invalide, lien de
  réinitialisation invalide/expiré) ressortait en « 401 Authentification requise » au lieu du
  vrai code (409/400), et l'intercepteur frontend déconnectait l'utilisateur au lieu d'afficher
  l'erreur. Cause : `sendError()` déclenche un forward interne vers `/error` qui retraverse la
  sécurité Spring sans le JWT. Corrigé à la racine (`GlobalExceptionHandler`), avec tests.
- **Bug corrigé — `YearMonth` sérialisé en `bytea`** : `CostRecord.billingMonth` n'avait pas de
  convertisseur JPA ; Hibernate le stockait par sérialisation Java, ce qui fausse silencieusement
  tri et comparaisons SQL utilisés par le calcul des coûts. Convertisseur ISO texte ajouté.
- **Sauvegardes hors site (optionnel)** : `backup.sh` sait pousser chaque dump vers un
  stockage distant via rclone (`RCLONE_REMOTE`) et notifier un service de supervision
  (`BACKUP_PING_URL`, ex. healthchecks.io) — désactivé tant que ces variables ne sont pas
  renseignées dans `.env`.
- *(Session précédente)* code-splitting frontend, dette technique (Redis inutilisé, logs),
  refonte visuelle, correctif 401 sur la fiche chauffeur (parsing de dates).

Tests : 71 tests backend verts, 11 tests frontend verts, build OK.

## Partie 1 — Technique

### 1.1 Bloquant avant un vrai lancement commercial

| Sujet | Statut | Action concrète |
|---|---|---|
| Mot de passe oublié | ✅ Fait | — |
| Migrations versionnées | ✅ Fait + **validé** (15/08/2026) | Stack docker-compose complète sur PostgreSQL 16 : `flyway_schema_history` (`success=t`), `ddl-auto: validate` OK, login et données vérifiés. |
| Emails transactionnels | ❌ À faire | Créer un compte chez un fournisseur SMTP transactionnel (Brevo, Postmark, SES...), renseigner `MAIL_*` dans `.env`, repasser `MAIL_ENABLED=true`. Sans ça, invitations et rappels d'essai ne partent jamais réellement. |
| Intégrations GPS/tachygraphe réelles | ❌ À faire (roadmap) | Décision produit avant tout : voir § Fondamental. Techniquement, le squelette (`backend/src/main/java/com/fleethub/integration/`) attend une implémentation par fournisseur (`GpsSource`, `TachographSource`, `CostSource`). |

### 1.2 Important, à traiter avant une montée en charge réelle

- **Sauvegardes hors site** : renseigner `RCLONE_REMOTE` + `RCLONE_CONFIG_BASE64` dans `.env`
  (le mécanisme est en place, il manque juste vos identifiants de stockage — ex. Backblaze B2,
  Scaleway Object Storage, S3). Tester une restauration au moins une fois (`gunzip | psql`).
- **Supervision** : `BACKUP_PING_URL` couvre les sauvegardes ; il manque un ping équivalent sur
  `/actuator/health` (ex. UptimeRobot gratuit, alerte si le site tombe) et un traqueur d'erreurs
  applicatives (Sentry a un plan gratuit compatible Spring Boot + React).
- **JWT sans révocation** : un token compromis reste valide jusqu'à expiration (24h), pas de
  déconnexion à distance ni de 2FA. Acceptable pour un lancement, à noter comme limite connue
  si un client pose la question en revue de sécurité.
- **App mobile** : au-delà du debug local — build signé (`bundleRelease`), icônes/splash définitifs,
  fiche Play Store/App Store. Nécessite un compte développeur Google (25 $ une fois) et Apple
  (99 $/an) — décision business, voir § Fondamental.
- **Un seul VPS** : point de défaillance unique, documenté comme limite acceptée pour un début
  d'activité ; à revisiter si le nombre de clients justifie de la redondance.

### 1.3 Suivi de routine (pas bloquant)

- `npm audit` / `mvn dependency-check` de temps en temps (13 vulnérabilités npm déjà signalées
  lors du diagnostic initial, à trier).
- Nettoyage régulier de `backend/*.log` locaux (déjà gitignorés).

## Partie 2 — Fondamental (business / légal)

Rien ici n'est automatisable par du code : ce sont des décisions et démarches qui vous
appartiennent. Voici la checklist et, pour chacune, ce qu'il faut concrètement faire.

### 2.1 Cadre juridique (préalable à toute vente réelle)

1. **Créer une structure juridique** (micro-entreprise pour démarrer, ou société) si ce n'est
   pas déjà fait — indispensable pour émettre des factures, ouvrir un compte Stripe en mode
   live, et signer des CGU opposables.
2. **Réécrire les mentions légales et la politique de confidentialité** (`LegalService.java`) :
   le contenu actuel est un gabarit générique (email `privacy@fleet-hub.fr` fictif, aucune
   raison sociale/SIRET/adresse). Une fois la structure créée, soit un générateur sérieux
   (ex. les CGU d'un service comme Legalstart/Captain Contrat), soit une relecture par un
   juriste/avocat — le montant en jeu (abonnements SaaS + données de conduite RGPD-sensibles)
   justifie une vraie relecture, pas un copier-coller.
3. **DPA (accord de sous-traitance RGPD)** avec chaque sous-traitant réel une fois choisi
   (hébergeur VPS, Stripe, fournisseur SMTP) — généralement un document standard que le
   fournisseur fournit lui-même sur demande.

### 2.2 Facturation réelle

1. Créer un compte Stripe, passer en mode **live** (nécessite la structure juridique ci-dessus
   + un RIB professionnel).
2. Créer les 3 produits d'abonnement (STARTER/PRO/ENTERPRISE) dans Stripe, récupérer les
   `price_id`, compléter `.env` (`STRIPE_*`), activer `STRIPE_ENABLED=true`.
3. Définir par écrit une politique de remboursement/résiliation cohérente avec les CGU
   (durée de préavis, remboursement au prorata ou non).

### 2.3 Validation marché

Les fourchettes de prix dans `SELLING.md` (2 500–10 000 €) sont des ordres de grandeur de
marketplace, pas des prix validés par de vrais acheteurs. Avant de lister le produit :
- Identifier 5 à 10 prospects réels (transporteurs, loueurs de flotte, gestionnaires de parc)
  et leur montrer la démo — au minimum pour valider que le calcul du score composite
  Chauffeur × Camion répond à un vrai point de douleur non couvert par les acteurs installés
  (Geotab, Samsara, Fleetio, WEBFLEET...).
- Décider explicitement du positionnement : vendu comme **outil clé en main** (implique
  d'avoir de vraies intégrations GPS/tachygraphe branchées, cf. 1.1) ou comme **base de code
  à reprendre et brancher soi-même** (le squelette actuel suffit, mais il faut le dire
  clairement dans l'annonce pour ne pas décevoir l'acheteur).

### 2.4 Support et garanties

- Définir une durée de support incluse (30–90 jours suggéré dans `SELLING.md`) et un tarif
  d'extension.
- Définir un canal de support (email dédié, ou a minima une adresse professionnelle réelle —
  pas `privacy@fleet-hub.fr` qui n'existe pas).
- Aligner la politique de garantie/remboursement sur les règles du canal de vente choisi
  (14–30 jours selon la plateforme).

## Séquencement recommandé

1. **Cadre juridique** (2.1) — préalable à tout le reste, y compris Stripe live.
2. **Emails transactionnels** (1.1) — rapide, débloque l'onboarding réel (invitations).
3. **Validation Flyway sur un vrai PostgreSQL** (1.1) — avant tout déploiement avec de vraies
   données à préserver.
4. **Décision GPS/tachygraphe** (2.3) — conditionne si vous vendez "prêt à l'emploi" ou "base à
   brancher" ; impacte le pitch commercial autant que la roadmap technique.
5. **Stripe live + sauvegardes hors site + supervision** (1.2, 2.2) — juste avant le premier
   client payant.
6. **Validation marché + support/garanties** (2.3, 2.4) — en parallèle, dès que la démo est
   présentable.

---

Ce que je peux continuer à faire directement (code, config, tests) : tout ce qui est marqué
❌ dans la partie technique, plus tout ajustement une fois vos décisions business prises
(ex. brancher un vrai fournisseur GPS dès que vous avez un compte chez l'un d'eux).
