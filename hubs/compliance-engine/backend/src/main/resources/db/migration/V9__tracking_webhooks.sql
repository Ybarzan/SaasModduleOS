-- V9 — Tracking & Webhooks

CREATE TABLE shipment_trackings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipment_orders(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    carrier VARCHAR(50),
    tracking_number VARCHAR(200),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_detail VARCHAR(500),
    estimated_delivery TIMESTAMP,
    actual_delivery TIMESTAMP,
    last_location VARCHAR(255),
    last_latitude DOUBLE PRECISION,
    last_longitude DOUBLE PRECISION,
    raw_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shipment_trackings_shipment ON shipment_trackings(shipment_id);
CREATE INDEX idx_shipment_trackings_company ON shipment_trackings(company_id);
CREATE INDEX idx_shipment_trackings_status ON shipment_trackings(status);
CREATE INDEX idx_shipment_trackings_tracking_number ON shipment_trackings(tracking_number);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_company ON webhook_events(company_id);
CREATE INDEX idx_webhook_events_provider ON webhook_events(provider);
CREATE INDEX idx_webhook_events_processed ON webhook_events(processed);
