-- V6__carbon_offsets.sql
-- shipment_id : UUID brut, sans FK -- l'expédition (ShipmentOrder) vit côté
-- fleet-hub depuis l'extraction Transport.

CREATE TABLE IF NOT EXISTS carbon_offsets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID,
    co2_emissions_kg DECIMAL(12,2) NOT NULL,
    offset_credits_purchased DECIMAL(12,2) DEFAULT 0,
    offset_credits_retired DECIMAL(12,2) DEFAULT 0,
    offset_provider VARCHAR(100),
    offset_project_name VARCHAR(200),
    offset_project_type VARCHAR(50),
    offset_cost_per_ton DECIMAL(10,2),
    offset_total_cost DECIMAL(15,2),
    offset_currency VARCHAR(3) DEFAULT 'EUR',
    certification_id VARCHAR(100),
    retired_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'TRACKING',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_carbon_offset_company ON carbon_offsets(company_id);
CREATE INDEX idx_carbon_offset_shipment ON carbon_offsets(shipment_id);
CREATE INDEX idx_carbon_offset_status ON carbon_offsets(status);
