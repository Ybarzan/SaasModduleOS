-- V3__inventory.sql
-- Catalogue d'articles et codes-barres associes.

CREATE TABLE IF NOT EXISTS inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    sku VARCHAR(100),
    name VARCHAR(300) NOT NULL,
    description TEXT,
    hs_code VARCHAR(20),
    origin_country VARCHAR(3),
    unit VARCHAR(10) NOT NULL DEFAULT 'PCS',
    unit_price NUMERIC(15, 2) DEFAULT 0,
    category VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inventory_items_company ON inventory_items(company_id);
CREATE INDEX IF NOT EXISTS idx_inventory_items_hs ON inventory_items(hs_code);

CREATE TABLE IF NOT EXISTS item_barcodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    item_id UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    barcode VARCHAR(200) NOT NULL,
    barcode_type VARCHAR(20) NOT NULL DEFAULT 'EAN13',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_item_barcodes_company_code ON item_barcodes(company_id, barcode);
CREATE INDEX IF NOT EXISTS idx_item_barcodes_item ON item_barcodes(item_id);

CREATE TABLE IF NOT EXISTS stock_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    quantity_on_hand NUMERIC(15, 2) NOT NULL DEFAULT 0,
    quantity_reserved NUMERIC(15, 2) NOT NULL DEFAULT 0,
    quantity_in_transit NUMERIC(15, 2) NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_balances_warehouse_item ON stock_balances(warehouse_id, item_id);
CREATE INDEX IF NOT EXISTS idx_stock_balances_company ON stock_balances(company_id);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    item_id UUID NOT NULL REFERENCES inventory_items(id),
    quantity NUMERIC(15, 2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    note TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_stock_movements_company ON stock_movements(company_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_item ON stock_movements(item_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created ON stock_movements(created_at);
