-- ADR-0002 (docs/adr/0002-creation-expedition-externe.md) : opt-in SEPARE de
-- internal_integration_enabled (qui ne gouverne que la lecture, V-- voir
-- Company.internalIntegrationEnabled). Une societe qui a deja active le
-- partage en lecture (ex. pour MultiBranchService) ne doit pas se retrouver,
-- sans l'avoir demande explicitement, a faire creer des expeditions
-- automatiquement -- meme principe que fleethub.tracking.auto-sync.enabled
-- (chantier AutoTrackingSyncService).
ALTER TABLE company ADD COLUMN allow_external_shipment_creation BOOLEAN NOT NULL DEFAULT FALSE;
