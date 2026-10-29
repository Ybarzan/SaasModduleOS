# ADR-0002 : Création de `ShipmentOrder` sur fleet-hub depuis une source externe (e-commerce, email)

- **Statut** : proposé — en attente de validation utilisateur avant implémentation.
- **Date** : 2026-10-29
- **Contexte du plan** : `docs/03-plan-migration.md`, Phase 1 J4 (complément non fait) ; découvert par audit de code (chantiers #27-#34 de la session en cours), pas une ligne pré-existante du plan.

## Contexte

Quatre points d'appel, dans deux services différents de compliance-engine, ont perdu leur capacité à **créer** un `ShipmentOrder` lors du retrait du domaine Transport (commit `dc1e04d`, 2026-10-12). Contrairement aux huit chantiers déjà livrés cette session (#27-#34), qui ne faisaient que **lire** fleet-hub via `FleetHubIntegrationClient` (dégradation gracieuse triviale, aucun risque de donnée persistée fausse), restaurer ces quatre points demande d'**écrire** une nouvelle expédition sur la base de fleet-hub depuis un autre hub — fleet-hub n'expose aujourd'hui aucun endpoint `/api/internal/**` en écriture, seulement des lectures (`GET .../shipments/{id}`, `.../shipments/{id}/items`, `.../dashboard-summary`, voir `InternalShipmentController`/`InternalDashboardController`).

**Les quatre points d'appel, tous actuellement des no-op journalisés plutôt que des échecs silencieux :**

1. `ECommerceSyncService.syncSingleIntegration()` (`hubs/compliance-engine/backend/.../service/ECommerceSyncService.java:81-91`) — la synchronisation périodique (toutes les 5 min, `incokalk.ecommerce.sync-interval`) appelle toujours `adapter.syncOrders(integration)` en amont (pleinement fonctionnel, vérifié dans `ShopifyAdapter.java:51` — pagine réellement l'API Shopify), mais la boucle qui transformait chaque commande en `ShipmentOrder` a été retirée ; la méthode se contente de journaliser et de marquer `lastSyncAt`, sans jamais traiter une commande.
2. `ECommerceAdapter.mapOrderToShipment(...)` — supprimée de l'interface et des 3 implémentations (`ShopifyAdapter.java:116`, `WooCommerceAdapter.java:86`, `PrestaShopAdapter.java:96`), toutes avec le même commentaire pointant vers ce gap.
3. `EmailIntakeService.autoCreateDraftShipment()` (`hubs/compliance-engine/backend/.../service/EmailIntakeService.java:248-253`) — appelée à deux endroits vivants (`processMessage()` dès qu'une société est identifiée avec origine+destination parsées, et `confirmIntake()` après validation manuelle d'un intake ambigu) ; le corps actuel se limite à un `log.info("... temporairement indisponible ...")`.
4. Le champ `EmailIntake.createdShipmentId` et la valeur d'enum `IntakeStatus.SHIPMENT_CREATED` existent déjà dans le modèle (`model/EmailIntake.java:56-57`, `63-65`) et sont même comptés par `getStats()` — mais rien ne les renseigne jamais aujourd'hui. L'infrastructure d'accueil de la future fonctionnalité a été écrite, pas la fonctionnalité.

**Deux candidats trouvés par l'audit touchent une entité différente (`Carrier`, pas `ShipmentOrder`) et sont volontairement laissés hors de cet ADR** — voir note en fin de document.

## Facteurs de décision

- **Écrire n'est pas lire** : une lecture ratée dégrade vers `0`/`"Unknown"`, invisible pour l'utilisateur final. Une création ratée — ou pire, une création *réussie mais fausse* (mauvaise adresse, doublon) — produit un enregistrement persistant et visible dans les files de travail des répartiteurs fleet-hub. Le risque n'est pas symétrique à celui des huit chantiers précédents ; le niveau de prudence doit être plus élevé.
- **Aucune des 4 sources externes ne connaît les identifiants internes de fleet-hub.** `ShipmentService.createShipment(ShipmentOrderDTO, ...)` (`hubs/fleet-hub/backend/.../service/ShipmentService.java:55`), utilisé par l'UI de création fleet-hub, accepte `carrierId`/`truckId`/`shippingRateId`/`clientId` — tous des clés étrangères internes à fleet-hub qu'un webhook Shopify ou un email parsé n'a aucun moyen de connaître ni de deviner de façon fiable.
- **`ShipmentOrder` n'a qu'une seule colonne obligatoire métier** (`orderNumber`, généré côté serveur) — tout le reste (`shipper*`/`consignee*`/`goods*`/`weightKg`/etc.) est `nullable` (`model/ShipmentOrder.java:54-224`). Le schéma n'empêche techniquement rien ; ce n'est pas une garantie de qualité des données, seulement une absence de contrainte bloquante.
- **Un statut `DRAFT` existe déjà** dans `ShipmentOrder.Status` (`DRAFT, QUOTED, BOOKED, IN_TRANSIT, DELIVERED, CANCELLED`) et n'est compté ni par `AutoTrackingSyncService` (ne boucle que sur `BOOKED`/`IN_TRANSIT`, chantier #32) ni par `getDashboardSummary().activeShipments` (même filtre, chantiers #30/#34, vérifié dans ce fil). Une expédition créée en `DRAFT` n'apparaît donc dans aucun agrégat "actif" déjà construit — elle reste invisible jusqu'à ce qu'un humain la fasse progresser.
- **L'idempotence est en partie déjà construite.** `ecommerce_shipment_links` (migration V81, chantier #28) porte déjà une contrainte `UNIQUE(integration_id, external_order_id)` — exactement la clé qu'il faut vérifier avant de créer une expédition pour ne jamais en dupliquer une pour la même commande. Côté email, `EmailIntake.createdShipmentId` joue le même rôle (si déjà renseigné, ne jamais recréer).
- **Précédent déjà posé cette session pour une décision structurellement proche** (contenu généré/dérivé sans confirmation humaine immédiate) : `NlRuleTranslationService` crée toujours sa règle avec `isActive=false`, quel que soit ce que le modèle propose (chantier Phase 3 J9-J10). Le même principe — produire quelque chose d'inerte par défaut, qu'un humain doit faire progresser consciemment — s'applique ici via le statut `DRAFT` déjà existant, pas une nouveauté à inventer.
- **Volume** : `ShopifyAdapter.syncOrders` pagine jusqu'à 50 pages de 250 commandes filtrées par `updated_at_min` — en pratique borné par le volume réel de commandes depuis le dernier sync (5 min), mais pas nul. Un appel HTTP synchrone par commande vers fleet-hub à chaque cycle est un détail d'implémentation à surveiller, pas un facteur qui change la décision ci-dessous.

## Options considérées

### Option A — Exposer `ShipmentOrderDTO` tel quel (le DTO interne de l'UI fleet-hub) sur un nouvel endpoint M2M

Réutilise directement le DTO que l'UI de création fleet-hub envoie déjà à `POST /api/shipments` (authentifié JWT). Zéro nouveau code de mapping côté fleet-hub.

- **Contre** : expose `carrierId`/`truckId`/`shippingRateId`/`clientId` dans un contrat que 4 appelants externes ne rempliront jamais utilement (ils ne connaissent pas ces identifiants) — soit ils restent toujours `null` (le contrat ment sur ce qu'il attend), soit un appelant mal informé tente d'en deviner un (risque d'assigner la mauvaise société de transport). Couple aussi l'API M2M à la forme interne de l'UI fleet-hub : tout changement du DTO de création UI casserait silencieusement ce canal externe sans que personne ne le remarque immédiatement.

### Option B — DTO M2M dédié, minimal, restreint à ce qu'un appelant externe peut connaître de façon fiable (recommandée)

Un seul nouveau DTO, par exemple `ExternalShipmentCreationRequest` : `shipperName/Address/City/Country/PostalCode`, `consigneeName/Address/City/Country/PostalCode`, `goodsDescription`, `goodsValue`+`currency`, `weightKg`, `volumeM3` (tous optionnels sauf un minimum à définir, voir Questions ouvertes), plus un champ de traçabilité libre (`sourceLabel`, ex. `"Shopify commande #4821"` ou `"Email intake <id>"`, affiché dans les notes/UI fleet-hub pour qu'un répartiteur comprenne d'où vient le brouillon).

Toujours côté serveur fleet-hub, jamais fourni par l'appelant :
- `company` résolue depuis la clé `X-Internal-Integration-Key` (même principe que toutes les lectures existantes — jamais un identifiant transmis) ;
- `status` forcé à `DRAFT`, quoi que l'appelant envoie ;
- `orderNumber` généré normalement (`generateOrderNumber()`, déjà le comportement de `createShipment`) ;
- `carrier`/`truck`/`shippingRate`/`clientId` toujours `null` à la création — un humain les complète ensuite depuis l'UI fleet-hub normale, qui sait déjà gérer une expédition `DRAFT` incomplète.

Pas de normalisation de code pays à l'écriture (contrairement à `LandedCostService.createFromShipment`, chantier #31) : `ShipmentOrder.shipperCountry`/`consigneeCountry` sont déjà des colonnes libres côté fleet-hub aujourd'hui (`length = 3`, pas de contrainte de format) — stocker tel quel n'introduit donc aucune régression de qualité par rapport à ce que fleet-hub accepte déjà pour une création manuelle.

- **Pour** : contrat qui dit exactement ce qu'il attend, aucun champ qui invite à deviner une FK interne, découplé de l'UI interne fleet-hub.
- **Contre** : un nouveau DTO à maintenir en plus de `ShipmentOrderDTO` — duplication limitée (un sous-ensemble de champs déjà tous présents sur `ShipmentOrder`).

### Option C — Un endpoint dédié par source (`.../from-ecommerce-order`, `.../from-email-intake`)

Même idée qu'Option B mais avec un contrat distinct par appelant plutôt qu'un DTO générique + champ `sourceLabel`.

- **Contre** : duplication pour un besoin quasi identique (les deux sources produisent la même forme de données : adresse shipper/consignee approximative, poids/volume optionnels, aucune FK fleet-hub connue). Pas de bénéfice identifié qu'un simple champ de traçabilité ne couvre pas déjà dans l'Option B.

## Décision proposée

**Option B**, si validée : un seul nouveau DTO M2M minimal, un seul nouvel endpoint `POST /api/internal/shipments` sur `InternalShipmentController` (même authentification `X-Internal-Integration-Key` que les lectures existantes, même contrôleur), création forcée en `DRAFT`, jamais de FK interne acceptée depuis l'appelant.

**Ce que cet ADR ne tranche pas tout seul** — voir Questions ouvertes ci-dessous avant toute implémentation :
- si la capacité d'écriture doit être gardée par un opt-in **séparé** de celui qui gouverne déjà la lecture (`internalIntegrationEnabled`) ;
- le champ minimal obligatoire en-deçà duquel la création est refusée plutôt que silencieusement acceptée avec un brouillon quasi vide.

## Schéma envisagé (contrat M2M, pas un changement de table)

Aucune nouvelle migration côté fleet-hub : tous les champs du DTO proposé existent déjà sur `ShipmentOrder`. Le seul changement de schéma serait côté compliance-engine, pour fermer la boucle d'idempotence :

```sql
-- EmailIntake porte déjà created_shipment_id (model/EmailIntake.java:56) : rien à ajouter.
-- ecommerce_shipment_links (V81) porte déjà la contrainte d'idempotence nécessaire : rien à ajouter.
```

Autrement dit : **la création automatique de brouillon, si validée, ne touche quasiment qu'à de la logique de service** — brancher `ECommerceSyncService`/`EmailIntakeService` sur le nouvel endpoint fleet-hub, en réutilisant les tables d'idempotence déjà en place. C'est le principal argument en faveur du périmètre proposé : pas de nouvelle décision de modèle de données, seulement un nouveau contrat réseau étroit.

```java
// fleet-hub, nouveau DTO (illustratif, pas final)
public record ExternalShipmentCreationRequest(
    String shipperName, String shipperAddress, String shipperCity,
    String shipperCountry, String shipperPostalCode,
    String consigneeName, String consigneeAddress, String consigneeCity,
    String consigneeCountry, String consigneePostalCode,
    String goodsDescription, Double goodsValue, String currency,
    Double weightKg, Double volumeM3, Integer packagesCount,
    String sourceLabel
) {}
```

## Plan de déploiement (si validé)

1. fleet-hub : nouveau DTO + `POST /api/internal/shipments` sur `InternalShipmentController`, nouvelle méthode `ShipmentService.createFromExternalSource(...)` (force `DRAFT`, ignore tout champ hors de la liste ci-dessus).
2. compliance-engine, e-commerce : dans `ECommerceSyncService.syncSingleIntegration()`, pour chaque commande renvoyée par `syncOrders()`, vérifier l'absence de lien existant (`EcommerceShipmentLinkRepository` par `integrationId`+`externalOrderId`) avant d'appeler le nouvel endpoint ; créer le lien immédiatement après succès (réutilise `EcommerceShipmentLinkService`, chantier #28).
3. compliance-engine, email : dans `EmailIntakeService.autoCreateDraftShipment()`, appeler le nouvel endpoint, stocker l'UUID retourné dans `createdShipmentId`, passer `status` à `SHIPMENT_CREATED`.
4. Tests : au minimum, non-duplication (même commande synchronisée deux fois = un seul appel de création), dégradation si fleet-hub indisponible (échec journalisé, jamais de retry agressif qui dupliquerait), et qu'aucune FK interne fleet-hub n'est jamais acceptée depuis la requête entrante.

## Conséquences

- **Positif** : ferme une boucle visible depuis plusieurs chantiers (deux champs de modèle déjà prêts et jamais utilisés), sans nouvelle décision de modèle de données, en réutilisant deux mécanismes d'idempotence déjà construits.
- **Négatif / dette acceptée** : les brouillons `DRAFT` créés automatiquement s'accumulent dans la liste de travail fleet-hub sans notification proactive vers un répartiteur — un humain doit aller les chercher. Pas traité par cet ADR (pourrait être une amélioration UI ultérieure, hors scope).
- **Risques** :
  - Qualité de la donnée pour la source email : `EmailIntake.origin`/`destination` sont du texte libre extrait par regex depuis le corps d'un email (`service/EmailIntakeService.java`), nettement moins fiable que l'adresse structurée d'une commande Shopify/WooCommerce/PrestaShop — un brouillon créé depuis un email mal parsé peut être plus bruyant qu'utile. À surveiller après activation, pas bloquant pour démarrer si le volume reste faible.
  - Si l'opt-in d'écriture n'est pas séparé de celui de lecture (voir Question 1), une société qui a activé le partage en lecture pour un autre usage (ex. `MultiBranchService`) se retrouverait, sans l'avoir demandé explicitement, à faire créer des expéditions automatiquement.

## Questions ouvertes pour validation

1. **L'écriture doit-elle être gardée par un opt-in séparé de la lecture ?** Aujourd'hui, `internalIntegrationEnabled`/la présence d'une clé gouverne déjà tout `/api/internal/**` en lecture (chantiers #27-#34). Ajouter une création automatique de données persistantes sous la même bascule changerait silencieusement le comportement pour toute société qui l'a déjà activée pour un usage en lecture — même logique que la décision prise pour `AutoTrackingSyncService` (chantier #32, opt-in séparé car changement de comportement, pas juste nouvelle lecture). Recommandation personnelle : oui, un second flag explicite (ex. `allowExternalShipmentCreation`), mais c'est la décision du propriétaire du produit, pas la mienne seule.
2. **Champ minimum obligatoire avant refus ?** Proposition : refuser (400) si ni `consigneeName`/`consigneeAddress` ni `consigneeCity`+`consigneeCountry` ne sont renseignés — un brouillon sans destination identifiable n'a aucune valeur opérationnelle. À confirmer ou amender.
3. **Portée immédiate : les deux sources (e-commerce + email) en même temps, ou une seule d'abord ?** Les deux partagent le même endpoint proposé ; livrer les deux ensemble ne change pas la taille du chantier côté fleet-hub, seulement le nombre de services compliance-engine à brancher derrière.
4. **Faut-il un plafond par cycle de synchronisation** (ex. : au plus N créations par appel de `scheduledSyncAll`) pour éviter qu'un premier sync après une longue coupure ne crée des centaines de brouillons d'un coup ? Détail d'implémentation, pas un facteur de décision, mais à trancher avant de coder.

## Note : second candidat écarté de cet ADR — création en masse de `Carrier`

`ImportService.importCarriersCsv` (`hubs/compliance-engine/backend/.../service/ImportService.java:17`) a été entièrement retirée (pas même un stub) avec le domaine Transport, et **aucun contrôleur ne l'appelle plus** (`controller/compliance/ImportController.java:22`, commentaire seul, aucune route). Contrairement aux quatre points ci-dessus, ce chemin est **du code mort sans appelant vivant aujourd'hui** — restaurer un endpoint d'écriture `Carrier` en masse (une entité différente, avec ses propres questions de déduplication par SIRET/nom/pays) n'a pas la même urgence et mérite sa propre décision le jour où un besoin réel se présente, plutôt que d'être traité comme un sous-point de celui-ci.
