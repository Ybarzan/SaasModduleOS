# Plan de migration en 3 phases

> Hypothèse de calibrage : équipe actuelle (1-2 développeurs full-stack + IA en pair-programming, cadence observée sur l'historique de commits IncoKalk). Les durées sont en semaines-calendaires à cette cadence, pas en semaines-personnes classiques de cabinet de conseil — voir [05-estimation-couts-risques.md](05-estimation-couts-risques.md) pour la conversion et un scénario à effectif renforcé.

```mermaid
gantt
    dateFormat  YYYY-MM-DD
    title Migration IncoKalk → plateforme d'orchestration (indicatif, démarrage arbitraire)
    section Phase 1 — Visibilité
    Schéma canonique + event store       :p1a, 2026-09-01, 3w
    Marquage LIVE/SIMULATED              :p1b, after p1a, 1w
    E-commerce écriture (Shopify)        :p1c, after p1a, 2w
    section Phase 2 — ETA scoring
    Capture ETA multi-source             :p2a, after p1b, 2w
    Table fiabilité par source           :p2b, after p2a, 2w
    UI transparence du score             :p2c, after p2b, 1w
    section Phase 3 — Orchestrateur
    Bus d'événements (outbox)            :p3a, after p2a, 2w
    Moteur de règles structuré           :p3b, after p3a, 3w
    Exécuteur d'actions                  :p3c, after p3b, 3w
    Config langage naturel               :p3d, after p3b, 2w
    Pilote client + durcissement         :p3e, after p3c, 3w
```

---

## Phase 1 — Mise à niveau de la couche de visibilité (6 semaines)

**Objectif** : passer d'adaptateurs qui alimentent directement les tables métier à un event store canonique, sans casser aucune fonctionnalité existante.

| Jalon | Livrable | Critère de sortie |
|---|---|---|
| J1 | Schéma `ShipmentEvent` défini + table event store (partitionnée `company_id`) | Migration Flyway V60 revue, ADR (Architecture Decision Record) validé |
| J2 | Les 5 `CarrierAdapter` + 2 `ErpProvider` + `ShopifyAdapter` publient dans l'event store en plus de leur comportement actuel (double écriture) | Tests de non-régression backend verts, aucun endpoint existant modifié |
| J3 | Flag `dataSource: LIVE\|SIMULATED` propagé jusqu'au frontend | Le badge est visible sur `ShipTracker.tsx` et toute vue tracking — **fait, mais côté fleet-hub** : le domaine transport (réservations transporteur, tracking) a été déplacé de compliance-engine vers fleet-hub (migration `V75__drop_transport_domain.sql`) après l'écriture de ce plan, ce qui explique l'absence de `ShipTracker.tsx` dans compliance-engine aujourd'hui. `CarrierBookingRequest.simulated` et `TrackingEvent.dataSource` sont correctement remplis côté backend fleet-hub et affichés (`CarrierBookings.jsx`, `Shipments.jsx`, `ClientShipmentDetail.jsx`) |
| J4 | `ECommerceAdapter.pushOrderStatus/pushEta` implémenté pour Shopify | Un changement de statut IncoKalk se reflète dans Shopify sous 5 min |
| J5-J6 | Bascule des lectures existantes (dashboard, portail client) de la lecture directe des tables vers des projections issues de l'event store | Dashboard et portail client passent aux nouvelles projections, ancien chemin de lecture retiré |

**Ne pas faire en Phase 1** : ne pas toucher au moteur de règles ni à l'ETA — c'est une fondation de données, elle doit être invisible pour l'utilisateur final au démarrage (double écriture, bascule progressive).

---

## Phase 2 — Moteur ETA avec scoring (5 semaines)

**Objectif** : transformer le blend interne existant (Python/Java/heuristique) en agrégateur multi-source incluant les ETA réellement rapportés.

| Jalon | Livrable | Critère de sortie |
|---|---|---|
| J1-J2 | Capture systématique de l'ETA annoncé au booking (déjà dans `BookingResponse`, non persisté après coup) + ETA dérivé AIS | Table `eta_reported_sources`, alimentée à chaque booking et chaque cycle de polling AIS — **booking fait 2026-10-28** (fleet-hub, V14) : `CarrierBookingService.submitBooking()` capture `EtaReportedSource` (source `CARRIER_BOOKING`) à chaque réservation confirmée, lane/mode figés depuis `ShipmentOrder` au moment de l'observation. **ETA dérivé AIS non fait** : aucune expédition n'est aujourd'hui liée à un MMSI de navire suivi (`AisStreamService` n'alimente qu'une carte de suivi temps réel, voir `TrackingMapController`) — lier une expédition réelle à un navire suivi reste un chantier séparé, pas comblé ici. |
| J3-J4 | Table `eta_source_reliability` (transporteur × lane × mode), calculée depuis l'écart historique réel (`TrackingEvent` vs `EtaPrediction` passées) | Job planifié hebdomadaire, historique d'au moins 4 semaines de données avant mise en production du poids calculé — **fait 2026-10-28** (fleet-hub, V15, `EtaSourceReliabilityService.recomputeWeekly()`, cron hebdomadaire lundi 3h) : recalcul complet (pas d'incrément) de l'écart moyen signé et absolu entre `EtaReportedSource.reportedArrival` et `ShipmentOrder.actualDeliveryDate`, groupé par transporteur × pays origine/destination × mode. Le seuil "4 semaines de données" est approximé par un nombre minimal d'échantillons (`MIN_RELIABILITY_SAMPLES = 20` dans `EtaPredictionService`, voir J5) — ce service ne connaît pas l'âge des observations, seulement leur nombre. |
| J5 | Agrégateur final : `confidencePercent` devient fonction du blend multi-source pondéré, pas uniquement du modèle ML interne | A/B interne : comparer l'ancien score vs le nouveau sur les 90 derniers jours d'expéditions clôturées, MAE mesurée — **mécanisme fait 2026-10-28**, **A/B non fait** (nécessite des données de production réelles, indisponibles dans cet environnement) : `EtaPredictionService.applyReliabilityWeighting()` pondère la confiance heuristique existante par la fiabilité mesurée dès que `sampleCount >= 20`, avec repli strict sur l'heuristique seule sinon (donnée insuffisante) ou si la prédiction n'est pas rattachée à une expédition réelle (`EtaPredictionController.predict` accepte désormais un `shipmentId` optionnel, jusque là silencieusement ignoré). La formule de blend est un point de départ raisonnable et borné comme l'heuristique existante, pas calibrée par l'A/B prévu ci-dessus — à mesurer une fois un historique de production disponible. |

**Dépendance dure sur Phase 1** : l'agrégateur lit l'event store, pas les tables directement — Phase 2 ne peut pas commencer avant que J2 de Phase 1 soit stable (mais peut chevaucher J3-J6 de Phase 1, cf. Gantt).

---

## Phase 3 — Orchestrateur autonome (10-13 semaines, la plus lourde)

**Objectif** : fermer la boucle savoir → faire, en commençant par le cas d'usage à plus forte valeur perçue (réallocation stock ou ajustement fournisseur sur retard, à confirmer avec 2-3 clients pilotes avant de généraliser).

| Jalon | Livrable | Critère de sortie |
|---|---|---|
| J1-J2 | Bus d'événements (pattern outbox PostgreSQL + `ApplicationEventPublisher`) | Les événements ETA dégradé / statut changé transitent par le bus, `EventPublisher` actuel migré dessus sans régression notification — **`SHIPMENT_STATUS_CHANGE` fait 2026-08-23** (table `event_outbox` V63, `EventOutboxProcessor` planifié avec retry/abandon après 5 tentatives) ; **`SHIPMENT_CREATED` migré 2026-08-24**, même pattern (`ShipmentCreatedPayload`, écrit dans la même transaction que `ShipmentService.createShipment`). **Régression découverte et corrigée 2026-10-28** : le retrait du domaine Transport de compliance-engine (commit `dc1e04d`, 2026-10-12 -- voir [07-integration-fleet-hub.md](07-integration-fleet-hub.md)) a supprimé `ShipmentService`/`EventPublisher`, laissant `EventOutboxProcessor.dispatch()` sans aucun producteur pendant ~2 semaines (rejetait tout événement reçu). Rebranché via un nouveau webhook `fleet-hub -> compliance-engine` (`FleetHubEventWebhookController`, authentifié par clé hashée, opt-in par société) : `ShipmentEventPublisher` de fleet-hub (qui possède désormais `ShipmentOrder`/`TrackingEvent`) pousse ces deux événements en best-effort, `EventOutboxProcessor.dispatch()` les retraite avec les `eventType` historiques inchangés -- toute `NotificationRule` existante redevient fonctionnelle sans reconfiguration. `providerStatusChanged`/`quoteReceived` **volontairement laissés sur l'ancien chemin `@Async`** : audit du code (2026-08-24) confirme qu'ils n'ont aucun appelant réel aujourd'hui (`NotificationService.onProviderDown/onProviderRecovered/onQuoteReceived` existent mais rien ne les déclenche) — migrer de la plomberie autour de méthodes mortes n'aurait rien apporté ; à faire quand un vrai déclencheur (ex. health-check fournisseur) sera branché. **J1-J2 clos pour tout ce qui a un déclencheur réel aujourd'hui.** |
| J3-J5 | Moteur de règles structuré (condition composée → action candidate → contrainte de gouvernance) | Schéma de règle versionné, moteur d'évaluation testé sur 20+ scénarios de règles représentatifs — **fait 2026-08-23** : condition composée (`RuleConditionNode`/`RuleConditionEvaluator`, AND/OR/8 opérateurs, `NotificationRule.conditionJson` V64) + action candidate typée (`actionType`, validé, crée une `OrchestrationSuggestion` jamais exécutée) + gouvernance (`requiresApproval`/`maxBudgetAmount`/`allowedCarrierIds`, V65). **Constructeur visuel fait 2026-08-24** (`ConditionTreeEditor.tsx`, arbre ET/OU imbriqué dans `NotificationRules.tsx`) — la chaîne complète (créer une règle avec condition composée + action + gouvernance, jusqu'à l'approbation et l'exécution) est pilotable depuis l'UI, plus aucune étape API-only |
| J6-J8 | Exécuteur d'actions coordonnées — **1 seul cas d'usage complet en premier** (recommandé : ajustement commande fournisseur via `ErpProvider.exportOrders` sur ETA dégradé confirmé) | Un scénario de bout en bout démontrable : ETA se dégrade → règle se déclenche → commande ajustée dans Odoo/SAP, journal d'audit complet — **fait 2026-08-24** : `OrchestrationExecutor` (V66) déclenché automatiquement par `OrchestrationSuggestionService.approve()` (l'approbation humaine EST le déclencheur d'exécution) — charge l'expédition, vérifie la gouvernance (`allowedCarrierIds`/`maxBudgetAmount`, désormais réellement bloquante et non plus seulement déclarative), appelle `ErpProvider.exportOrders(config, List.of(shipment))`, fait passer la suggestion à `EXECUTED`/`FAILED` avec `executionResult` journalisé séparément de la note de décision humaine. **Cassé depuis le retrait du domaine Transport (2026-10-12)** : `SUGGEST_ERP_ORDER_ADJUSTMENT` reposait entièrement sur `ShipmentOrderRepository`, retiré avec le reste -- `OrchestrationExecutor.execute()` échoue désormais systématiquement toute suggestion (`FAILED`), par construction et documenté comme tel dans le code. Gap explicitement laissé pour un chantier séparé (pas comblé par la correction du 2026-10-28 ci-dessus, qui ne restaure que la création de suggestions `PENDING_APPROVAL` et les notifications in-app/email/webhook -- pas leur exécution automatique). **À refaire : adapter l'exécuteur pour appeler `FleetHubIntegrationClient`/l'API fleet-hub plutôt qu'un repository local, avant que J6-J8 soit de nouveau réellement clos.** |
| J9-J10 | Configuration en langage naturel (LLM → règle structurée → validation humaine obligatoire avant activation) | Interface no-code testée par un non-développeur (client pilote), taux de règles correctement traduites mesuré manuellement sur 30 formulations |
| J11-J13 | Pilote avec 2-3 clients réels, durcissement (limites budgétaires, transporteurs autorisés, rollback d'action, alerting sur échec d'action) | Pilote validé, cadre de gouvernance (budget/permissions) effectivement bloquant en test, pas seulement déclaratif |

**Ce que la Phase 3 ne couvre volontairement pas au premier tour** : réallocation de stock multi-entrepôt et replanification de tournée sont des cas d'usage 2 et 3 — à ajouter après validation du premier cas d'usage sur un pilote réel, pas en parallèle (risque de diluer l'effort sur un moteur encore non éprouvé en production).

---

## Dépendances et risques de séquencement

- Phase 2 dépend de Phase 1 (event store comme source de lecture) mais peut démarrer sa capture de données avant que Phase 1 soit 100% terminée (chevauchement volontaire dans le Gantt).
- Phase 3 dépend de Phase 1 (bus consomme l'event store) mais **pas** de Phase 2 au sens strict — le moteur de règles peut se déclencher sur un changement de statut simple avant que le scoring ETA multi-source soit fini. Ne pas bloquer Phase 3 sur l'achèvement complet de Phase 2.
- Le point de non-retour commercial est la fin de Phase 1 J6 (bascule des lectures) — prévoir une fenêtre de gel des nouvelles features produit pendant cette semaine-là, comme pour toute migration de source de vérité.
