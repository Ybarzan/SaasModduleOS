# Nouveaux composants techniques à développer

Classés par couche, avec pour chacun : ce qu'il fait, sur quoi il s'appuie dans le code existant, et sa complexité relative.

## Couche 1 — Visibilité

### `ShipmentEvent` — schéma canonique
Modèle pivot (origine, destination, statut normalisé sur une énumération commune, horodatage, source, `dataSource: LIVE|SIMULATED`, payload brut en JSONB pour audit). Chaque adaptateur existant (`CarrierAdapter`, `ErpProvider`, `ECommerceAdapter`) gagne une méthode `toShipmentEvent()`.
**Complexité : moyenne.** Le travail difficile n'est pas technique mais de modélisation — trouver un statut commun qui couvre les vocabulaires DHL/MSC/Odoo/Shopify sans perte d'information (le payload brut JSONB sert de filet de sécurité).

### Event store append-only
Table PostgreSQL partitionnée (`company_id`, `shipment_id`), ou extension TimescaleDB si le volume d'événements par expédition devient significatif (probable au-delà de quelques centaines de milliers d'expéditions/mois — à mesurer, pas à anticiper).
**Complexité : faible à moyenne.** PostgreSQL suffit largement au volume actuel ; ne pas introduire TimescaleDB avant d'en avoir la preuve par la mesure.

### Connecteur d'écriture e-commerce
Extension de `ECommerceAdapter` avec `pushOrderStatus`/`pushEta`, implémenté pour Shopify d'abord (API GraphQL Admin, webhooks natifs disponibles côté Shopify).
**Complexité : faible.** Shopify a une API d'écriture bien documentée ; le pattern d'adaptateur existe déjà côté lecture.

---

## Couche 2 — Moteur ETA

### Capture d'ETA multi-source
Table `eta_reported_sources` (shipment_id, source_type, eta_value, reported_at). Alimentée par le booking transporteur (donnée déjà disponible dans `BookingResponse`, simplement non persistée aujourd'hui après coup) et par le flux AIS existant.
**Complexité : faible.** La donnée existe déjà dans le système, il s'agit de la capturer plutôt que de la recalculer.

### Table de fiabilité par source
`eta_source_reliability` (carrier/source × lane × mode → poids), recalculée par un job planifié à partir de l'écart réel mesuré (`TrackingEvent` réel vs `EtaPrediction`/`eta_reported_sources` passés).
**Complexité : moyenne.** Le calcul statistique est simple (MAE glissante par source), la difficulté est le volume de données historique nécessaire avant que les poids soient fiables (~4-8 semaines minimum, cf. plan de migration).

### Agrégateur pondéré
Fusionne `eta_reported_sources` + la prédiction interne existante (`EtaPredictionService`) via les poids de `eta_source_reliability`, produit un ETA consensus + `confidencePercent` détaillé par source.
**Complexité : moyenne.** S'appuie sur une infrastructure déjà là (cascade Python/Java/heuristique) — c'est une extension du blend existant, pas une reconstruction.

---

## Couche 3 — Orchestrateur (la vraie nouveauté)

### Bus d'événements (pattern outbox)
Table `event_outbox` + worker de publication, ou `ApplicationEventPublisher` Spring en interne selon le besoin de découplage inter-services. Garantit qu'un événement métier (ETA dégradé, statut changé) est livré même en cas d'échec transitoire d'un consommateur.
**Complexité : moyenne.** Pattern bien connu, mais c'est une pièce d'infrastructure critique — à tester en isolation (rejeu, idempotence) avant de brancher le moteur de règles dessus.

### Moteur de règles structuré
Schéma de règle : `condition` (arbre de comparaisons composées AND/OR sur des champs de `ShipmentEvent`/`EtaPrediction`), `actions candidates` (typées, pas du texte libre), `contraintes de gouvernance` (budget max, transporteurs/entrepôts autorisés, seuil de validation humaine).
**Complexité : élevée.** C'est le composant le plus stratégique et le plus risqué à mal concevoir — privilégier un moteur maison simple (interprète d'arbre de conditions en Java, pas de DSL exotique) plutôt qu'intégrer Drools/Camunda, dont la courbe d'apprentissage et le poids opérationnel ne sont pas justifiés à cette échelle.

**Partie `condition` faite, 2026-08-23** : `RuleConditionNode` (interprète maison, exactement l'approche recommandée ci-dessus, pas de DSL) + `RuleConditionEvaluator`, branché sur `NotificationRule.conditionJson`. AND/OR imbriqués, 8 opérateurs (EQ/NEQ/CONTAINS/IN/GT/GTE/LT/LTE). Coexiste avec l'ancien filtrage plat (`filterStatus`/`filterCarrierId`/`filterDataSource`) sans le casser — une règle utilise l'un ou l'autre, jamais les deux.

**Parties `actions candidates` + `contraintes de gouvernance` faites, 2026-08-23** : `NotificationRule.actionType` (typé, validé contre une liste connue — pas de texte libre) + `requiresApproval`/`maxBudgetAmount`/`allowedCarrierIds` comme champs de gouvernance (V65). Quand une règle a un `actionType`, elle crée en plus de la notification une `OrchestrationSuggestion` — **jamais une exécution**, toujours `PENDING_APPROVAL`. `requiresApproval`/`maxBudgetAmount`/`allowedCarrierIds` sont persistés et validés (budget non-négatif) mais **pas encore vérifiés par du code d'exécution**, puisque l'exécuteur qui les consommerait n'existe pas encore — c'est exactement la préparation attendue avant l'exécuteur d'actions ci-dessous, pas une anticipation inutile. Premier (et seul) type d'action reconnu : `SUGGEST_ERP_ORDER_ADJUSTMENT`. Pas de constructeur de règles visuel (AND/OR imbriqués, choix d'action) côté frontend pour l'instant — tout ce chantier est backend + API.

### Exécuteur d'actions coordonnées
Traduit une décision de règle en appel vers un service existant (`ErpProvider.exportOrders`, `WarehouseService`, `NotificationService`, futur connecteur TMS d'écriture). Journalise systématiquement décision + données déclenchantes + résultat (succès/échec/rollback).
**Complexité : élevée**, principalement à cause de la gestion d'échec partiel (que faire si l'ajustement ERP réussit mais la notification client échoue ?) — nécessite un pattern de saga simple (compensation par étape) dès le premier cas d'usage, pas ajouté après coup.

**Flux d'approbation fait, 2026-08-23** : `OrchestrationSuggestionService` + `OrchestrationSuggestionController` (`/v1/orchestration-suggestions`, GET liste/détail, POST `{id}/approve`/`{id}/reject`, rôle OWNER/ADMIN/MANAGER requis, plan PRO minimum). Machine à états simple (`PENDING_APPROVAL` → `APPROVED`/`REJECTED`, transition impossible depuis un autre statut) avec horodatage + utilisateur + note de décision.

**Exécuteur fait, 2026-08-24** : `OrchestrationExecutor` (V66, colonne `execution_result`). Déclenché par `OrchestrationSuggestionService.approve()` lui-même — l'approbation humaine sert directement de déclencheur d'exécution, pas de file d'attente séparée entre `APPROVED` et l'appel réel. Pour `SUGGEST_ERP_ORDER_ADJUSTMENT` (seul type reconnu) : charge l'expédition liée à la suggestion, **applique désormais réellement** `allowedCarrierIds` (comparé à `shipment.getCarrier()`) et `maxBudgetAmount` (comparé à `finalCost`, sinon `quotedCost`) — ces champs étaient persistés/validés depuis V65 mais jamais vérifiés, ils bloquent maintenant effectivement une exécution qui les enfreint (échec avec raison explicite dans `executionResult`, aucun appel ERP déclenché). Si la gouvernance passe : trouve la config ERP active de l'entreprise (`ErpConfigRepository.findByCompanyIdAndIsActiveTrue`, première trouvée), résout le connecteur (`ErpProviderRegistry.getProvider`), appelle `exportOrders(config, List.of(shipment))` — un export ciblé sur la seule expédition concernée, pas le sync en masse habituel. Statut final `EXECUTED`/`FAILED` avec `executionResult` (distinct de `decisionNote`, qui reste la note humaine). Ne lève jamais d'exception — toute erreur inattendue est capturée et journalisée comme `FAILED`, jamais propagée à l'appelant.

**Sur la gestion d'échec partiel / saga** : ce premier cas d'usage ne fait qu'un seul appel externe (l'export ERP) — pas de coordination multi-système, donc pas de pattern de saga/compensation nécessaire ici (l'appel réussit ou échoue, sans état intermédiaire à compenser). Le pattern de saga décrit ci-dessus reste à construire **quand** un deuxième type d'action coordonnera plusieurs systèmes (ex. ajustement ERP + notification client + réallocation entrepôt) — volontairement pas anticipé avant d'avoir un vrai second cas d'usage.

**Phase 3 J1-J8 entièrement close** (bus d'événements, moteur de règles, gouvernance, approbation, exécution) — reste J9-J13 (langage naturel, pilote client).

**Interface de décision humaine faite, 2026-08-24** : `OrchestrationSuggestions.tsx`, premier consommateur frontend — jusque-là toute la Phase 3 (bus, moteur de règles, gouvernance, approbation, exécution) n'était accessible que par appel API direct, aucun humain ne pouvait réellement approuver/rejeter une suggestion sans Postman. Page à onglets (En attente / Historique), décision via modale avec note optionnelle, tags de statut bordés (`[EN ATTENTE]`/`[APPROUVÉ]`/`[REJETÉ]`/`[EXÉCUTÉ]`/`[ÉCHOUÉ]`) dans le registre Praxio v0.2. `OrchestrationSuggestion.rule` était `@JsonIgnore` (protège contre la sérialisation de l'entité lazy complète) donc le nom de la règle n'atteignait jamais le frontend — corrigé par un getter dérivé `getRuleName()` plutôt qu'une couche DTO, cohérent avec la convention existante du code (contrôleurs qui sérialisent l'entité JPA directement, DTO réservés à l'écriture, cf. `NotificationRuleController`).

### Interface no-code en langage naturel
Un LLM (Claude déjà utilisé pour d'autres suggestions dans le produit — cf. `HsMlService`) traduit une instruction utilisateur en règle structurée du moteur ci-dessus, présentée pour validation humaine avant activation. Jamais d'exécution directe de la sortie du LLM.
**Complexité : moyenne.** Le risque n'est pas technique (le pattern LLM → structure validée est bien maîtrisé) mais produit : l'UI de relecture/validation doit rendre une règle mal traduite évidente à corriger, pas juste "approuver en un clic".

---

## Ce qui n'a volontairement pas de nouveau composant dédié

- **Multi-tenant / gouvernance de base** : `TenantContext` + RBAC existants suffisent, étendus avec les nouveaux champs de contrainte (budget, transporteurs autorisés) plutôt que remplacés.
- **Connecteurs transporteurs/ERP/e-commerce en lecture** : les interfaces `CarrierAdapter`/`ErpProvider`/`ECommerceAdapter` restent le point d'entrée ; seule leur sortie change de format (vers `ShipmentEvent`).
- **Réplanification de tournée** : `OptimizationService` existe déjà pour un usage manuel — la Phase 3 le rend *déclenchable automatiquement* par l'exécuteur d'actions, sans le reconstruire.
