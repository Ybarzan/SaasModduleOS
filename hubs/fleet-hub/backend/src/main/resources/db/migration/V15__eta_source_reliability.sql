-- Phase 2 J3-J4 du plan de migration : fiabilité par transporteur x lane x mode,
-- recalculée depuis l'écart réel historique (eta_reported_sources vs
-- shipment_orders.actual_delivery_date) -- voir EtaSourceReliabilityService.
-- Une ligne par groupe (upsert applicatif à chaque recalcul), jamais un
-- historique de chaque run : seule la mesure la plus à jour importe pour
-- pondérer une prédiction courante.

CREATE TABLE eta_source_reliability (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    carrier_id UUID,
    origin_country VARCHAR(3) NOT NULL,
    destination_country VARCHAR(3) NOT NULL,
    transport_mode VARCHAR(20) NOT NULL,
    sample_count INTEGER NOT NULL DEFAULT 0,
    mean_deviation_days DECIMAL(8,2),
    mean_absolute_deviation_days DECIMAL(8,2),
    computed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_esr_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_esr_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE CASCADE
);
CREATE INDEX idx_esr_company ON eta_source_reliability (company_id);
CREATE UNIQUE INDEX uk_esr_group ON eta_source_reliability (
    company_id, COALESCE(carrier_id, '00000000-0000-0000-0000-000000000000'),
    origin_country, destination_country, transport_mode
);
