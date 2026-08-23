-- V52__groupage.sql
-- Groupage / consolidation multi-exportateurs (co-loading)

CREATE TABLE IF NOT EXISTS groupages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    reference VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    transport_mode VARCHAR(10),
    carrier_name VARCHAR(120),
    origin VARCHAR(100),
    destination VARCHAR(100),
    capacity_weight_kg DECIMAL(12,2),
    capacity_volume_m3 DECIMAL(12,2),
    booked_weight_kg DECIMAL(12,2) NOT NULL DEFAULT 0,
    booked_volume_m3 DECIMAL(12,2) NOT NULL DEFAULT 0,
    planned_departure DATE,
    planned_arrival DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groupages_company ON groupages(company_id);
CREATE INDEX idx_groupages_status ON groupages(status);

CREATE TABLE IF NOT EXISTS groupage_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    groupage_id UUID NOT NULL REFERENCES groupages(id) ON DELETE CASCADE,
    shipment_order_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    external_company VARCHAR(150),
    reference VARCHAR(60),
    weight_kg DECIMAL(12,2) NOT NULL DEFAULT 0,
    volume_m3 DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groupage_members_groupage ON groupage_members(groupage_id);
CREATE INDEX idx_groupage_members_shipment ON groupage_members(shipment_order_id);
