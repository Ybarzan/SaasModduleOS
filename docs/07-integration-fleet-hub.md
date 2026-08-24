# Intégration de fleet-hub comme Hub de la plateforme

## Ce qu'est fleet-hub, factuellement

Audit direct du code le 23/08/2026 (dépôt `D:\Users\nexus\Desktop\fleet-hub`, remote `github.com/Ybarzan/fleet-hub`) :

- SaaS de gestion de flotte poids lourds, multi-tenant, orienté couple **Chauffeur × Camion** : dashboard KPI (coût/km, taux d'utilisation, conformité), carte GPS temps réel, suivi tachygraphe (règles UE 561/2006), alertes.
- Stack : Java 21 / Spring Boot 3.4 / Flyway (V1-V7) côté backend, React 18 + Vite + Leaflet côté frontend, Capacitor (Android/iOS) côté mobile.
- Produit **fonctionnel et testé**, pas un POC : 182 fichiers backend, 23 contrôleurs REST, 21 pages React, 67 tests annoncés (JWT, multi-tenant, RGPD, Stripe), CI GitHub Actions.
- **A sa propre auth, son propre multi-tenant, sa propre facturation Stripe** — conçu et vendu comme produit autonome, pas comme brique technique.
- Deux mécanismes d'intégration externe déjà présents : `POST /api/webhooks/ingest` (ingestion push authentifiée par clé API) et un ensemble d'endpoints REST JWT lisibles en sortant (drivers, trucks, KPIs, positions GPS).
- Point faible assumé par le projet lui-même : le module `backend/src/main/java/com/fleethub/integration/` (sources GPS/tachygraphe/carburant) est un **squelette non connecté à un fournisseur réel** — mêmes symptômes que les adaptateurs transporteurs d'IncoKalk avant intégration réelle.
- **408 fichiers copiés dans ce monorepo, dont une part importante était non committée dans le dépôt d'origine au moment de la copie** (quasi tous les contrôleurs/modèles/DTOs modifiés, config CI, Dockerfile) — ce travail en cours est repris tel quel, mais **il faut le committer dans le dépôt `fleet-hub` d'origine séparément** pour ne pas le perdre si quelqu'un retouche ce dossier sans passer par ce monorepo.

## Pourquoi c'est un bon fit stratégique

L'audit d'IncoKalk ([01-audit-existant.md](01-audit-existant.md)) identifie un angle mort : les connecteurs transporteurs couvrent le fret international (DHL, MSC, CMA CGM...) mais rien ne couvre la flotte propre d'un chargeur ni le dernier kilomètre. fleet-hub comble exactement ce vide, avec en prime une donnée immédiatement exploitable par la Couche 2 (position GPS temps réel → ETA dernier kilomètre) et la Couche 3 (replanification de tournée comme action orchestrée).

## Décision d'architecture : intégration par API, pas fusion de code

**Ne pas fusionner les deux backends dans un seul monolithe.** Trois raisons concrètes :

1. **Double système d'authentification/multi-tenant.** Fusionner créerait exactement le bug déjà documenté dans un audit antérieur d'IncoKalk : deux systèmes de rôles non réconciliés (`User.Role` vs `CompanyRole.Role`) causant des échecs d'autorisation silencieux. fleet-hub a son propre JWT/RBAC/Stripe — les dupliquer dans un seul schéma de base de données sans plan de réconciliation explicite est le chemin le plus court vers ce même bug, à plus grande échelle.
2. **Cycles de vie indépendants.** fleet-hub a son propre rythme de release, sa propre CI, ses propres tests E2E Playwright mobile/desktop. Un monolithe fusionné couple ces cycles inutilement.
3. **fleet-hub a déjà l'interface d'intégration qu'il faut.** `POST /api/webhooks/ingest` et les endpoints REST sortants sont exactement le point de contact dont la Couche 1 (ingestion/normalisation) a besoin — pas besoin de les reconstruire en interne.

### Ce que ça implique concrètement

- fleet-hub reste un **service déployé indépendamment**, avec sa propre base de données.
- Le connecteur `FleetHubAdapter` (nouveau, côté compliance-engine, même famille que `CarrierAdapter`/`ErpProvider`) consomme les endpoints REST de fleet-hub (drivers, trucks, positions GPS) et les transforme en `ShipmentEvent` (schéma canonique défini en [02-architecture-cible.md](02-architecture-cible.md)) pour les expéditions qui utilisent la flotte propre du client.
- Dans l'autre sens, l'exécuteur d'actions de la Couche 3 peut appeler l'API fleet-hub existante pour déclencher une replanification de tournée — pas besoin d'un nouveau protocole, l'API REST suffit.
- **SSO unifié entre les deux produits** (un seul login pour accéder à compliance-engine et fleet-hub) est une amélioration UX désirable mais **secondaire** — à traiter après que l'intégration data fonctionne, pas comme prérequis. Proposition technique : fleet-hub adopte le JWT émis par compliance-engine comme identité fédérée (OAuth2/OIDC), sans toucher à son schéma de rôles interne.

## État de l'implémentation

**Configuration + client REST fait, 2026-08-24** : `FleetHubConfig` (V67, identifiants d'un compte de service fleet-hub -- URL de base, username, password) + `FleetHubClient` (`POST /api/auth/login` puis `GET /api/map/vehicles` avec le token Bearer obtenu, mapping exact du record `MapVehicleDto` côté fleet-hub) + CRUD via `/v1/fleethub` (OWNER/ADMIN pour créer/modifier/supprimer, MANAGER peut aussi lister/tester la connexion). Pas de cache de token dans cette première version -- un login par appel, volontairement simple ; à optimiser seulement si la fréquence d'appel le justifie réellement. Le compte de service utilisé ne doit pas avoir la 2FA activée (`POST /api/auth/login` ne renvoie pas de token si `totpRequired=true`) -- `FleetHubClient` détecte ce cas et échoue avec un message explicite plutôt qu'une erreur opaque. Toujours par API, jamais d'accès direct à la base fleet-hub, conformément à la décision d'architecture ci-dessus.

**Adapter de tracking + lien expédition fait, 2026-08-24** : `FleetHubAdapter implements TrackingProvider` (type `FLEET_HUB`, même famille que `AirTrackingProvider`/`MaritimeTrackingProvider`/`RoadTrackingProvider`), `ShipmentOrder.fleetHubTruckRegistration` (V68, nullable — une expédition livrée par la flotte propre n'a pas forcément de `carrier` au sens transporteur tiers). `LiveTrackingService.detectMode()` route vers `FLEET_HUB` (et utilise l'immatriculation comme "tracking number") dès qu'une expédition a un camion fleet-hub assigné, **avant** la logique existante basée sur `carrier.transportModes` — un camion assigné prime toujours sur un transporteur défini par ailleurs. Changement additif et minimal : le comportement existant (SEA/AIR/ROAD par transporteur) est inchangé pour toute expédition sans camion fleet-hub assigné. fleet-hub n'expose qu'un instantané de position (pas d'historique d'événements via `GET /api/map/vehicles`), donc `getTrackingInfo` ne renvoie jamais plus d'un élément, contrairement aux autres providers.

**Interface de configuration faite, 2026-08-24** : `FleetHubSettings.tsx` (liste des configurations, création/modification/suppression, test de connexion, véhicules de la flotte avec position GPS affichés en carte dépliable) — même motif que la page ERP mais dans le registre Praxio v0.2 (coins carrés, `::`, tags bordés) puisque c'est une page neuve. Nouvel endpoint `GET /v1/fleethub/{id}/vehicles` ajouté au passage (ouvert jusqu'à USER, contrairement au reste réservé à MANAGER+ — nécessaire pour qu'un utilisateur créant une expédition puisse un jour choisir un camion dans un sélecteur alimenté par cette liste).

**Sélecteur de camion fait, 2026-08-24** : `Shipments.tsx` expose `fleetHubTruckRegistration` (nouveau champ sur `ShipmentOrderDTO`/`ShipmentOrder`, propagé dans `ShipmentService.createShipment`) via un `<select>` peuplé par un nouvel endpoint `GET /v1/fleethub/vehicles` (sans ID de config, agrège toutes les configurations actives de l'entreprise). Ce nouvel endpoint est **délibérément ouvert jusqu'à USER** — contrairement au reste de `/v1/fleethub` réservé MANAGER+ — parce que `Shipments.tsx` lui-même est accessible dès USER : sans cet endpoint dédié, un utilisateur simple aurait vu son appel de liste échouer en 403 en silence. Le sélecteur ne s'affiche que si au moins un véhicule est disponible (masqué proprement quand fleet-hub n'est pas configuré, pas de champ vide qui ne sert à rien).

**Reste à faire** (hors scope de cette passe, à traiter si un vrai besoin se confirme) : réplanification de tournée déclenchée par l'exécuteur d'actions (Couche 3, cas d'usage 3 — explicitement différé après validation du premier cas d'usage sur un pilote réel, cf. docs/03). **L'intégration fleet-hub est maintenant complète de bout en bout** : configuration, tracking GPS, assignation depuis l'UI de création d'expédition.

## Ce qu'il ne faut pas faire

- Ne pas connecter l'orchestrateur directement aux tables PostgreSQL de fleet-hub — toujours passer par son API, même en interne (le principe "cohabiter, pas remplacer" s'applique aussi entre les deux hubs du même produit, pas seulement vis-à-vis des systèmes tiers du client).
- Ne pas essayer de finir le module `integration/` de fleet-hub (connecteurs GPS/AS24/carburant réels) dans le cadre de cette refonte — c'est un chantier fleet-hub à part entière, indépendant du calendrier de Praxio.
- Ne pas migrer les utilisateurs fleet-hub existants vers le nouveau branding avant que l'intégration soit stable — même principe de transition en douceur que pour IncoKalk (cf. [06-positionnement-gtm.md](06-positionnement-gtm.md)).
