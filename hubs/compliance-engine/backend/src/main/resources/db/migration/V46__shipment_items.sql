-- V46__shipment_items.sql
-- Articles liés à une expédition (ShipmentItem)

CREATE TABLE IF NOT EXISTS shipment_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    shipment_id UUID NOT NULL REFERENCES shipment_orders(id) ON DELETE CASCADE,
    item_id UUID,
    sku VARCHAR(100),
    name VARCHAR(300),
    description TEXT,
    hs_code VARCHAR(20),
    quantity NUMERIC(15, 2) NOT NULL DEFAULT 1,
    unit VARCHAR(10) NOT NULL DEFAULT 'PCS',
    unit_price NUMERIC(15, 2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_shipment_items_company ON shipment_items(company_id);
CREATE INDEX IF NOT EXISTS idx_shipment_items_shipment ON shipment_items(shipment_id);
CREATE INDEX IF NOT EXISTS idx_shipment_items_item ON shipment_items(item_id);