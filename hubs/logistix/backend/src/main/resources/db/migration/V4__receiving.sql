-- V4__receiving.sql
-- Reception marchandises : bons de reception, lignes, scans et ecarts.
--
-- shipment_id reste une colonne UUID nullable SANS cle etrangere : c'est une
-- reference logique vers une expedition qui vivra dans le futur hub
-- "Transport" (pas encore construit), au meme titre que dans
-- compliance-engine d'origine (table shipment_orders, hub different).

CREATE TABLE IF NOT EXISTS receiving_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    shipment_id UUID,
    order_number VARCHAR(50) NOT NULL,
    reference VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    received_by UUID,
    received_at TIMESTAMP,
    notes TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_receiving_orders_company ON receiving_orders(company_id);
CREATE INDEX IF NOT EXISTS idx_receiving_orders_warehouse ON receiving_orders(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_receiving_orders_shipment ON receiving_orders(shipment_id);

CREATE TABLE IF NOT EXISTS receiving_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    receiving_order_id UUID NOT NULL REFERENCES receiving_orders(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    quantity_expected NUMERIC(15, 2) NOT NULL DEFAULT 0,
    quantity_received NUMERIC(15, 2) NOT NULL DEFAULT 0,
    quantity_damaged NUMERIC(15, 2) NOT NULL DEFAULT 0,
    unit VARCHAR(10) NOT NULL DEFAULT 'PCS',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_receiving_lines_order ON receiving_order_lines(receiving_order_id);
CREATE INDEX IF NOT EXISTS idx_receiving_lines_item ON receiving_order_lines(item_id);

CREATE TABLE IF NOT EXISTS receiving_scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    receiving_order_id UUID NOT NULL REFERENCES receiving_orders(id) ON DELETE CASCADE,
    line_id UUID REFERENCES receiving_order_lines(id),
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    barcode VARCHAR(200),
    quantity NUMERIC(15, 2) NOT NULL DEFAULT 1,
    lot_number VARCHAR(100),
    expiry_date DATE,
    serial_number VARCHAR(100),
    photo_url VARCHAR(500),
    notes TEXT,
    scanned_by UUID,
    scanned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_receiving_scans_order ON receiving_scans(receiving_order_id);
CREATE INDEX IF NOT EXISTS idx_receiving_scans_item ON receiving_scans(item_id);

CREATE TABLE IF NOT EXISTS discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    receiving_order_id UUID NOT NULL REFERENCES receiving_orders(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    line_id UUID REFERENCES receiving_order_lines(id),
    type VARCHAR(20) NOT NULL,
    expected_qty NUMERIC(15, 2) DEFAULT 0,
    actual_qty NUMERIC(15, 2) DEFAULT 0,
    difference NUMERIC(15, 2) DEFAULT 0,
    resolution_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_discrepancies_company ON discrepancies(company_id);
CREATE INDEX IF NOT EXISTS idx_discrepancies_order ON discrepancies(receiving_order_id);
CREATE INDEX IF NOT EXISTS idx_discrepancies_status ON discrepancies(resolution_status);
