# Architecture IncoKalk

> Vue d'ensemble de l'architecture technique de la plateforme IncoKalk.

---

## Vue d'ensemble

IncoKalk est une application SaaS monolithique (backend Spring Boot + frontend React) déployée via Docker Compose (dev) et Kubernetes (prod). Elle couvre le cycle complet de la supply chain : simulation Incoterms, expédition, dédouanement, facturation, et reporting.

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                     │
│  React 19 + TypeScript + Vite + Tailwind + PWA            │
│  react-router (lazy) + Zustand + React Query + Axios      │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS (Nginx reverse proxy)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                      NGINX (Proxy)                         │
│  - SSL termination                                        │
│  - Rate limiting                                          │
│  - CORS configuration                                     │
│  - Proxy /api → backend, / → frontend                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│   BACKEND    │ │  FRONTEND    │ │   MinIO (S3)     │
│  Spring Boot │ │  Vite (SSR)  │ │   Storage        │
│  Java 21     │ │  React       │ │   (documents,    │
│  Port 8081   │ │  Port 5174   │ │    photos scans) │
└──────┬───────┘ └──────────────┘ └──────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│                      PostgreSQL 16                         │
│  - Multi-tenant (company_id sur chaque table)             │
│  - Flyway migrations (V1-V46)                             │
│  - Indexes sur company_id, foreign keys                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        Redis 7                             │
│  - Cache taux TARIC (TTL 24h)                             │
│  - Rate limiting (Bucket4j)                               │
│  - Sessions temporaires                                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Backend

### Package structure

```
com.incokalk/
├── controller/
│   ├── auth/          # Authentification, RBAC
│   ├── shipment/      # Expéditions, tracking, items
│   ├── warehouse/     # Entrepôts, inventaire, réception
│   ├── compliance/    # Douanes (TARIC, DAU, ICS2, DEB)
│   ├── financial/     # Facturation, Stripe, landed cost
│   ├── config/        # Configuration multi-tenant, ERP
│   ├── analytics/     # Dashboard & statistiques
│   ├── notification/  # Notifications push/email
│   ├── portal/        # Portail client
│   ├── shared/        # Export, documents, webhooks
│   ├── carbon/        # CO₂, CSRD
│   └── training/      # Academy / formations
├── service/
│   ├── warehouse/     # InventoryService, ReceivingService, WarehouseService
│   ├── shipment/      # ShipmentService (+ adapters transporteurs)
│   ├── ecommerce/     # Shopify, WooCommerce, PrestaShop adapters
│   ├── erp/           # Odoo, QuickBooks, SAP B1 adapters
│   ├── carrier/       # DHL, Geodis, MSC, CMA CGM, DB Schenker
│   ├── provider/      # CarrierProvider, DHLCarrierProvider...
│   └── ...            # Billing, Compliance, Notification, etc.
├── model/             # Entités JPA (60+)
├── repository/        # Spring Data JPA (60+)
├── dto/               # Data Transfer Objects (request/response)
├── security/          # JWT, API Key, RateLimitFilter, RolesAllowedAspect
├── aspect/            # AuditAspect, RolesAllowedAspect
├── exception/         # GlobalExceptionHandler, ResourceNotFoundException
├── tenant/            # TenantContext (ThreadLocal multi-tenant)
└── scheduler/         # Jobs programmés (quota reset, tracking sync...)
```

### Multi-tenant architecture

Chaque requête HTTP est filtrée par `TenantFilter` qui extrait le `X-Tenant-ID` header et le stocke dans `TenantContext` (ThreadLocal). Tous les repositories filtrent automatiquement par `company_id`.

```java
// Exemple dans un repository
@Query("SELECT e FROM Entity e WHERE e.companyId = :companyId AND e.id = :id")
Optional<Entity> findByCompanyIdAndId(@Param("companyId") UUID companyId, @Param("id") UUID id);
```

### Sécurité

| Couche | Mécanisme |
|---|---|
| Authentification | JWT Bearer (JJWT 0.12.3) + API Keys |
| Autorisation | RBAC via `@RolesAllowed` (OWNER, ADMIN, MANAGER, USER) |
| Rate limiting | Bucket4j — 60 req/min/IP, quotas par plan |
| CORS | Configuré via `SecurityConfig` |
| Validation | Jakarta Validation (`@Valid`, `@NotBlank`, `@NotNull`) |
| Audit | `AuditAspect` loggue toutes les opérations CRUD |

### Flux de données typique

```
Client → Nginx → Backend (JWT validation → TenantFilter → Controller → Service → Repository → DB)
                    ↑
              AuditAspect (log)
              RateLimitFilter (throttle)
              RolesAllowedAspect (authorize)
```

---

## Frontend

### Architecture

```
React 19 + TypeScript 5.9 + Vite 5.4
├── Routing (react-router-dom v7, lazy-loaded)
├── State (Zustand pour auth, React Query pour serveur)
├── API (Axios avec interceptors JWT + tenant)
├── UI (Tailwind CSS + composants custom)
├── PWA (manifest + service worker)
└── Tests (Vitest + Testing Library + Playwright)
```

### State management

| Store | Usage |
|---|---|
| `auth` | Authentification, utilisateur, rôle, token |
| `clientAuth` | Portail client (authentification séparée) |
| React Query | Données serveur (caching, invalidation, refetch) |

### API Layer

Le client API (`frontend/src/lib/api.ts`) centralise tous les appels :

- **Base URL** : `VITE_API_URL` (fallback `/api`)
- **Interceptors** : ajout JWT + `X-Tenant-ID` automatiques
- **Refresh token** : single-flight (un seul appel en cours)
- **54 groupes d'endpoints** couvrant tous les domaines

---

## Infrastructure

### Docker Compose (dev)

```
Services (8) :
├── postgres      : PostgreSQL 16 (port 5432)
├── redis         : Redis 7 (port 6379)
├── minio         : MinIO S3 (port 9000/9001)
├── minio-init    : Initialise les buckets
├── backup        : Cron backup MinIO (toutes les 2h)
├── api           : Backend Spring Boot (port 8081)
├── frontend      : Frontend Vite (port 5174)
└── nginx         : Reverse proxy (ports 80/443)
└── pgAdmin       : Interface DB (port 5050, profil dev uniquement)
```

### Kubernetes (prod)

Manifests dans `infrastructure/k8s/` :
- Namespace `incokalk`
- ConfigMap + Secrets (placeholders)
- PostgreSQL StatefulSet
- Redis Deployment
- MinIO Deployment
- Backend Deployment (2 replicas, HPA 2-10)
- Frontend Deployment (2 replicas)
- Ingress + cert-manager (TLS)

### CI/CD

GitHub Actions (`.github/workflows/ci.yml`) :
1. Test backend (JUnit 5 + Testcontainers)
2. Lint + test frontend (Vitest)
3. Build backend (Maven) + build frontend (Vite)
4. Publication des images Docker sur GHCR
5. Upload Codecov

---

## Base de données

### Multi-tenant

Chaque table contient un champ `company_id UUID NOT NULL`. Tous les queries filtrent par `company_id`.

### Migrations Flyway

46 migrations (V1-V46) couvrant :
- V1-V44 : fondations (users, companies, roles, incoterms, shipments, customs, finance, etc.)
- V45 : warehouse/receiving module
- V46 : shipment items

### Index principaux

- `company_id` sur toutes les tables (filtrage multi-tenant)
- `shipment_id` sur `shipment_items`
- `warehouse_id + item_id` (unique) sur `stock_balances`
- `company_id + barcode` (unique) sur `item_barcodes`

---

## Technologies tierces

| Service | Usage |
|---|---|
| Stripe | Paiement et facturation |
| DHL / Geodis / MSC / CMA CGM / DB Schenker / Shippo | Transporteurs (fallback simulation) |
| Shopify / WooCommerce / PrestaShop | E-commerce |
| Odoo / QuickBooks / SAP B1 | ERP |
| AviationStack / Vessel API (AIS) / Ship24 | Tracking (fallback simulation) |
| OpenPDF / PDFBox | Génération PDF |
| Tess4J / Apache Tika | OCR documents |
| AWS SDK S3 | Stockage MinIO |
```
