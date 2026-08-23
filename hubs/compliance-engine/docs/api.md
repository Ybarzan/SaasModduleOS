# API IncoKalk

> Documentation de l'API REST IncoKalk.

---

## Accès à la documentation interactive

La documentation Swagger UI est disponible au démarrage du backend :

```
http://localhost:8081/api/swagger-ui.html
```

L' OpenAPI spec JSON est disponible à :

```
http://localhost:8081/api/v3/api-docs
```

---

## Base URL

| Environnement | URL |
|---|---|
| Dev local | `http://localhost:8080/api` |
| Docker Compose | `http://localhost:8081/api` |
| Production | `https://api.incokalk.com/api` |

Toutes les routes sont préfixées par `/api/v1`.

---

## Authentification

### JWT Bearer

Ajouter l'en-tête à chaque requête :

```
Authorization: Bearer <jwt_token>
```

### API Key

Pour les intégrations tierces :

```
X-API-Key: <api_key>
```

### Multi-tenant

Chaque requête doit inclure l'en-tête :

```
X-Tenant-ID: <company_uuid>
```

---

## Endpoints principaux

### Authentification

| Méthode | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/auth/login` | Connexion (email + mot de passe) |
| POST | `/api/v1/auth/register` | Inscription |
| POST | `/api/v1/auth/refresh` | Rafraîchir le token JWT |
| POST | `/api/v1/auth/validate-api-key` | Valider une clé API |

### Expéditions

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/shipments` | Lister les expéditions | USER+ |
| GET | `/api/v1/shipments/{id}` | Détail d'une expédition | USER+ |
| POST | `/api/v1/shipments` | Créer une expédition | MANAGER+ |
| PUT | `/api/v1/shipments/{id}` | Mettre à jour | MANAGER+ |
| DELETE | `/api/v1/shipments/{id}` | Supprimer | ADMIN |
| GET | `/api/v1/shipments/{id}/items` | Lister les articles | USER+ |
| POST | `/api/v1/shipments/{id}/items` | Ajouter un article | MANAGER+ |
| DELETE | `/api/v1/shipments/{id}/items` | Supprimer tous les articles | ADMIN |
| GET | `/api/v1/shipments/{id}/tracking` | Tracking en temps réel | USER+ |

### Entrepôts

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/warehouses` | Lister les entrepôts | USER+ |
| GET | `/api/v1/warehouses/{id}` | Détail d'un entrepôt | USER+ |
| POST | `/api/v1/warehouses` | Créer un entrepôt | MANAGER+ |
| PUT | `/api/v1/warehouses/{id}` | Mettre à jour | MANAGER+ |
| DELETE | `/api/v1/warehouses/{id}` | Désactiver | ADMIN |

### Inventaire

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/inventory/items` | Lister les articles (filtre ?q=) | USER+ |
| GET | `/api/v1/inventory/items/{id}` | Détail d'un article | USER+ |
| POST | `/api/v1/inventory/items` | Créer un article | MANAGER+ |
| PUT | `/api/v1/inventory/items/{id}` | Mettre à jour | MANAGER+ |
| DELETE | `/api/v1/inventory/items/{id}` | Désactiver | ADMIN |
| GET | `/api/v1/inventory/resolve?barcode=...` | Résoudre un code-barres | USER+ |
| GET | `/api/v1/inventory/items/{itemId}/barcodes` | Lister les codes-barres | USER+ |
| POST | `/api/v1/inventory/items/{itemId}/barcodes` | Associer un code-barres | MANAGER+ |
| DELETE | `/api/v1/inventory/items/{itemId}/barcodes/{barcodeId}` | Retirer un code-barres | MANAGER+ |
| GET | `/api/v1/inventory/balances?warehouseId=...` | Soldes de stock | USER+ |
| GET | `/api/v1/inventory/movements?itemId=...` | Mouvements de stock | MANAGER+ |
| POST | `/api/v1/inventory/adjustments` | Ajuster le stock | MANAGER+ |

### Réception

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/receivings` | Lister les bons (filtres ?status=, ?warehouseId=, ?shipmentId=) | USER+ |
| GET | `/api/v1/receivings/{id}` | Détail d'un bon (lignes, scans, écarts) | USER+ |
| POST | `/api/v1/receivings` | Créer un bon | MANAGER+ |
| POST | `/api/v1/receivings/{id}/lines` | Ajouter une ligne attendue | MANAGER+ |
| POST | `/api/v1/receivings/{id}/scan` | Scanner un article (code-barres/QR) | USER+ |
| POST | `/api/v1/receivings/{id}/damage` | Signaler un endommagé | MANAGER+ |
| POST | `/api/v1/receivings/{id}/complete` | Clôturer le bon | MANAGER+ |
| POST | `/api/v1/receivings/{id}/cancel` | Annuler le bon | ADMIN |
| GET | `/api/v1/receivings/discrepancies` | Lister les écarts | MANAGER+ |
| POST | `/api/v1/receivings/discrepancies/{id}/resolve` | Résoudre un écart | MANAGER+ |

### Douanes

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/compliance/customs` | Calculer les droits de douane | USER |
| GET | `/api/v1/compliance/taric` | Rechercher dans TARIC | MANAGER+ |
| POST | `/api/v1/compliance/declarations` | Créer une déclaration (DAU/DEB/ICS2) | MANAGER+ |
| GET | `/api/v1/compliance/eori` | Valider un numéro EORI | MANAGER+ |
| GET | `/api/v1/compliance/denied-party` | Screening sanctions (OFAC/UN/UK) | MANAGER+ |

### Finance

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| POST | `/api/v1/financial/billing` | Générer une facture client | ADMIN |
| GET | `/api/v1/financial/invoices` | Lister les factures | MANAGER+ |
| POST | `/api/v1/financial/carrier-invoices` | Facturer un transporteur | ADMIN |
| GET | `/api/v1/financial/landed-cost` | Calculer le landed cost | MANAGER+ |
| POST | `/api/v1/financial/payment-terms` | Créer des termes de paiement | ADMIN |

### E-Commerce

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/config/ecommerce` | Lister les intégrations | MANAGER+ |
| POST | `/api/v1/config/ecommerce/sync` | Lancer une synchronisation | MANAGER+ |
| GET | `/api/v1/config/ecommerce/sync-logs` | Historique des syncs | MANAGER+ |

### ERP

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/config/erp` | Lister les configs ERP | MANAGER+ |
| POST | `/api/v1/config/erp/sync` | Lancer une synchronisation ERP | MANAGER+ |
| GET | `/api/v1/config/erp/health` | Vérifier la connexion ERP | MANAGER+ |

### Analytics

| Méthode | Endpoint | Description | Rôle |
|---|---|---|---|
| GET | `/api/v1/analytics/dashboard` | Statistiques du dashboard | MANAGER+ |
| GET | `/api/v1/analytics/shipments` | Statistiques expéditions | MANAGER+ |
| GET | `/api/v1/analytics/carriers` | Performance transporteurs | MANAGER+ |

---

## Codes d'erreur

| Code HTTP | Signification |
|---|---|
| 401 | Non authentifié (JWT manquant ou expiré) |
| 403 | Interdit (rôle insuffisant) |
| 404 | Ressource non trouvée |
| 409 | Conflit (doublon, ressource déjà existante) |
| 422 | Validation failed (champ requis manquant, format invalide) |
| 429 | Trop de requêtes (rate limit atteint) |
| 500 | Erreur interne du serveur |

### Format d'erreur

```json
{
  "timestamp": "2026-07-31T15:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Expédition non trouvée",
  "path": "/api/v1/shipments/uuid-inexistant"
}
```

---

## Rate Limiting

Les quotas sont appliqués par plan :

| Plan | Requêtes/minute |
|---|---|
| FREE | 10 |
| PRO | 500 |
| API_STARTER | 2000 |
| API_PRO | 10000 |

Les en-têtes de réponse incluent :

```
X-RateLimit-Limit: 500
X-RateLimit-Remaining: 498
X-RateLimit-Reset: 1722400000
```

---

## WebSocket (temps réel)

Le tracking en temps réel utilise WebSocket pour les mises à jour de statut d'expédition :

```
ws://localhost:8081/api/v1/shipments/{id}/tracking/ws
```

Les événements envoyés :
- `TRACKING_UPDATED` : nouveau point de tracking
- `SHIPMENT_STATUS_CHANGED` : changement de statut
- `ETA_UPDATED` : nouvelle estimation d'arrivée
