# ADR-0001 : Où (et si) introduire un event store canonique

- **Statut** : proposé — en attente de revue
- **Date** : 2026-10-29
- **Contexte du plan** : `docs/03-plan-migration.md`, Phase 2/3 (observabilité cross-hub, historique des événements)

## Contexte

`docs/02-architecture-cible.md` et `docs/04-composants-techniques.md` décrivent une
architecture cible à 3 couches avec un **event store append-only** (table
`shipment_events` ou équivalent) comme source de vérité pour :
- l'historique des changements de statut d'une expédition,
- les projections de dashboard (au lieu de lectures directes sur les tables
  métier),
- une base pour calculer des métriques comme `port_historical_delay_mean`.

Ces deux documents ont été écrits **avant** la migration du domaine Transport
(commit `dc1e04d`, 2026-10-12), à une époque où tous les adaptateurs
producteurs d'événements (`CarrierAdapter` ×5, `ShipmentOrder`,
`TrackingEvent`) vivaient dans un seul hub (`compliance-engine`). Depuis cette
migration :

- `ShipmentOrder`, `TrackingEvent` et les `CarrierAdapter` vivent dans
  **fleet-hub**.
- Le moteur de règles (`NotificationRule`) et l'orchestrateur
  (`OrchestrationSuggestion`, `OrchestrationExecutor`) restent dans
  **compliance-engine**.
- Les deux hubs communiquent déjà par un **pont de webhooks** prouvé dans les
  deux sens : `FleetHubEventWebhookController` / `ShipmentEventPublisher`
  (fleet-hub → compliance-engine, événements
  `FLEETHUB_SHIPMENT_STATUS_CHANGE` / `FLEETHUB_SHIPMENT_CREATED`) et
  `ComplianceEngineWebhookController` (compliance-engine → fleet-hub),
  authentifiés par clé API (`ApiKeyCrypto`).

Deux faits du design d'origine doivent être corrigés avant de trancher :

1. **`event_outbox` (compliance-engine, migration V63) n'est pas un event
   store.** C'est une file de **livraison** at-least-once, transitoire :
   statuts `PENDING` → `PROCESSED`/`FAILED`, jusqu'à 5 tentatives, vidée/purgée
   après traitement. Elle n'a jamais été conçue pour conserver un historique
   permanent ni pour servir de base à des projections. La confondre avec un
   event store serait une erreur de conception.
2. **`port_historical_delay_mean`** (cité ligne 72 de
   `02-architecture-cible.md` comme « déjà en base côté douane ») **n'existe
   nulle part dans le code**, vérifié par recherche exhaustive. C'est une
   affirmation aspirationnelle du document d'origine, pas un fait. Toute
   décision prise ici doit repartir de zéro sur ce point, pas présumer qu'une
   base de calcul existe déjà.

Le besoin concret aujourd'hui, que cet ADR doit couvrir, est plus modeste que
« un event store global » : avoir un historique fiable et consultable des
événements d'expédition (créations, changements de statut, ETA capturées) —
pour l'instant surtout consommé par le dashboard de fleet-hub
(`DashboardService`, `InternalDashboardSummaryService`,
`InternalDashboardController`), qui aujourd'hui lit **directement** dans les
tables métier locales (pas de couche de projection, pas d'historique au-delà
de l'état courant).

## Facteurs de décision

- Ne rien casser : le plan de migration fixe explicitement comme critère de
  sortie « aucun endpoint existant modifié ».
- Les deux hubs ont des bases Postgres séparées ; pas d'accès direct
  inter-base (règle non négociable du projet).
- Aucune des deux bases n'a de volume de données qui justifie une techno
  spécialisée (TimescaleDB, partitionnement) — aucun précédent dans
  l'historique de migration de l'un ou l'autre hub, aucune preuve de charge.
- Le pont de webhooks existant fonctionne et est déjà testé en production
  interne ; toute solution qui l'oblige à changer de contrat augmente le
  risque pour un gain incertain.
- Il n'existe aujourd'hui aucun besoin métier exprimé pour une vue
  **globale, cross-hub, consolidée** des événements (pas de requirement
  « je veux voir en un seul endroit tout ce qui s'est passé sur une
  expédition, douane et transport confondus »). Construire ça maintenant
  serait spéculatif.

## Options considérées

### Option A — Event store dans fleet-hub uniquement

Table `shipment_events` (append-only) dans la base fleet-hub, écrite
directement par les services qui produisent déjà les changements d'état
(`ShipmentService`, `CarrierBookingService`, `AisEtaCaptureService`, etc.).
Le dashboard de fleet-hub lit cette table au lieu des tables métier.

- ✅ Couvre le seul consommateur concret identifié aujourd'hui
  (`DashboardService` de fleet-hub).
- ✅ Aucun changement au pont de webhooks existant.
- ❌ Ne couvre pas un futur besoin de projection côté compliance-engine (ex. :
  un dashboard douanier qui voudrait l'historique transport) sans dupliquer
  le travail plus tard.

### Option B — Event store dans compliance-engine uniquement, en étendant `event_outbox`

Ajouter une écriture permanente (table séparée, pas `event_outbox` lui-même)
alimentée par le flux de webhooks entrant déjà reçu par
`FleetHubEventWebhookController`.

- ✅ Réutilise un chemin d'ingestion déjà prouvé.
- ❌ Ne couvre pas les événements purement internes à fleet-hub qui ne sont
  pas poussés par webhook aujourd'hui (ex. : capture ETA AIS, qui n'a aucune
  raison de déclencher un webhook vers compliance-engine).
- ❌ Mélange conceptuellement le rôle de `event_outbox` (livraison) avec un
  rôle d'archive (historique), même si on les sépare physiquement — risque
  de confusion future si quelqu'un relie les deux par erreur.

### Option C — Fédéré : un event store local par hub, pas de store global cross-hub (recommandé)

Chaque hub tient son propre historique append-only de ses propres
événements : `shipment_events` dans fleet-hub (créations, changements de
statut, capture ETA), et, séparément et plus tard si le besoin apparaît, une
table équivalente dans compliance-engine pour ses propres événements
(suggestions d'orchestration exécutées, règles déclenchées). **Aucun des deux
stores ne cherche à être la source de vérité de l'autre hub.** Le pont de
webhooks existant reste **strictement inchangé** — il continue de notifier,
pas d'alimenter un store partagé.

- ✅ Risque le plus bas : chaque hub n'écrit que dans sa propre base, pas de
  nouveau contrat réseau, le pont de webhooks ne bouge pas d'un octet.
  Satisfait directement le critère « aucun endpoint existant modifié ».
  ✅ Couvre le besoin concret identifié (dashboard fleet-hub) immédiatement.
- ✅ N'empêche pas une vue consolidée plus tard : si un vrai besoin cross-hub
  apparaît, on saura déjà lequel des deux stores locaux a les bonnes données,
  et on pourra décider *alors*, avec des faits, s'il faut les relier.
- ❌ Pas de vue cross-hub immédiate — accepté : aucun besoin exprimé
  aujourd'hui ne la demande.

## Décision

**Option C.** On commence par un event store local, append-only, **dans
fleet-hub seulement**, pour couvrir le seul consommateur réel identifié
(`DashboardService` / `InternalDashboardSummaryService`). On ne touche à
rien côté compliance-engine dans ce chantier — ni `event_outbox`, ni le pont
de webhooks, ni une nouvelle table. Si un besoin de projection apparaît côté
compliance-engine, il fera l'objet d'un ADR séparé, au moment où un vrai
cas d'usage existera.

On rejette explicitement, pour ce chantier :
- toute techno de séries temporelles (TimescaleDB) ou tout partitionnement
  Postgres — aucune preuve de volume qui le justifie ;
- toute modification du pont de webhooks existant ;
- toute tentative de store global cross-hub tant qu'aucun besoin métier
  concret n'a été exprimé.

## Schéma envisagé (fleet-hub)

```sql
CREATE TABLE shipment_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    shipment_id     UUID NOT NULL REFERENCES shipment_orders(id),
    event_type      VARCHAR(50) NOT NULL,   -- CREATED, STATUS_CHANGED, ETA_CAPTURED, ...
    payload         JSONB NOT NULL,          -- détail spécifique au type (ex. : ancien/nouveau statut)
    occurred_at     TIMESTAMP NOT NULL,      -- quand l'événement s'est produit
    recorded_at     TIMESTAMP NOT NULL DEFAULT now(), -- quand on l'a écrit (append-only, jamais updatable)
    CONSTRAINT shipment_events_no_update CHECK (true) -- append-only appliqué au niveau applicatif, pas de UPDATE exposé
);

CREATE INDEX idx_shipment_events_shipment ON shipment_events (shipment_id, occurred_at);
CREATE INDEX idx_shipment_events_company ON shipment_events (company_id, occurred_at);
```

Pas de nouvelle dépendance, pas de nouveau service réseau : une table
Postgres classique dans la base fleet-hub existante, écrite via le
repository Spring Data JPA habituel.

## Plan de déploiement (si validé)

1. **Phase 1 — double écriture** : chaque point qui modifie déjà l'état d'une
   expédition (`ShipmentService`, `CarrierBookingService`,
   `AisEtaCaptureService`) écrit aussi une ligne dans `shipment_events`, en
   plus de son comportement actuel inchangé. Aucune lecture ne change encore.
2. **Phase 2 — parité** : `DashboardService` / `InternalDashboardSummaryService`
   construisent leurs projections à partir de `shipment_events` en parallèle
   de l'ancienne lecture directe, et on compare les deux résultats (log de
   divergence, pas d'exposition publique) pendant une période d'observation.
3. **Phase 3 — bascule** : une fois la parité confirmée, les endpoints de
   dashboard basculent sur la projection issue de `shipment_events`. Les
   lectures directes sur les tables métier pour ces endpoints spécifiques
   sont retirées. Aucun contrat d'API externe ne change (même format de
   réponse).

## Conséquences

- **Positif** : historique réel et consultable des événements d'expédition,
  sans aucun risque pour le pont cross-hub existant, sans dépendance
  nouvelle, sans spéculation sur un besoin cross-hub non exprimé.
- **Négatif / dette acceptée** : si un besoin cross-hub apparaît plus tard
  (ex. : compliance-engine veut l'historique transport), il faudra soit
  étendre le pont de webhooks existant pour y inclure plus de détail, soit
  construire un accès en lecture dédié — ce travail n'est pas fait ici et
  nécessitera son propre ADR.
- **Risques** :
  - Volume de `shipment_events` qui croît indéfiniment sans politique de
    rétention définie → à surveiller après la Phase 1, pas bloquant pour
    démarrer.
  - Double écriture en Phase 1 qui échoue partiellement (événement business
    appliqué, écriture event store ratée, ou l'inverse) → traiter dans la
    même transaction applicative que l'écriture métier plutôt que comme un
    appel séparé après coup, pour éviter une divergence silencieuse.

## Questions ouvertes pour validation

1. Confirmer le périmètre : uniquement fleet-hub pour ce chantier, rien côté
   compliance-engine — d'accord ?
2. Le schéma `shipment_events` ci-dessus couvre-t-il les types d'événements
   attendus, ou faut-il en ajouter/retirer avant implémentation (ex. :
   faut-il un événement dédié pour l'assignation d'un MMSI de navire suivi) ?
3. Politique de rétention : aucune pour l'instant (accepté comme dette), ou
   faut-il la définir dès cette phase ?
4. Est-ce qu'une vue cross-hub est un besoin réel à moyen terme, ou purement
   hypothétique pour l'instant ? (Détermine si Option C doit rester la
   décision finale ou si c'est une étape vers autre chose à anticiper
   davantage dans le schéma.)
