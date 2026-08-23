-- V47__preferential_regimes.sql
-- R�gimes pr�f�rentiels EU : r�gles d'origine, taux pr�f�rentiels, TVA intracommunautaire

-- ============================================================
-- Pr�ferential rates (taux pr�f�rentiels par pays x HS)
-- ============================================================
CREATE TABLE IF NOT EXISTS preferential_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    hs_code VARCHAR(12) NOT NULL,
    origin_country VARCHAR(2) NOT NULL,
    dest_country VARCHAR(2) NOT NULL DEFAULT 'FR',
    agreement_code VARCHAR(20) NOT NULL,
    agreement_name VARCHAR(200),
    duty_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    duty_type VARCHAR(3) DEFAULT 'AD',
    is_preferential BOOLEAN NOT NULL DEFAULT TRUE,
    origin_criterion VARCHAR(3),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pref_rates_hs ON preferential_rates(hs_code);
CREATE INDEX IF NOT EXISTS idx_pref_rates_origin ON preferential_rates(origin_country, dest_country);
CREATE INDEX IF NOT EXISTS idx_pref_rates_agreement ON preferential_rates(agreement_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pref_rates ON preferential_rates(company_id, hs_code, origin_country, dest_country, agreement_code);

-- ============================================================
-- Rules of Origin verification log
-- ============================================================
CREATE TABLE IF NOT EXISTS origin_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    shipment_id UUID,
    hs_code VARCHAR(12) NOT NULL,
    origin_country VARCHAR(2) NOT NULL,
    dest_country VARCHAR(2) NOT NULL,
    agreement_code VARCHAR(20),
    criterion_used VARCHAR(3),
    is_originating BOOLEAN NOT NULL,
    value_added_pct NUMERIC(5,2),
    explanation TEXT,
    warnings TEXT[],
    verified_at TIMESTAMP NOT NULL DEFAULT NOW(),
    verified_by UUID
);
CREATE INDEX IF NOT EXISTS idx_origin_ver_company ON origin_verifications(company_id);
CREATE INDEX IF NOT EXISTS idx_origin_ver_shipment ON origin_verifications(shipment_id);

-- ============================================================
-- Customs invoices (factures douani�res)
-- ============================================================
CREATE TABLE IF NOT EXISTS customs_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    shipment_id UUID,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_date DATE NOT NULL DEFAULT CURRENT_DATE,
    shipper_name VARCHAR(300),
    shipper_address TEXT,
    shipper_city VARCHAR(100),
    shipper_country VARCHAR(3),
    shipper_postal_code VARCHAR(20),
    consignee_name VARCHAR(300),
    consignee_address TEXT,
    consignee_city VARCHAR(100),
    consignee_country VARCHAR(3),
    consignee_postal_code VARCHAR(20),
    eori_number VARCHAR(20),
    goods_description TEXT,
    currency VARCHAR(3) DEFAULT 'EUR',
    total_goods_value NUMERIC(15,2) DEFAULT 0,
    total_weight_kg NUMERIC(15,3),
    total_packages INTEGER,
    incoterm_code VARCHAR(3),
    total_duty NUMERIC(15,2) DEFAULT 0,
    total_vat NUMERIC(15,2) DEFAULT 0,
    total_amount NUMERIC(15,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    pdf_url VARCHAR(500),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customs_invoice_company ON customs_invoices(company_id);
CREATE INDEX IF NOT EXISTS idx_customs_invoice_shipment ON customs_invoices(shipment_id);
CREATE INDEX IF NOT EXISTS idx_customs_invoice_number ON customs_invoices(invoice_number);

-- ============================================================
-- Customs invoice items (lignes de facture douani�re)
-- ============================================================
CREATE TABLE IF NOT EXISTS customs_invoice_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES customs_invoices(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    sku VARCHAR(100),
    name VARCHAR(300),
    description TEXT,
    hs_code VARCHAR(12),
    quantity NUMERIC(15,2),
    unit VARCHAR(10) DEFAULT 'PCS',
    unit_price NUMERIC(15,2) DEFAULT 0,
    total_value NUMERIC(15,2) DEFAULT 0,
    country_of_origin VARCHAR(3),
    duty_rate NUMERIC(5,2) DEFAULT 0,
    duty_type VARCHAR(3),
    is_preferential BOOLEAN DEFAULT FALSE,
    duty_amount NUMERIC(15,2) DEFAULT 0,
    vat_rate NUMERIC(5,2) DEFAULT 20,
    vat_amount NUMERIC(15,2) DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_customs_invoice_items_invoice ON customs_invoice_items(invoice_id);

-- ============================================================
-- TVA intracommunautaire - taux r�duits par pays
-- ============================================================
CREATE TABLE IF NOT EXISTS vat_intracommunity_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code VARCHAR(2) NOT NULL,
    standard_rate NUMERIC(5,2) NOT NULL,
    reduced_rate_1 NUMERIC(5,2),
    reduced_rate_2 NUMERIC(5,2),
    super_reduced_rate NUMERIC(5,2),
    reverse_charge_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    vies_validation BOOLEAN NOT NULL DEFAULT TRUE,
    tai_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(country_code)
);

-- Insert EU VAT rates for intra-community
INSERT INTO vat_intracommunity_rates (country_code, standard_rate, reduced_rate_1, reduced_rate_2, super_reduced_rate, reverse_charge_applicable, vies_validation, tai_applicable) VALUES
('FR', 20.00, 10.00, 5.50, 2.10, TRUE, TRUE, FALSE),
('DE', 19.00, 7.00, NULL, NULL, TRUE, TRUE, FALSE),
('IT', 22.00, 10.00, 5.00, 4.00, TRUE, TRUE, FALSE),
('ES', 21.00, 10.00, 4.00, NULL, TRUE, TRUE, FALSE),
('PT', 23.00, 13.00, 6.00, NULL, TRUE, TRUE, FALSE),
('NL', 21.00, 9.00, NULL, NULL, TRUE, TRUE, FALSE),
('BE', 21.00, 12.00, 6.00, NULL, TRUE, TRUE, FALSE),
('LU', 17.00, 14.00, 8.00, 3.00, TRUE, TRUE, FALSE),
('AT', 20.00, 10.00, 13.00, 5.00, TRUE, TRUE, FALSE),
('FI', 24.00, 14.00, 10.00, NULL, TRUE, TRUE, FALSE),
('SE', 25.00, 12.00, 6.00, NULL, TRUE, TRUE, FALSE),
('DK', 25.00, NULL, NULL, NULL, TRUE, TRUE, FALSE),
('IE', 23.00, 13.50, 9.00, 4.80, TRUE, TRUE, FALSE),
('GR', 24.00, 13.00, 6.50, NULL, TRUE, TRUE, FALSE),
('PL', 23.00, 8.00, 5.00, NULL, TRUE, TRUE, FALSE),
('CZ', 21.00, 15.00, 10.00, NULL, TRUE, TRUE, FALSE),
('SK', 20.00, 10.00, NULL, NULL, TRUE, TRUE, FALSE),
('HU', 27.00, 18.00, 5.00, NULL, TRUE, TRUE, FALSE),
('RO', 19.00, 9.00, 5.00, NULL, TRUE, TRUE, FALSE),
('BG', 20.00, 9.00, NULL, NULL, TRUE, TRUE, FALSE),
('HR', 25.00, 13.00, 5.00, NULL, TRUE, TRUE, FALSE),
('SI', 22.00, 9.50, 5.00, NULL, TRUE, TRUE, FALSE),
('EE', 20.00, 9.00, 5.00, NULL, TRUE, TRUE, FALSE),
('LV', 21.00, 12.00, 5.00, NULL, TRUE, TRUE, FALSE),
('LT', 21.00, 9.00, 5.00, NULL, TRUE, TRUE, FALSE),
('CY', 19.00, 9.00, 5.00, NULL, TRUE, TRUE, FALSE),
('MT', 18.00, 7.00, 5.00, NULL, TRUE, TRUE, FALSE)
ON CONFLICT (country_code) DO NOTHING;