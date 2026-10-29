-- ADR-0001 (docs/adr/0001-event-store-canonique.md) -- Option C, scope
-- fleet-hub seul : journal d'événements append-only par expédition.
-- Phase 1 (double écriture) : alimenté en plus du comportement existant,
-- aucun endpoint ni table existante modifiée. Ne remplace pas tracking_events
-- (jalons de suivi statut/position) : couvre les événements business qui
-- n'y ont pas leur place (création, assignation navire, capture d'ETA), et
-- journalise aussi les changements de statut pour offrir une timeline
-- unifiée exploitable par une future projection (Phase 2/3 de l'ADR).

CREATE TABLE shipment_events (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    shipment_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN
        ('SHIPMENT_CREATED', 'STATUS_CHANGED', 'VESSEL_ASSIGNED', 'ETA_CAPTURED')),
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_se_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_se_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE CASCADE
);
CREATE INDEX idx_se_shipment ON shipment_events (shipment_id, occurred_at);
CREATE INDEX idx_se_company ON shipment_events (company_id, occurred_at);
