-- Phase 2 J1-J2 du plan de migration (docs/03-plan-migration.md) : capture
-- systématique de l'ETA annoncée par une source externe pour une expédition
-- réelle, au moment où elle est émise -- jamais recalculée après coup --
-- pour pouvoir comparer plus tard contre shipment_orders.actual_delivery_date
-- et mesurer la fiabilité réelle de chaque source (voir V15, EtaSourceReliabilityService).
--
-- Une seule source alimente cette table pour l'instant : CARRIER_BOOKING (l'ETA
-- que le transporteur annonce au moment de la réservation, capturée par
-- CarrierBookingService.submitBooking). L'ETA dérivé AIS prévu par le plan n'est
-- pas implémenté : aucune expédition n'est aujourd'hui liée à un MMSI de navire
-- suivi (voir AisStreamService, qui n'alimente qu'une carte de suivi, pas de
-- lien expédition <-> navire) -- chantier séparé.

CREATE TABLE eta_reported_sources (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    shipment_id UUID NOT NULL,
    carrier_id UUID,
    source VARCHAR(20) NOT NULL CHECK (source IN ('CARRIER_BOOKING')),
    reported_days INTEGER,
    reported_arrival TIMESTAMP(6),
    origin_country VARCHAR(3),
    destination_country VARCHAR(3),
    transport_mode VARCHAR(20),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ers_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_ers_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE CASCADE,
    CONSTRAINT fk_ers_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE CASCADE
);
CREATE INDEX idx_ers_company ON eta_reported_sources (company_id);
CREATE INDEX idx_ers_shipment ON eta_reported_sources (shipment_id);
CREATE INDEX idx_ers_reliability_group ON eta_reported_sources (carrier_id, origin_country, destination_country, transport_mode);
