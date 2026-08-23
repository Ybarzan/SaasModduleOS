# IncoKalk Backend

> API REST Spring Boot pour la plateforme SaaS IncoKalk — calculateur et outil d'opérations logistiques (Incoterms 2020, douanes françaises, tracking, e-commerce, ERP, finance, CSRD).

---

## Stack

| Composant | Technologie |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Build | Maven |
| Base de données | PostgreSQL 16 (prod), H2 (test) |
| Migrations | Flyway (46 migrations) |
| Cache | Redis 7 |
| Stockage objets | MinIO (S3-compatible) |
| Sécurité | JWT (JJWT 0.12.3) + API Keys + Bucket4j (rate limiting) |
| API REST | springdoc-openapi 2.3.0 (Swagger UI) |
| Tests | JUnit 5, Testcontainers, JaCoCo, MockMvc |
| Logging | SLF4J + Logback |

---

## Structure du projet

```
backend/
├── src/main/java/com/incokalk/
│   ├── IncoKalkApplication.java
│   ├── config/            # Configuration (Security, CORS, etc.)
│   ├── controller/        # Controllers REST
│   │   ├── auth/          # Authentification, RBAC
│   │   ├── shipment/      # Expéditions, tracking
│   │   ├── compliance/    # Douanes, TARIC, DAU, ICS2
│   │   ├── financial/     # Facturation, Stripe, landed cost
│   │   ├── warehouse/     # Entrepôts, inventaire, réception
│   │   ├── config/        # Configuration multi-tenant
│   │   ├── analytics/     # Dashboard & statistiques
│   │   ├── notification/  # Notifications push/email
│   │   ├── portal/        # Portail client
│   │   ├── shared/        # Export, documents, webhooks
│   │   ├── carbon/        # CO₂, CSRD
│   │   └── training/      # Academy / formations
│   ├── service/           # Logique métier
│   │   ├── warehouse/     # Inventory, Receiving, Warehouse
│   │   ├── ecommerce/     # Sync Shopify/WooCommerce/PrestaShop
│   │   ├── erp/           # Sync Odoo/QuickBooks/SAP
│   │   ├── carrier/       # Adapters transporteurs
│   │   └── provider/      # Providers (DHL, Geodis, MSC...)
│   ├── model/             # Entités JPA
│   ├── repository/        # Spring Data JPA repositories
│   ├── dto/               # Data Transfer Objects
│   ├── security/          # JWT, API Key, Rate Limiting
│   ├── aspect/            # Audit, RolesAllowed
│   ├── exception/         # Global exception handling
│   ├── tenant/            # Multi-tenant (X-Tenant-ID)
│   └── scheduler/         # Jobs programmés
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── application-test.yml
│   └── db/migration/      # Flyway migrations V1-V46
└── src/test/
    └── java/com/incokalk/ # Tests unitaires + intégration
```

---

## Démarrage rapide

### Prérequis

- Java 21+
- Maven 3.9+
- PostgreSQL 16
- Redis 7
- MinIO (optionnel, pour le stockage de fichiers)

### Lancement local

```bash
cd backend

# Copier la configuration
cp src/main/resources/application-dev.yml src/main/resources/application-local.yml  # si nécessaire

# Lancer l'application
mvn spring-boot:run
```

L'API démarre sur `http://localhost:8080/api`.

Swagger UI : `http://localhost:8080/api/swagger-ui.html`

### Variables d'environnement

| Variable | Description | Défaut |
|---|---|---|
| `DB_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5432/incokalk` |
| `DB_USERNAME` | Utilisateur DB | `postgres` |
| `DB_PASSWORD` | Mot de passe DB | `postgres` |
| `JWT_SECRET` | Clé secrète JWT | (générée automatiquement en dev) |
| `STRIPE_SECRET_KEY` | Clé Stripe | (optionnel) |
| `MINIO_URL` | URL MinIO | `http://localhost:9000` |
| `REDIS_URL` | URL Redis | `redis://localhost:6379` |

---

## Architecture

### Multi-tenant

Chaque requête HTTP doit inclure l'en-tête `X-Tenant-ID` identifiant la société (company). Le filtre `TenantFilter` extrait cette valeur et la stocke dans `TenantContext` (ThreadLocal). Tous les repositories filtrent automatiquement par `company_id`.

### Sécurité

- **JWT Bearer** : token JWT dans l'en-tête `Authorization: Bearer <token>`
- **API Keys** : clé API dans l'en-tête `X-API-Key` (pour les intégrations tierces)
- **Rate Limiting** : 60 requêtes/minute par IP (Bucket4j)
- **Quotas par plan** : FREE=10, PRO=500, API_STARTER=2000, API_PRO=10000 requêtes/minute
- **RBAC** : rôles `OWNER`, `ADMIN`, `MANAGER`, `USER` — annotation `@RolesAllowed`

### Contrôleurs

Préfixe commun : `/api/v1`

| Préfixe | Domaine | Rôles autorisés |
|---|---|---|
| `/v1/warehouses` | Entrepôts | USER+ |
| `/v1/inventory` | Catalogue & stock | USER+ |
| `/v1/receivings` | Réception marchandises | USER+ |
| `/v1/shipments` | Expéditions | USER+ |
| `/v1/auth/*` | Authentification | PUBLIC |
| `/v1/compliance/*` | Douanes | MANAGER+ |
| `/v1/financial/*` | Finance | MANAGER+ |
| `/v1/admin/*` | Administration | OWNER+ |

### Services

Les services sont organisés par domaine :

- **Warehouse** : `WarehouseService`, `InventoryService`, `ReceivingService`
- **Shipment** : `ShipmentService` (création, statut, items, tracking)
- **ECommerce** : `ECommerceSyncService` (sync Shopify/WooCommerce/PrestaShop)
- **ERP** : `ErpSyncService` (sync Odoo/QuickBooks/SAP B1)
- **Carrier** : Adapters pour DHL, Geodis, MSC, CMA CGM, DB Schenker, Shippo
- **Compliance** : `CustomsDutyService`, `Ics2DeclarationService`, `DebDeclarationService`
- **Financial** : `BillingService`, `CarrierInvoiceService`, `ClientInvoiceService`, `LandedCostService`

### Modèles Warehouse/Receiving

| Entité | Table | Description |
|---|---|---|
| `Warehouse` | `warehouses` | Entrepôt (site de réception/storage) |
| `InventoryItem` | `inventory_items` | Catalogue articles (SKU, HS code, origine) |
| `ItemBarcode` | `item_barcodes` | Codes-barres multiples par article |
| `StockBalance` | `stock_balances` | Solde par entrepôt / article |
| `StockMovement` | `stock_movements` | Historique des mouvements (audit trail) |
| `ReceivingOrder` | `receiving_orders` | Bon de réception (ASN) |
| `ReceivingOrderLine` | `receiving_order_lines` | Lignes attendues d'un bon de réception |
| `ReceivingScan` | `receiving_scans` | Scans code-barres/QR lors de la réception |
| `Discrepancy` | `discrepancies` | Écarts (manquant, excédent, endommagé, imprévu) |
| `ShipmentItem` | `shipment_items` | Articles liés à une expédition |

---

## Tests

```bash
# Tous les tests (JUnit 5 + Testcontainers)
mvn test

# Couverture JaCoCo
mvn test jacoco:report

# Tests spécifiques à un module
mvn test -pl . -Dtest=InventoryServiceTest
```

### Couverture actuelle

- **931 tests** au total
- **0 échec**
- Couverture JaCoCo : ~85% des lignes backend

---

## Jobs programmés

| Job | Fréquence | Description |
|---|---|---|
| `quotaReset` | 0h00 | Réinitialisation des quotas quotidiens |
| `trackingSync` | 5 min | Synchronisation des tracking numbers |
| `ecommerceSync` | 5 min | Sync commandes e-commerce |
| `etaRetrain` | 2h | Retraînement modèle ETA prédictif |
| `taricSync` | Désactivé par défaut | Sync données TARIC |

---

## Intégrations externes

### Transporteurs (fallback simulation)

| Transporteur | Adapter | Mode |
|---|---|---|
| DHL | `DHLAdapter` | Simulation (sans clé) |
| Geodis | `GeodisAdapter` | Simulation |
| MSC | `MSCAdapter` | Simulation |
| CMA CGM | `CmaCgmAdapter` | Simulation |
| DB Schenker | `DBSchenkerAdapter` | Simulation |
| Shippo | `ShippoCarrierProvider` | Simulation |

### E-Commerce

| Plateforme | Adapter |
|---|---|
| Shopify | `ShopifyAdapter` |
| WooCommerce | `WooCommerceAdapter` |
| PrestaShop | `PrestaShopAdapter` |

### ERP

| Système | Adapter |
|---|---|
| Odoo | `OdooAdapter` |
| QuickBooks | `QuickBooksAdapter` |
| SAP B1 | `SapB1Adapter` |

### Paiement

- **Stripe** : intégration pour facturation client (clés configurables via `.env`)

---

## Configuration des profils

| Profil | Usage |
|---|---|
| `dev` | Développement local (H2, pgAdmin, MinIO auto) |
| `prod` | Production (PostgreSQL réel, Redis, MinIO externe) |
| `test` | Tests (Testcontainers PostgreSQL + Redis) |

---

## Logs

Les logs sont écrits dans la console (stdout) au format structuré. En production, configurez un appender fichier dans `logback-spring.xml`.

Niveau par défaut : `INFO`

---

## Points de vigilance

1. **JWT secret** : ne jamais commiter la clé réelle — utiliser `backend/bdkey.env` (ignoré par git)
2. **Stripe keys** : passer par `.env` en production, jamais en dur dans le code
3. **MinIO buckets** : le service `FileStorageService` crée les buckets au démarrage (nécessite MinIO accessible)
4. **Multi-tenant** : toujours valider que `TenantContext.get()` est présent avant toute opération DB
5. **Flyway** : les migrations doivent être séquentielles (V45, V46, ...) — ne jamais renommer une migration déjà appliquée en prod
6. **Rate limiting** : les quotas par plan sont appliqués via Bucket4j — surveiller en prod
7. **Healthcheck** : endpoint `/actuator/health` exposé pour Kubernetes liveness/readiness probes

---

## Contact & Support

Pour toute question technique sur le backend, contactez l'équipe IncoKalk via Slack `#incokalk-backend` ou ouvrez une issue sur GitHub.
