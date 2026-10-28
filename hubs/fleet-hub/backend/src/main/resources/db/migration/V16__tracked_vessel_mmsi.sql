-- Phase 2 (complément AIS) du plan de migration : lie une expédition à un
-- navire suivi par MMSI, pour capter l'ETA que ce navire diffuse lui-même via
-- AIS (voir AisStreamService, EtaReportedSource.Source.AIS_BROADCAST). Saisie
-- manuelle par un opérateur -- rien dans ce système ne permet de déduire
-- automatiquement quel navire transporte quelle expédition.

ALTER TABLE shipment_orders ADD COLUMN tracked_vessel_mmsi VARCHAR(20);
CREATE INDEX idx_shipments_vessel_mmsi ON shipment_orders (tracked_vessel_mmsi) WHERE tracked_vessel_mmsi IS NOT NULL;
