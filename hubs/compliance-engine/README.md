# IncoKalk

> Plateforme SaaS de décision supply chain : **Incoterms 2020**, douanes françaises (TARIC, DAU, DEB, ICS2), tracking temps réel, e-commerce, ERP, finance et CSRD. Interface 100% en français, marque marocaine (palette zellige/sable/olive/medina/terracotta).

---

## Sommaire

- [Présentation](#présentation)
- [Stack](#stack)
- [Architecture](#architecture)
- [Infrastructure](#infrastructure)
- [Démarrage rapide](#démarrage-rapide)
- [Tests](#tests)
- [Ce qui manque / points de vigilance](#ce-qui-manque--points-de-vigilance)
- [Roadmap](#roadmap)
- [Structure du dépôt](#structure-du-dépôt)

---

## Présentation

IncoKalk est un calculateur et outil d'opérations logistiques. Il couvre le cycle complet **devis → expédition → dédouanement → facturation → reporting** :

- **Simulation Incoterms 2020** : 11 codes, matrice acheteur/vendeur, scores de risque, landed cost, douane/TVA, assurance
- **Expédition & tracking** : transporteurs maritimes/aériens/routiers, cartes temps réel, ETA prédictive
- **Douanes françaises** : TARIC, DAU (DEB), ICS2, export, EORI, EUR.1, screening sanctions (OFAC/UN/UK), classification HS + OCR
- **Intégrations** : Shopify / WooCommerce / PrestaShop, Odoo / QuickBooks / SAP B1, Stripe
- **Plateforme** : multi-tenant, RBAC, API keys avec quotas, audit, multi-branche, white-label, académie, CSRD, portail client, PWA

---

## Stack

| Couche | Technologie |
|---|---|
| **Backend** | Java 21, Spring Boot 3.3.5, Maven (`com.incokalk:incokalk-backend:1.0.0`) |
| **Frontend** | React 19, TypeScript 5.9, Vite 5.4, Tailwind 3.4 |
| **Bases** | PostgreSQL 16, Redis 7, MinIO (S3) |
| **Proxy** | Nginx |
| **Mobile** | PWA (manifest + service worker) |
| **Tests backend** | JUnit 5, Testcontainers, JaCoCo, MockMvc, E2E |
| **Tests frontend** | Vitest + Testing Library, Playwright |
| **CI/CD** | GitHub Actions → images GHCR + Codecov |
| **Orchestration** | Docker Compose + manifests Kubernetes (kustomize) |

### Backend (détail)

- **Spring Boot 3.3.5, Java 21**, context-path `/api`
- JWT (JJWT 0.12.3) + API keys, bucket4j 8.7.0 (rate limiting / quotas)
- PostgreSQL + H2 (test), Flyway (44 migrations, `ddl-auto: validate` en prod), Redis
- springdoc-openapi 2.3.0 (Swagger UI sur `/api/swagger-ui.html`)
- Stripe, openpdf / pdfbox 3.0.3, tess4j 5.12.0 (OCR), tika, opencsv
- AWS SDK S3 2.25.27 (MinIO), Jakarta Mail, Lombok, Testcontainers 1.19.3

### Frontend (détail)

- **React 19 + TypeScript 5.9 + Vite 5.4**, PWA
- react-router-dom 7.13, zustand 4.5 (stores), @tanstack/react-query 5.20 (état serveur), axios
- recharts 3.8 (analytics), leaflet 1.9 + react-leaflet 5.0 (cartes tracking), jspdf 4.2
- vitest 4.1 + @testing-library/react, @playwright/test 1.49
- 73 pages (105 fichiers), guards par rôle, lazy-loading

---

## Architecture

### Backend

- **65 contrôleurs REST** (préfixe `/api/v1`), **107 services**, **60 entités JPA**, **60 repositories**
- **44 migrations Flyway** (V1 → V44)
- Domaines : cœur métier (simulation, freight rates, douane/TVA, quotes), expédition & tracking (5 adapters transporteurs avec *fallback simulation* sans clé API), douanes FR (DAU/ICS2/EORI/DPS/OCR), intégrations e-commerce & ERP, finance (Stripe, facturation, supply-chain finance, TVA), plateforme (JWT, RBAC, multi-tenant `X-Tenant-ID`, audit, quotas par plan)
- **Sécurité** : JWT Bearer + X-API-Key, rate limiting 60 req/min/IP, quotas par plan (FREE=10, PRO=500, API_STARTER=2000, API_PRO=10000)
- **Jobs programmés** : reset quotas (0h), sync tracking (5 min), sync e-commerce (5 min), retrain ETA ML (2h), sync TARIC (désactivé par défaut)

### Frontend

- Routing react-router v7 (lazy) + guards par rôle
- State : Zustand (auth) + React Query (serveur)
- API layer centralisé : `frontend/src/lib/api.ts` (54 groupes d'endpoints, refresh token single-flight)
- Calculateurs (landed cost, douane, CO₂, poids volumétrique, assurance, itinéraire), dashboard analytics, déclarations douanières, tracking temps réel (vessels + vols), portail client, PWA offline

---

## Infrastructure

### Docker Compose (`infrastructure/docker/`)

8 services : `postgres`, `redis`, `minio`, `minio-init`, `backup` (cron 2h), `api`, `frontend`, `nginx` + `pgAdmin` (profil `dev`).

Ports par défaut :

| Service | Port |
|---|---|
| API | 8081 |
| Frontend | 5174 |
| Nginx | 80/443 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| MinIO | 9000/9001 |
| pgAdmin (profil dev) | 5050 |

> Ces ports sont surchargés via `.env` (`infrastructure/docker/.env`) : `API_PORT`, `FRONTEND_PORT`, `CORS_ORIGINS`, plus les secrets (DB, MinIO, Stripe, transporteurs...). Le fichier `.env` est ignoré par git — s'appuyer sur `.env.example` si présent.

### Kubernetes

Manifests kustomize (`infrastructure/k8s/`) : namespace, configmap, secrets (placeholders), postgres StatefulSet, redis, minio, backend ×2 replicas, frontend ×2, ingress + cert-manager, HPA (2–10).

### CI/CD

`.github/workflows/ci.yml` : test + build backend, lint + test + build frontend, publication images GHCR (branche `master`), upload Codecov. **Pas de workflow de déploiement.**

---

## Démarrage rapide

### Option A — Docker (tout-en-un)

```bash
cd infrastructure/docker
cp .env.example .env   # si dispo, sinon créer .env depuis les valeurs de docker-compose
docker compose up -d --build
```

Puis :
- Frontend : http://localhost:5174
- API / Swagger : http://localhost:8081/api/swagger-ui.html
- MinIO console : http://localhost:9001
- pgAdmin (profil dev) : http://localhost:5050

### Option B — Dev local (sans Docker)

Backend (port 8080) :

```bash
cd backend
mvn spring-boot:run
```

Frontend (port 5173) :

```bash
cd frontend
npm install
npm run dev
```

> Les deux modes peuvent tourner simultanément (Docker sur 8081/5174, dev local sur 8080/5173).

---

## Tests

```bash
# Backend (JUnit 5 + Testcontainers)
cd backend
mvn test

# Frontend (Vitest)
cd frontend
npm test

# E2E (Playwright)
cd frontend
npx playwright test
```

État actuel : **905 tests backend + 112 tests frontend, tous verts.**

---

## Ce qui manque / points de vigilance

### Bugs corrigés ✅
1. **Backup MinIO réparé** : image backup personnalisée (`infrastructure/docker/backup/Dockerfile`) avec le client `mc` installé → l'upload vers MinIO fonctionne (vérifié end-to-end)
2. **Stripe transmis à Docker** : `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` et les prix Stripe sont passés au service `api` dans docker-compose
3. **`ProviderHealth.tsx` attaché au router** : route `/providers/health` ajoutée
4. **`package.json`** : clé `scripts` dupliquée fusionnée
5. **`VITE_API_URL` utilisé** : `baseURL` de l'API lis `import.meta.env.VITE_API_URL` (fallback `/api`, proxy Vite/nginx)
6. **Restes `.github/java-upgrade/` et `.github/modernize/`** : supprimés
7. **Secrets sécurisés** : secret JWT réel de `backend/bdkey.env` régénéré ; `infrastructure/k8s/secret.yaml` aligné sur les noms attendus (`DB_PASSWORD`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`)
8. **Healthcheck frontend Docker réparé** : `wget http://127.0.0.1/health` au lieu de `localhost` (bug IPv6)

### Documentation
- Pas de README backend ; `docs/` : api.md, architecture.md, deployment.md, ROADMAP-90JOURS.md (stratégie d'audit) + internal/ (archive brainstorm)
- README frontend = template Vite par défaut (à réécrire)

### Fonctionnel / produit
- TARIC en mode simulation par défaut (`TARIC_SYNC_ENABLED=false`)
- APIs externes transporteurs/tracking = fallback simulé sans clés
- Pas de déploiement automatisé (serveur/K8s)
- Émail intake + OCR désactivés par défaut
- Dashboard Python Dash = séparé du dépôt (voir ROADMAP)

---

## Roadmap

Le fichier **[ROADMAP.md](ROADMAP.md)** détaille les 31 features planifiées en 6 phases (P0 fondations douanes → P5 intégrations gouvernementales), avec les priorités quick-win.

---

## Structure du dépôt

```
IncoKalk/
├── backend/               # Spring Boot (API REST, jobs, intégrations)
│   ├── src/main/java/     # 65 contrôleurs, 107 services, 60 entités
│   └── src/main/resources/ # application.yml + profils dev/prod/test + migrations Flyway
├── frontend/              # React 19 + Vite (73 pages, PWA)
│   └── src/               # pages/, lib/api.ts, stores, components, tests
├── infrastructure/
│   ├── docker/            # docker-compose.yml + .env
│   ├── nginx/             # reverse proxy
│   └── k8s/               # manifests kustomize
└── .github/workflows/     # ci.yml (test, build, GHCR, Codecov)
```
