-- Phase 2 (complément AIS) du plan de migration : autorise la nouvelle valeur
-- AIS_BROADCAST (EtaReportedSource.Source) -- l'ETA que le navire suivi
-- diffuse lui-même via AIS, voir AisStreamService.getBroadcastEta() et
-- AisEtaCaptureService. Nom de contrainte par défaut PostgreSQL pour un CHECK
-- de colonne non nommé explicitement à sa création (V14) : {table}_{colonne}_check.

ALTER TABLE eta_reported_sources DROP CONSTRAINT IF EXISTS eta_reported_sources_source_check;
ALTER TABLE eta_reported_sources ADD CONSTRAINT eta_reported_sources_source_check
    CHECK (source IN ('CARRIER_BOOKING', 'AIS_BROADCAST'));
