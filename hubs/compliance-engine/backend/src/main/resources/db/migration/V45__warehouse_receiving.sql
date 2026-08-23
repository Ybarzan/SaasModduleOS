-- V45__warehouse_receiving.sql
-- Réception marchandises : entrepôts, catalogue articles, scan code-barres/QR, stock

-- ============================================================
-- Warehouses (entrepôts, liés à une branche optionnelle)
-- ============================================================
CREATE TABLE IF NOT EXISTS warehouses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    branch_id UUID,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_warehouses_company ON warehouses(company_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_branch ON warehouses(branch_id);

-- ============================================================
-- Inventory items (catalogue articles)
-- ============================================================
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

-- ============================================================
-- Item barcodes (multi codes par article)
-- ============================================================
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

-- ============================================================
-- Receiving orders (bons de réception / ASN)
-- ============================================================
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

-- ============================================================
-- Receiving order lines (lignes attendues)
-- ============================================================
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

-- ============================================================
-- Receiving scans (chaque scan code-barres/QR)
-- ============================================================
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

-- ============================================================
-- Discrepancies (écarts de réception)
-- ============================================================
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

-- ============================================================
-- Stock balances (soldes par entrepôt × article)
-- ============================================================
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

-- ============================================================
-- Stock movements (audit trail immuable)
-- ============================================================
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
