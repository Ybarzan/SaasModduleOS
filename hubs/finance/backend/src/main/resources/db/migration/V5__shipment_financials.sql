-- V5__shipment_financials.sql
-- shipment_id : UUID brut, sans FK -- l'expédition (ShipmentOrder) vit côté
-- fleet-hub depuis l'extraction Transport.

CREATE TABLE IF NOT EXISTS shipment_financials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID NOT NULL,
    client_name VARCHAR(200),
    origin VARCHAR(10),
    destination VARCHAR(10),
    mode VARCHAR(10),
    carrier_name VARCHAR(200),
    revenue DECIMAL(15,2) DEFAULT 0,
    revenue_currency VARCHAR(3) DEFAULT 'EUR',
    cost_freight DECIMAL(15,2) DEFAULT 0,
    cost_fuel DECIMAL(15,2) DEFAULT 0,
    cost_handling DECIMAL(15,2) DEFAULT 0,
    cost_customs DECIMAL(15,2) DEFAULT 0,
    cost_insurance DECIMAL(15,2) DEFAULT 0,
    cost_warehouse DECIMAL(15,2) DEFAULT 0,
    cost_last_mile DECIMAL(15,2) DEFAULT 0,
    cost_other DECIMAL(15,2) DEFAULT 0,
    total_cost DECIMAL(15,2) DEFAULT 0,
    gross_margin DECIMAL(15,2) DEFAULT 0,
    gross_margin_percent DECIMAL(8,4) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shipment_financials_company ON shipment_financials(company_id);
CREATE UNIQUE INDEX idx_shipment_financials_shipment ON shipment_financials(shipment_id);
CREATE INDEX idx_shipment_financials_lane ON shipment_financials(origin, destination);
