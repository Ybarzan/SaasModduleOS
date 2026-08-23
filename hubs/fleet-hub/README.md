# Fleet Hub — Outil de gestion de flotte (KPIs Chauffeur × Camion)

Application web de gestion de flotte pour transport de poids lourds. Elle centralise les données
de balises GPS et de tachygraphe (AS24) pour offrir un tableau de bord en temps réel au gestionnaire
de transport, avec des KPIs centrés sur le **couple Chauffeur × Camion**.

> **Statut : SaaS multi-tenant.** Chaque société cliente s'inscrit en autonomie
> (`/register`), obtient un **essai gratuit de 14 jours** (plan TRIAL) puis choisit un
> abonnement (STARTER / PRO / ENTERPRISE) via **Stripe** (checkout, portail de facturation,
> webhooks, suspension automatique en cas d'impayé). Les données sont isolées par société ;
> un **back-office plateforme** (`/admin`, rôle `SAAS_ADMIN`) gère abonnements, essais et
> suspensions. Conformité **RGPD** : mentions légales, export/portabilité, journal d'audit et
> suppression de compte. Gestion des **utilisateurs** par société (invitation, rôles),
> **alertes & notifications** configurables, et **intégrations externes** tachygraphe / GPS /
> carburant (polling + API push).

---

## 1. Stack technique

| Couche     | Technologies |
|------------|--------------|
| Backend    | Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security + JWT, Maven |
| Base de données | H2 (dev, intégrée, zéro config) / PostgreSQL (prod) |
| Frontend   | React 18, Vite, React Router, axios, Recharts, Leaflet (carte OSM, sans clé API) |
| Déploiement | Docker + docker-compose |

## 2. Fonctionnalités

- **SaaS multi-tenant** : inscription autonome des sociétés (`/register`), données isolées par
  société (`company_id` sur toutes les entités), essai gratuit 14 jours puis plans
  **TRIAL (10 véh / 5 chauf), STARTER (25/10), PRO (100/50), ENTERPRISE (illimité)**
- **Back-office plateforme** (`/admin`, rôle `SAAS_ADMIN`) : liste des sociétés,
  suspension / réactivation, changement de plan, prolongation d'essai ; les sociétés
  **résiliées sont bloquées** à la connexion, les sociétés **suspendues ou en essai expiré**
  restent connectables mais avec **données gelées** (402) : elles peuvent toujours se
  connecter pour s'abonner ou régulariser leur paiement (page `/billing`)
- **Abonnement & facturation Stripe** (`/billing`) : checkout d'abonnement (STARTER/PRO/
  ENTERPRISE), portail de facturation, changement de plan, webhooks (paiement, résiliation,
  impayé → suspension automatique)
- **RGPD** (`/rgpd`, rôle ADMIN) : export/portabilité JSON de toutes les données du tenant,
  journal d'audit (connexions, invitations, actions sensibles), suppression du compte
  (effacement + résiliation Stripe) ; **mentions légales publiques** (CGU, confidentialité)
- **Gestion des utilisateurs** (`/users`, rôle ADMIN) : invitation d'ADMIN/GESTIONNAIRE avec
  lien d'activation, changement de rôle, activation/désactivation, suppression (garde-fous
  sur le dernier ADMIN)
- **Alertes & notifications** (`/notifications`) : balayage automatique (entretien à échéance,
  non-conformité tachygraphe, temps de conduite, usage anormal), seuils configurables,
  marquage lu
- **Intégrations externes** : polling tachygraphe/GPS/carburant (`@Scheduled`) + endpoint
  push `POST /api/webhooks/ingest` (clé API) — voir [`INTEGRATION.md`](INTEGRATION.md)
- **Authentification JWT** : login/mot de passe, rôles ADMIN / GESTIONNAIRE (société) et
  SAAS_ADMIN (opérateur plateforme, sans société)
- **Tableau de bord "Glance"** : les 4 KPIs North Star visibles sans scroll
  (coût au km, taux d'utilisation, conformité maintenance, indisponibilité imprévue)
- **Liste des couples Chauffeur × Camion** avec KPIs et filtres (Jour / Semaine / Mois) + recherche
- **Drill-down par chauffeur** : graphiques d'évolution (km, coûts), événements de conduite,
  répartition des coûts, alertes
- **Carte temps réel** : position des véhicules, code couleur par statut
  (vert = roulage, orange = arrêt, bleu = repos, rouge = alerte/immobilisé)
- **Saisie manuelle** : ajouter/modifier/supprimer chauffeurs, camions, affectations,
  trajets, relevés de carburant et jours de tachygraphe (menu **✏️ Saisie**), sans attendre
  le branchement des APIs
- **KPIs calculés** (14 indicateurs) : sécurité (événements à risque), éco-conduite, temps de
  conduite, conformité réglementation 561/2006, consommation L/100km, uptime, maintenance, etc.
- **API documentée** : Swagger UI / OpenAPI

## 3. Lancer en local (sans Docker)

### Prérequis
- JDK 21
- Maven 3.9+
- Node.js 22+

### Backend (port 8090)
```bash
cd backend
mvn spring-boot:run
```
Le seeder crée automatiquement 6 chauffeurs, 6 camions, les affectations et ~30 jours de données.

### Frontend (port 5199)
```bash
cd frontend
npm install
npm run dev
```
Puis ouvrir **http://localhost:5199**

### Identifiants de démonstration
| Utilisateur | Mot de passe | Rôle |
|-------------|--------------|------|
| `saasadmin` | `admin`      | SAAS_ADMIN (opérateur plateforme) |
| `admin`     | `admin`      | ADMIN (société démo « Fleet Hub Démo ») |
| `gestionnaire` | `gestion` | GESTIONNAIRE |

> En prod, `ADMIN_PASSWORD` et `GESTIONNAIRE_PASSWORD` surchargent les mots de passe
> (`saasadmin` utilise `ADMIN_PASSWORD`).

### Inscription d'une nouvelle société
Depuis la page de connexion, cliquez sur **Créer ma société** : nom de la société + admin local
(prénom, nom, email, mot de passe). La société démarre en **TRIAL, 14 jours**, et l'utilisateur
est connecté automatiquement.

### Swagger
**http://localhost:8090/swagger-ui.html** (bouton "Authorize" : coller le token JWT récupéré via
`POST /api/auth/login`).

## 4. Tutoriel d'utilisation

### Étape 1 — Se connecter

Ouvrez **http://localhost:5199** puis connectez-vous :

| Utilisateur | Mot de passe | Rôle |
|-------------|--------------|------|
| `saasadmin` | `admin`      | SAAS_ADMIN (opérateur plateforme) |
| `admin`     | `admin`      | ADMIN (société démo) |
| `gestionnaire` | `gestion` | GESTIONNAIRE |

Une fois connecté, une barre latérale apparaît avec les menus :
**📊 Tableau de bord**, **🧑‍✈️ Chauffeurs**, **🚛 Camions**, **🗺️ Carte temps réel**,
**✏️ Saisie**, **🔔 Alertes**, **💳 Abonnement**, **👥 Utilisateurs** (ADMIN),
**🛡️ Mes données** (ADMIN), plus **🛠️ Administration** (visible uniquement pour `saasadmin`),
et votre identité + le bouton **Déconnexion** en bas.

### Étape 2 — Lire le tableau de bord (page d'accueil)

- **4 KPIs "North Star"** en haut : coût au km, taux d'utilisation, conformité maintenance,
  indisponibilité imprévue. Chaque carte affiche aussi le total de km parcourus.
- **Sélecteur de période** (Jour / Semaine / Mois) en haut à droite : il recalcule toute la
  page sur la période choisie.
- **Score global de la flotte** : jauge composite + répartition des véhicules
  (en service, à l'arrêt/repos, en alerte) + compteurs d'alertes et de jours de conduite non conformes.
- **Alertes actives** à surveiller (dépassement de temps de conduite, immobilisation, etc.).
- **Meilleurs couples Chauffeur × Camion** : tableau cliquable vers le détail.

### Étape 3 — Suivre les chauffeurs

Menu **Chauffeurs** :
- Utilisez le **sélecteur de période** et la **barre de recherche** (nom du chauffeur,
  immatriculation, marque, modèle) pour filtrer la liste.
- Chaque ligne = un couple : score composite, km, coût/km, utilisation, éco-conduite,
  conformité conduite (561/2006), consommation, événements à risque.
- **Cliquez sur un chauffeur** pour ouvrir son analyse détaillée.

### Étape 4 — Analyser un couple (drill-down)

La page détail d'un chauffeur montre :
- le **score composite** et les 4 KPIs North Star du couple ;
- les alertes éventuelles en bandeau jaune ;
- deux blocs de KPIs : **🧑‍✈️ Conduite** (événements à risque, éco-conduite, ralenti,
  ponctualité, conformité 561/2006) et **🚛 Camion** (consommation et dérive vs référence,
  uptime, immobilisations, km en charge, coût total, heures de roulage) ;
- trois graphiques : **kilomètres et coûts quotidiens**, **événements de conduite**
  (freinage brusque, accélération, excès de vitesse, ralenti), **répartition des coûts** ;
- le lien **← Retour aux chauffeurs** en haut à gauche.

### Étape 5 — Voir les camions

Menu **Camions** : état de la flotte avec véhicule, type, énergie, **statut en badge coloré**,
chauffeur affecté, consommation de référence et **dernier passage GPS**.

### Étape 6 — La carte temps réel

Menu **Carte temps réel** :
- chaque véhicule est un point coloré selon son statut (vert = en roulage, orange = arrêt,
  bleu = repos, rouge = alerte/immobilisé) — voir la légende au-dessus de la carte ;
- **cliquez sur un point** : popup avec immatriculation, statut, vitesse et chauffeur ;
- la carte se **rafraîchit automatiquement toutes les 15 secondes** ;
- le tableau sous la carte liste les positions (vitesse, coordonnées).

### Étape 7 — Saisir des données manuellement

Menu **✏️ Saisie** : 6 onglets (Chauffeurs, Camions, Affectations, Trajets, Carburant,
Tachygraphe). Dans chaque onglet :
- remplissez le **formulaire** puis cliquez sur **Ajouter** pour créer un élément ;
- cliquez sur **Éditer** sur une ligne pour pré-remplir le formulaire et enregistrer
  vos modifications ;
- cliquez sur **Supprimer** pour retirer un élément (les données liées sont nettoyées) ;
- pour une **affectation** : si le chauffeur ou le camion est déjà affecté, l'affectation
  précédente est automatiquement désactivée (un chauffeur / un camion = une affectation active).

Les KPIs se **recalculent automatiquement** : un trajet ou un relevé de carburant ajouté
apparaît immédiatement dans le tableau de bord, la liste des chauffeurs et le drill-down.

### Étape 8 — Se déconnecter

Cliquez sur **Déconnexion** en bas de la barre latérale.

### Étape 9 — Le back-office plateforme (SAAS_ADMIN)

Connectez-vous avec `saasadmin` puis ouvrez **🛠️ Administration** :
- la liste des **sociétés clientes** (plan, statut, fin d'essai, nb d'utilisateurs/chauffeurs/camions) ;
- **Suspendre / Réactiver** une société : les données deviennent inaccessibles (402) mais les
  utilisateurs restent connectables pour régulariser (essai expiré, impayé) — la résiliation
  seule bloque totalement la connexion ;
- changer le **plan** (liste déroulante : TRIAL, STARTER, PRO, ENTERPRISE — les limites de
  véhicules et chauffeurs sont appliquées côté API) ;
- **+30 j essai** pour prolonger un essai gratuit.

### Notes pratiques

- Le sélecteur **Jour / Semaine / Mois** existe sur le tableau de bord, la liste des
  chauffeurs et le détail d'un couple : toutes les cartes et tableaux s'adaptent.
- Les données sont de la **démo générée au démarrage du backend** : chaque redémarrage
  recrée un jeu de données cohérent (6 chauffeurs, 6 camions, ~30 jours d'activité).
- Pour explorer l'API sans interface, utilisez **Swagger UI** (voir ci-dessus).

## 5. Lancer en production (Docker)

Copier `.env.example` vers `.env` puis compléter les valeurs avant de lancer :

```bash
docker compose up --build
```

L'infrastructure docker-compose comprend **6 services** :

| Service   | Rôle |
|-----------|------|
| `caddy`   | Reverse proxy **HTTPS automatique** (Let's Encrypt pour un vrai domaine, certificat interne pour `localhost`) — ports 80/443 |
| `db`      | PostgreSQL 16 (volume `pgdata`, healthcheck) |
| `backend` | Spring Boot (profil `prod`, PostgreSQL, logs **JSON structuré** en prod) |
| `frontend`| Build React servi par nginx (proxie `/api/` vers le backend) |
| `backup`  | Sauvegarde quotidienne `pg_dump` compressée dans `./backups/` (rétention `BACKUP_KEEP_DAYS`, défaut 7 j) |

### Configuration minimum du `.env`

```bash
APP_DOMAIN=fleethub.monentreprise.fr   # domaine public ; Caddy gère le certificat
DB_PASSWORD=...
JWT_SECRET=openssl rand -hex 64
ADMIN_PASSWORD=...
GESTIONNAIRE_PASSWORD=...
```

> Si `APP_DOMAIN` change, mettre aussi à jour `APP_CORS_ALLOWED_ORIGINS` et `APP_BASE_URL`
> (le docker-compose par défaut suppose `https://localhost`).

- Application : **https://votre-domaine** (http://localhost si `APP_DOMAIN=localhost`)
- Backend API : accessible uniquement via le proxy (healthcheck : `/actuator/health`)
- PostgreSQL : `localhost:5432` (db `fleethub`, user `fleethub`, mdp définis dans `.env`)
- Swagger UI n'est **pas exposé en profil prod** (désactivé dans `application.yml`).

> Le conteneur backend reçoit toutes les variables de configuration (Stripe, intégrations,
> emails, URL publique) via docker-compose. Pensez à changer `JWT_SECRET`, les identifiants
> BDD et les clés Stripe/API en environnement réel.

### CI/CD (GitHub Actions)

`.github/workflows/ci.yml` s'exécute à chaque push sur `main` / PR :
1. `mvn test` (backend, 67 tests)
2. `npm ci` + `npm test` + `npm run build` (frontend)
3. **push des images Docker** `ghcr.io/<repo>-backend` / `-frontend` (tags `latest` + SHA) sur `main`

La surveillance de charge sert à publier les images ; le déploiement reste un
`docker compose up` depuis la source sur votre serveur (ou un pull des images GHCR).

## 6. Principales API REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/auth/login` | Connexion (renvoie le JWT + infos société) |
| POST | `/api/auth/register` | Inscription d'une société (essai 14 j, auto-login) |
| POST | `/api/auth/accept-invitation` | Activation d'un compte invité (token + mot de passe) |
| GET | `/api/admin/companies` | Back-office : liste des sociétés (SAAS_ADMIN) |
| POST | `/api/admin/companies/{id}/suspend` | Suspendre une société |
| POST | `/api/admin/companies/{id}/activate` | Réactiver une société |
| POST | `/api/admin/companies/{id}/plan` | Changer le plan `{ "plan": "PRO" }` |
| POST | `/api/admin/companies/{id}/extend-trial` | Prolonger l'essai `{ "days": 30 }` |
| GET | `/api/billing/status` | Statut de l'abonnement courant |
| POST | `/api/billing/checkout` | Session de checkout Stripe `{ "plan": "PRO" }` |
| POST | `/api/billing/portal` | Portail de facturation Stripe |
| GET | `/api/users` | Liste des utilisateurs de la société (ADMIN) |
| POST | `/api/users/invite` | Inviter un utilisateur (ADMIN) |
| PUT/DELETE | `/api/users/{id}` | Mettre à jour / supprimer un utilisateur (ADMIN) |
| GET | `/api/notifications` | Notifications du tenant (avec balayage) |
| POST | `/api/notifications/scan` | Lancer un balayage d'alertes |
| GET/POST | `/api/notifications/rules` | Règles d'alerte (liste / enregistrement) |
| GET | `/api/account/export` | Export RGPD JSON des données (ADMIN) |
| GET | `/api/account/audit-log` | Journal d'audit du tenant (ADMIN) |
| POST | `/api/account/delete` | Suppression RGPD du compte `{ "password": "…" }` (ADMIN) |
| GET | `/api/legal/{key}` | Mentions légales publiques (`terms`, `privacy`) |
| POST | `/api/webhooks/ingest` | Push externe (tachygraphe/GPS/carburant, header `X-API-Key`) |
| GET | `/api/dashboard/summary?period=MONTH` | KPIs North Star + alertes + top couples |
| GET | `/api/kpis/couples?period=MONTH` | KPIs de tous les couples |
| GET | `/api/kpis/couples/{id}?period=MONTH` | Détail : KPIs + tendances + événements + coûts |
| GET | `/api/kpis/definitions` | Formules des KPIs |
| GET | `/api/drivers` | Liste des chauffeurs (tenant courant) |
| GET | `/api/trucks` | Liste des camions (tenant courant) |
| GET | `/api/map/vehicles` | Positions temps réel |

Toutes les données métier sont **isolées par société** : un utilisateur d'une société ne voit
jamais les données d'une autre (404 si accès direct à un ID étranger).

`period` accepte : `DAY`, `WEEK`, `MONTH`.

## 7. Structure du projet

```
fleet-hub/
├── backend/
│   ├── src/main/java/com/fleethub/
│   │   ├── config/       # Sécurité, OpenAPI, DataSeeder
│   │   ├── controller/   # Endpoints REST
│   │   ├── dto/          # Objets de réponse
│   │   ├── integration/  # Squelette ETL : sources tachygraphe/GPS/carburant
│   │   ├── model/        # Entités JPA
│   │   ├── repository/   # Accès données
│   │   ├── security/     # JWT
│   │   └── service/      # KpiService (moteur de calcul), DashboardService
│   └── src/test/java/    # Tests unitaires + intégration (JWT, AuthService, contrôleurs, KPI)
├── frontend/
│   ├── android/          # Projet natif Android (Capacitor)
│   ├── ios/              # Projet natif iOS (Capacitor, build macOS)
│   ├── e2e/              # Tests E2E Playwright (desktop + mobile)
│   └── src/
│       ├── components/   # StatCard, ScoreGauge, Layout, …
│       ├── context/      # Authentification
│       ├── pages/        # Dashboard, Chauffeurs, Détail, Camions, Carte, Saisie
│       ├── services/     # Client axios (JWT)
│       └── test/         # Tests unitaires (vitest + Testing Library)
├── docker-compose.yml
└── README.md
```

## 8. Tests

```bash
# Backend — tests unitaires + intégration (67 tests : JWT, AuthService, contrôleurs CRUD,
# moteur KPI, isolation multi-tenant, back-office, limites de plan, RGPD, invitations,
# notifications, webhooks Stripe, ingestion externe + tenant-scoping, rate-limit login/register,
# bootstrap SAAS_ADMIN, gel des données en essai expiré)
cd backend
mvn test

# Frontend — tests unitaires (vitest + Testing Library, 11 tests)
cd frontend
npm test

# E2E — Playwright (2 projects : desktop Chrome + mobile Pixel 5)
# Nécessite le backend lancé (port 8090). Le frontend Vite est démarré automatiquement.
cd frontend
npx playwright test
npx playwright test --project=desktop   # uniquement desktop
npx playwright test --project=mobile    # uniquement mobile
```

Couverture : login/déconnexion, token JWT (valide, expiré, invalide), protection des routes,
CRUD complet (chauffeurs, camions, affectations, trajets, carburant, tachygraphe), règle de
conflit d'affectation, doublons, validation 400, 404, recherche/filtres, drill-down, saisie
manuelle, menu mobile (hamburger), **inscription SaaS, isolation des données entre sociétés,
limites de plan, back-office plateforme**.

## 9. Interface mobile

L'application est **responsive** (testée à partir de 393 px de large) :
- la barre latérale devient un **menu tiroir** ouvert par le bouton **☰** en haut à gauche ;
- la navigation se referme automatiquement après un clic sur un lien ;
- les tableaux défilent horizontalement, les formulaires passent sur une colonne et les
  cartes s'empilent sur petit écran.

## 10. App native (Capacitor) — Play Store / App Store

Le frontend est empaqueté avec **Capacitor 8** (`frontend/capacitor.config.json`,
plateformes `frontend/android/` et `frontend/ios/`) pour publier l'application sur le
**Play Store** (Android) et l'**App Store** (iOS). Le webview charge les fichiers
construits (`frontend/dist/`).

```bash
cd frontend
npm run cap:sync        # build Vite + cap sync (copie dist → android/ et ios/)
npm run cap:android     # sync puis ouvre le projet dans Android Studio
npm run cap:ios         # sync puis ouvre le projet dans Xcode (macOS requis)
```

Les **icônes et splash screens natifs** sont générés depuis `frontend/assets/*.svg`
(camion vectoriel, fond `#2563eb`) via `@capacitor/assets`. Pour les régénérer après
un changement de marque :

```bash
cd frontend
npx capacitor-assets generate --android --ios
```

**Build local de l'APK Android** (SDK requis — command-line tools, platform `android-36`,
build-tools `36.0.0`, chemin dans `frontend/android/local.properties` qui est ignoré par
git) :

```bash
cd frontend/android
./gradlew.bat assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat bundleRelease          # → AAB signé (Play Store), à configurer au préalable
```

Configuration clé :
- **appId** `fr.fleethub.app` et **appName** « Fleet Hub » dans `capacitor.config.json`.
- **URL de l'API** : dans le webview natif, l'app n'utilise plus le proxy Vite. Construisez
  le bundle avec `VITE_API_BASE_URL` pointant vers le backend de production :
  `npm run build -- --mode production` avec `VITE_API_BASE_URL=https://api.exemple.fr/api`
  (la valeur doit inclure le préfixe `/api`). Une surcharge au runtime est possible via
  `localStorage.fh_api_base` (utile en test sur un appareil).
- **Réseau** : le schéma du webview est `https` et le trafic en clair est bloqué
  (`cleartext: false`) — le backend doit être servi en HTTPS. **En debug uniquement**, le HTTP
  local est permis (manifest debug `usesCleartextTraffic` + `MIXED_CONTENT_ALWAYS_ALLOW` dans
  `MainActivity`, gardé par `BuildConfig.DEBUG`) pour pointer vers un backend de dev
  (`http://10.0.2.2:8090/api` sur l'émulateur).
- **Chiffrement** : pensez à régénérer les icônes et splash screens avant publication
  (`npx @capacitor/assets generate`) et à incrémenter `versionCode`/`versionName` dans
  `android/app/build.gradle` à chaque release.

Builds natifs : **Android** compilable en ligne de commande (voir ci-dessus) ou via
**Android Studio** ; **Xcode** sur macOS requis pour l'App Store.

## 11. Roadmap (étapes suivantes)

**SaaS — fait :** multi-tenancy (isolation `company_id`), inscription autonome, essai 14 j,
plans avec limites, back-office plateforme, **gel des données en essai expiré / suspension**
(login conservé pour s'abonner ou régulariser, données 402 hors facturation et RGPD),
**facturation Stripe** (checkout, portail, webhooks, suspension automatique), **RGPD /
mentions légales** (export, journal d'audit, suppression, CGU/confidentialité),
**gestion des utilisateurs** (invitation, rôles, activation), **notifications** (alertes
configurables, emails transactionnels).

**Fonctionnel — ensuite :**
1. **Brancher les vraies APIs** : balise GPS (polling `@Scheduled` + cache Redis) et AS24/tachygraphe
2. **Import des coûts réels** : carte carburant, factures maintenance (CSV/API)
3. **Alertes temps réel** : seuils de temps de conduite, immobilisations imprévues (socle en place)
4. **Redis** : cache des positions en temps réel
5. **CI/CD + monitoring** : Log4j, métriques, tests frontend

> **Intégrations externes (AS24, tachygraphe, GPS, carburant)** : un squelette prêt à
> remplir est déjà en place (`backend/src/main/java/com/fleethub/integration/`). Voir
> [`INTEGRATION.md`](INTEGRATION.md) pour le guide complet.

---

Fait pour être un **outil de décision** : chaque ligne du tableau de bord est un couple
Chauffeur × Camion avec son score composite, et chaque score se décompose jusqu'à la cause racine.
