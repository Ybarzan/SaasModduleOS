# ADR-0003 : Sécuriser l'API interne de fleet-hub (filtre unique, contrat explicite, une clé par hub)

- **Statut** : accepté — implémenté le 2026-11-04 (audit fleet-hub, point 2)
- **Date** : 2026-11-04
- **Périmètre** : `hubs/fleet-hub`, routes `/api/internal/**` appelées par compliance-engine, Logistix, Finance et le portail Praxio

## Contexte

fleet-hub est devenu le noyau de données de la plateforme : quatre autres hubs lisent ses
expéditions ou ses résumés via `/api/internal/**`, authentifiés par l'en-tête
`X-Internal-Integration-Key`. L'audit du 2026-11-04 a relevé quatre défauts sur cette surface :

1. **Authentification au cas par cas.** Les routes étaient `permitAll` dans `SecurityConfig` ;
   chaque méthode de contrôleur devait appeler elle-même `resolveCompanyId(apiKey)`. Une route
   ajoutée sans cet appel aurait été publique.
2. **Gel d'abonnement contourné.** `SubscriptionGuardFilter` ne s'appliquait qu'aux requêtes
   portant un JWT : une société suspendue ou en fin d'essai continuait d'alimenter les autres
   hubs et de recevoir des expéditions créées par compliance-engine.
3. **Entités JPA exposées.** Les réponses étaient les entités `ShipmentOrder` / `ShipmentItem`.
   Vérifié par test : dès qu'un camion était affecté à l'expédition, la sérialisation du champ
   paresseux `truck` échouait (`LazyInitializationException`, 500) — les autres hubs ne pouvaient
   pas lire les expéditions livrées par la flotte propre. Côté API du personnel, le même champ
   embarquait l'entité `Company` complète (hash de clé, code d'accès du portail, identifiants
   de facturation). Et tout renommage d'un champ d'entité cassait un autre hub sans erreur de
   compilation.
4. **Une seule clé, tous les droits.** La même clé servait aux quatre hubs, avec accès à tout
   (lecture, création, facturation), sans rotation possible autrement qu'en coupant le partage.

## Décision

### Filtre unique

`InternalApiKeyFilter` authentifie toute la famille `/api/internal/**` : clé valide d'une
société dont le partage est activé, sinon 401. Il pose la société dans `TenantContext` et
accorde le rôle `INTEGRATION`, que `SecurityConfig` exige désormais sur ces routes. Il est placé
avant `SubscriptionGuardFilter` : **un abonnement gelé gèle aussi ce que voient les autres hubs**
(402). Un JWT du personnel ne suffit jamais sur ces routes.

### Contrat explicite

Les réponses sont des DTO dédiés (`dto/internal/InternalShipmentDto`,
`InternalShipmentItemDto`, `InternalTrackingEventDto`), construits dans une transaction.
`InternalApiContractTest` recopie la liste des champs lus par chaque consommateur
(compliance-engine : `ShipmentDetailDto`, `ShipmentSummary`, `RecentShipmentDto`,
`ShipmentItemDetailDto` ; Logistix : `ShipmentSearchResult`, `ShipmentItemSummary` ;
Finance : `ShipmentLaneSummary`) et échoue si l'un d'eux disparaît. Ajouter un champ reste
libre (les consommateurs ignorent les champs inconnus). En complément, `ShipmentOrder.truck` et
les trois secrets de `Company` sont exclus de toute sérialisation JSON.

### Une clé par hub, droits minimaux

Table `integration_api_key` (V24) : une clé par (société, hub), hash SHA-256 seul, 4 derniers
caractères pour la reconnaître, date de dernière utilisation.

| Hub (`IntegrationConsumer`) | Droits |
|---|---|
| `COMPLIANCE_ENGINE` | lire les expéditions, créer des brouillons (en plus de l'opt-in société `allowExternalShipmentCreation`, ADR-0002), résumé d'activité |
| `LOGISTIX` | lire les expéditions |
| `FINANCE` | lire les expéditions |
| `PORTAL` | résumés d'activité et de facturation |
| `LEGACY` | tous — la clé partagée d'avant V24, reprise telle quelle par la migration |

Générer la clé d'un hub qui en a déjà une la remplace (rotation, l'ancienne est invalide
immédiatement). Une clé peut être révoquée seule sans toucher aux autres. L'interrupteur
général `internalIntegrationEnabled` reste : le désactiver rejette toutes les clés sans les
effacer.

## Conséquences

- **Aucune coupure au déploiement** : la clé partagée existante devient `LEGACY` et garde tous
  ses droits. L'écran « Intégration des hubs » invite à générer une clé par hub, la coller dans
  chaque hub, puis révoquer la clé historique. Aucun changement de code n'est nécessaire dans
  les autres hubs : ils envoient toujours `X-Internal-Integration-Key`, avec leur propre clé.
- **Rotation sans recouvrement** : entre la génération d'une nouvelle clé et son collage dans le
  hub concerné, ce hub reçoit des 401. Acceptable pour des appels non temps réel ; à revoir
  (période de chevauchement) si un hub devient critique en continu.
- `POST /api/internal-integrations/enable` ne génère plus de clé : les clés se gèrent par
  `POST|DELETE /api/internal-integrations/keys/{hub}`.
- Hors périmètre, à traiter séparément : `MarketplaceService` (FleetMarket, en-tête
  `X-Marketplace-Key`) garde l'ancien schéma `permitAll` + contrôle dans le service.
