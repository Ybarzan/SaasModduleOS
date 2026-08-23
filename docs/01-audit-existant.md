# Audit de l'existant — IncoKalk vs architecture cible à 3 couches

> Périmètre audité : dépôt `IncoKalk` (backend Spring Boot 3.2 / Java 21, frontend React 19, PostgreSQL 16, 59 migrations Flyway, ~130+ classes de service). Audit basé sur lecture directe du code (services `tracking/`, `carrier/`, `erp/`, `ecommerce/`, `ml/`, `notification/`, `scheduling/`) le 2026-08-23.

## Verdict en une phrase

IncoKalk n'est **pas** un simple tableau de bord de visibilité passive — c'est un socle de données de commerce international déjà mature (douane, transporteurs, ERP bidirectionnel, ETA prédictif avec score de confiance). Ce qui manque n'est pas la donnée, c'est la **couche d'action** : aucun moteur de règles conditionnelles au-delà d'un filtrage par égalité, aucun bus d'événements, et aucune écriture automatique vers l'aval déclenchée par un événement métier.

---

## Couche 1 — Visibilité unifiée : ce qui existe déjà

| Domaine | État réel | Preuve |
|---|---|---|
| Transporteurs | 5 adaptateurs (`DHLAdapter`, `CmaCgmAdapter`, `DBSchenkerAdapter`, `GeodisAdapter`, `MSCAdapter`) derrière une interface commune `CarrierAdapter` (`getCarrierCode`, `submitBooking`, `getBookingStatus`, `cancelBooking`) | `service/carrier/` |
| ERP | Interface `ErpProvider` **bidirectionnelle** : `importProducts/importOrders/importContacts` + `exportShipments/exportOrders`. Implémentations Odoo et SAP B1. Chaque sync journalisé (`ErpSyncLog`, direction IMPORT/EXPORT par type d'objet) | `service/erp/`, `ErpSyncService.java` |
| E-commerce | `ECommerceAdapter` implémenté pour Shopify — **lecture seule** (`syncOrders`, `mapOrderToShipment`), aucune écriture vers la plateforme e-commerce | `service/ecommerce/ShopifyAdapter.java` |
| Tracking | Interface `TrackingProvider`, 4 implémentations (air/mer/route/AIS). Ingestion **hybride** : polling actif toutes les 5 min (`ScheduledTasksConfig.autoSyncInTransitShipments`) + webhooks entrants signés (`/v1/webhooks/dhl`, `/shippo`, `/generic`) | `service/tracking/`, `V9__tracking_webhooks.sql` |
| Schéma de données | 59 migrations Flyway (V1→V59), multi-tenant strict (`company_id` sur chaque table) | `backend/src/main/resources/db/migration/` |

### Écarts vs la cible « source unique de vérité »

1. **Pas de couche de normalisation d'entrée commune.** Chaque client transporteur (`DhlBookingApiClient`, etc.) parse le JSON propriétaire « à la main » champ par champ. Il existe bien un DTO de sortie unifié (`BookingResponse`), mais aucun schéma canonique d'événement (« shipment event ») partagé entre transporteurs, ERP et e-commerce. Trois silos de vérité (shipment, warehouse, erp_sync_log) plutôt qu'un modèle d'ingestion unique.
2. **Le tracking est mono-source par expédition.** `TrackingProviderRegistry` sélectionne **un seul** provider par type de transport — il ne fusionne jamais plusieurs sources pour la même expédition. Le DTO `TrackingUpdate` n'a pas de champ `confidence` ni de traçabilité multi-source.
3. **Fallback en simulation silencieux.** Quand une clé API transporteur n'est pas configurée, l'adaptateur bascule sur des données **simulées** (`DHLAdapter.simulateBooking`) sans distinction visible pour l'utilisateur final entre donnée réelle et donnée inventée — un vrai risque si le produit se positionne comme « source unique de vérité ».
4. **E-commerce à sens unique.** Aucun adaptateur n'écrit d'information (statut, ETA) retour vers Shopify/WooCommerce/PrestaShop — la boucle n'est fermée que côté ERP.
5. **Pas de connecteur TMS/WMS externe.** Le module « warehouse » (réception, inventaire) est un WMS *interne* à IncoKalk, pas un connecteur vers un WMS tiers (Manhattan, Infios, etc.) — cohérent avec une PME qui n'a pas de WMS externe, mais à clarifier si des clients cibles en ont un.

---

## Couche 2 — Moteur ETA avec score de confiance : ce qui existe déjà

C'est la couche la plus avancée du produit actuel — **bien au-delà d'un simple affichage de dates**.

- Cascade à 3 niveaux dans `EtaPredictionService.predict()` : (1) microservice Python externe (LightGBM/XGBoost, `EtaMlClient`) pondéré par `blendWeight`, (2) modèle de régression Java en mémoire si ≥10 échantillons (`EtaRegressionModel`), (3) heuristique pure (base transit + facteur saisonnier + facteur congestion portuaire + délai douane estimé).
- Score de confiance déjà persisté par prédiction : `EtaPrediction.confidencePercent` (BigDecimal) + `ConfidenceLevel` (HIGH/MEDIUM/LOW), recalculé à chaque prédiction.
- Facteurs de correction déjà réels et à jour (ex. re-routage Cap de Bonne-Espérance vs Suez intégré dans `LANE_BASE_DAYS` suite à la fermeture Mer Rouge).

### Écart vs la cible

La cible demande d'**agréger plusieurs ETA rapportés** (transporteur, AIS, historique, météo — « 4 à 5 par expédition ») et de retourner un ETA consensus pondéré par la fiabilité de chaque source. Ce qui existe est l'inverse : **un seul modèle qui produit une seule prédiction combinée en interne** — la pondération se fait entre modèles de prédiction (Python/Java/heuristique), pas entre sources d'ETA indépendantes rapportées par le terrain (l'ETA annoncé par le transporteur lui-même n'est aujourd'hui pas capturé comme une entrée concurrente au modèle prédictif). Le gap est donc plus étroit qu'il n'y paraît : il s'agit d'ajouter une **source supplémentaire au blend existant**, pas de construire un moteur de scoring depuis zéro.

---

## Couche 3 — Orchestrateur autonome : l'écart réel

C'est ici que se situe l'essentiel du travail de refonte.

| Ce qui existe | Ce qui manque |
|---|---|
| `NotificationRule` : règle par entreprise avec `eventType`, filtres (`filterStatus`, `filterCarrierId` — égalité stricte uniquement), 3 canaux (email, webhook signé HMAC, in-app) | Aucun moteur de règles conditionnelles composées (ET/OU, seuils, comparaisons) — encore moins en langage naturel |
| `EventPublisher` : dispatcher interne `@Async`, appelle directement `NotificationService` | **Aucun bus d'événements** — pas de Kafka/RabbitMQ, pas même `ApplicationEventPublisher` Spring. Tout est un appel de méthode synchrone en dur. Aucun rejeu, aucune garantie de livraison |
| 4 endpoints webhooks sortants + entrants, tous point-à-point HTTP | Pas de propagation automatique d'un changement d'ETA vers WMS/ERP/portail client — seule une notification est envoyée, aucune **action** n'est déclenchée |
| `ApprovalWorkflow` (entité) | Limité au contexte réception d'entrepôt, pas un moteur de workflow générique cross-système |
| `ErpProvider.exportShipments/exportOrders` (écriture existe déjà techniquement) | Rien ne déclenche ces méthodes d'export automatiquement en réaction à un événement (ex. ETA dégradé → ajustement commande fournisseur) — elles sont probablement invoquées de façon manuelle/planifiée, pas événementielle |
| — | **Aucun moteur de règles Drools/Camunda/BPMN** — recherche exhaustive négative sur tout le dépôt |

**Conclusion Couche 3** : le produit sait notifier qu'un problème existe (« savoir »), mais rien dans le code n'agit dessus (« faire »). C'est exactement le fossé que la promesse « fermer l'écart entre savoir et faire » doit combler — et c'est une construction quasiment neuve, pas une évolution incrémentale.

---

## Autres constats utiles pour le chiffrage

- **Base de code saine et testée** : ~2000 tests backend (JaCoCo ~76% instructions / 61% branches), 678 tests frontend couvrant toutes les pages fonctionnelles, CI GitHub Actions avec SpotBugs. Un refactor de cette ampleur part d'un socle qui ne cassera pas silencieusement.
- **Multi-tenant strict déjà en place** (`TenantContext` ThreadLocal + filtrage `company_id`) — la gouvernance par périmètre utilisateur (principe « cadre défini par l'utilisateur ») a une fondation technique à réutiliser plutôt qu'à inventer.
- **3 jobs planifiés protégés par verrou distribué** (`DistributedJobLock`, vraisemblablement Redis) + plusieurs `@Scheduled` dispersés hors du module `scheduling/` — à consolider avant d'y accrocher un orchestrateur, sous peine de dupliquer la logique de verrouillage.
- **Point de blocage commercial sans lien avec cette refonte** : le checkout Stripe est non fonctionnel (prix jamais créés côté Stripe Dashboard) — ne bloque pas la refonte technique mais bloque la monétisation en parallèle ; à traiter indépendamment.
