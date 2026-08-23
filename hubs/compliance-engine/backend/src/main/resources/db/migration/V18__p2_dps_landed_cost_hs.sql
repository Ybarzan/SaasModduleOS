-- V18__p2_dps_landed_cost_hs.sql
-- P2: Denied Party Screening, Landed Cost Calculator, HS Code Suggestions

-- ============================================================
-- Denied Party Checks
-- ============================================================
CREATE TABLE IF NOT EXISTS denied_party_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    checked_name VARCHAR(255) NOT NULL,
    check_type VARCHAR(20) NOT NULL DEFAULT 'ENTITY',
    result VARCHAR(20) NOT NULL DEFAULT 'CLEAR',
    matched_list_name VARCHAR(100),
    matched_entry_id VARCHAR(50),
    matched_entry_details TEXT,
    risk_level VARCHAR(10) NOT NULL DEFAULT 'LOW',
    country_code VARCHAR(2),
    notes TEXT,
    checked_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dps_company ON denied_party_checks(company_id);
CREATE INDEX idx_dps_result ON denied_party_checks(result);
CREATE INDEX idx_dps_checked_name ON denied_party_checks(checked_name);
CREATE INDEX idx_dps_created_at ON denied_party_checks(created_at);

-- ============================================================
-- Sanctioned Entities (reference data)
-- ============================================================
CREATE TABLE IF NOT EXISTS sanctioned_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_source VARCHAR(20) NOT NULL,
    entry_id VARCHAR(50) NOT NULL,
    entity_type VARCHAR(20) NOT NULL DEFAULT 'ENTITY',
    name VARCHAR(500) NOT NULL,
    aliases TEXT,
    country_code VARCHAR(2),
    reason TEXT,
    program VARCHAR(100),
    list_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sanctioned_name ON sanctioned_entities(name);
CREATE INDEX idx_sanctioned_country ON sanctioned_entities(country_code);
CREATE INDEX idx_sanctioned_list_source ON sanctioned_entities(list_source);
CREATE INDEX idx_sanctioned_active ON sanctioned_entities(is_active);

-- ============================================================
-- Seed: Sanctioned Entities (EU Consolidated List examples)
-- ============================================================
INSERT INTO sanctioned_entities (list_source, entry_id, entity_type, name, aliases, country_code, reason, program, is_active) VALUES
('EU', 'EU-001', 'ENTITY', 'Rosoboronexport', 'ROSOBORONEXPORT', 'RU', 'Arms trade sanctions', 'EU Russia sanctions', true),
('EU', 'EU-002', 'ENTITY', 'Gazprom Neft', 'Gazprom Neft, Gazpromneft', 'RU', 'Energy sector sanctions', 'EU Russia sanctions', true),
('EU', 'EU-003', 'VESSEL', 'Luna', 'IMO 8500000', 'RU', 'Sanctioned vessel', 'EU Russia sanctions', true),
('EU', 'EU-004', 'PERSON', 'Igor Sechin', 'SECHIN Igor', 'RU', 'Senior government official', 'EU Russia sanctions', true),
('EU', 'EU-005', 'ENTITY', 'Iran Air', 'IRANAIR, Homa', 'IR', 'Aviation sanctions', 'EU Iran sanctions', true),
('EU', 'EU-006', 'ENTITY', 'National Iranian Oil Company', 'NIOC, NIOC Iran', 'IR', 'Oil and gas sanctions', 'EU Iran sanctions', true),
('EU', 'EU-007', 'PERSON', 'Kim Jong Un', 'KIM Jong Un, Kim Jong-un', 'KP', 'DPRK leadership', 'EU DPRK sanctions', true),
('EU', 'EU-008', 'ENTITY', 'Korea Mining Development Trading Corporation', 'KOMID', 'KP', 'Arms procurement', 'EU DPRK sanctions', true),
('EU', 'EU-009', 'ENTITY', 'Al-Quds Bank', 'AL QUDS, AL-QUDS', 'SY', 'Financial sanctions', 'EU Syria sanctions', true),
('EU', 'EU-010', 'PERSON', 'Bashar Al-Assad', 'ASSAD Bashar', 'SY', 'Syrian regime', 'EU Syria sanctions', true),
('EU', 'EU-011', 'ENTITY', 'Wagner Group', 'WAGNER, PMC Wagner', 'RU', 'Mercenary group', 'EU Russia sanctions', true),
('EU', 'EU-012', 'ENTITY', 'Rostec', 'ROSTEC, Rostekhnologii', 'RU', 'Defense sector', 'EU Russia sanctions', true),
('EU', 'EU-013', 'VESSEL', 'Vostochny', 'IMO 9000000', 'RU', 'Sanctioned vessel', 'EU Russia sanctions', true),
('EU', 'EU-014', 'ENTITY', 'Syrian Scientific Research Center', 'SSRC, SRC', 'SY', 'Chemical weapons', 'EU Syria sanctions', true),
('EU', 'EU-015', 'PERSON', 'Volodymyr Putin', 'PUTIN Vladimir', 'RU', 'Head of state', 'EU Russia sanctions', true),
('EU', 'EU-016', 'ENTITY', 'Belneftekhim', 'Belarusian chemicals', 'BY', 'Chemical industry sanctions', 'EU Belarus sanctions', true),
('EU', 'EU-017', 'ENTITY', 'Minsk Automobile Plant', 'MAZ, Minskiy Avtobusny', 'BY', 'Industrial sanctions', 'EU Belarus sanctions', true),
('EU', 'EU-018', 'PERSON', 'Alexander Lukashenko', 'LUKASHENKO Alexander', 'BY', 'Belarus leadership', 'EU Belarus sanctions', true),
('EU', 'EU-019', 'ENTITY', 'Severstal', 'SEVERSTAL, Severstal Group', 'RU', 'Steel industry sanctions', 'EU Russia sanctions', true),
('EU', 'EU-020', 'ENTITY', 'Novatek', 'NOVATEK, OAO Novatek', 'RU', 'Energy sector sanctions', 'EU Russia sanctions', true)
ON CONFLICT DO NOTHING;

-- ============================================================
-- Landed Costs
-- ============================================================
CREATE TABLE IF NOT EXISTS landed_costs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    calculation_name VARCHAR(200),
    origin_country VARCHAR(2) NOT NULL,
    destination_country VARCHAR(2) NOT NULL,
    incoterm VARCHAR(10) DEFAULT 'FOB',
    product_value DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    freight_cost DECIMAL(15,2) DEFAULT 0,
    insurance_cost DECIMAL(15,2) DEFAULT 0,
    duty_amount DECIMAL(15,2) DEFAULT 0,
    duty_rate DECIMAL(8,4) DEFAULT 0,
    vat_amount DECIMAL(15,2) DEFAULT 0,
    vat_rate DECIMAL(8,4) DEFAULT 0,
    port_charges DECIMAL(15,2) DEFAULT 0,
    customs_fees DECIMAL(15,2) DEFAULT 0,
    handling_fees DECIMAL(15,2) DEFAULT 0,
    last_mile_cost DECIMAL(15,2) DEFAULT 0,
    total_landed_cost DECIMAL(15,2) DEFAULT 0,
    unit_count INTEGER DEFAULT 1,
    total_landed_cost_per_unit DECIMAL(15,2) DEFAULT 0,
    margin DECIMAL(15,2),
    margin_percent DECIMAL(8,4),
    selling_price DECIMAL(15,2),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_landed_cost_company ON landed_costs(company_id);
CREATE INDEX idx_landed_cost_shipment ON landed_costs(shipment_id);
CREATE INDEX idx_landed_cost_created_at ON landed_costs(created_at);

-- ============================================================
-- HS Code Suggestions
-- ============================================================
CREATE TABLE IF NOT EXISTS hs_code_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    product_description TEXT NOT NULL,
    suggested_code_1 VARCHAR(10) NOT NULL,
    suggested_description_1 VARCHAR(500),
    confidence_1 DECIMAL(4,3),
    suggested_code_2 VARCHAR(10),
    suggested_description_2 VARCHAR(500),
    confidence_2 DECIMAL(4,3),
    suggested_code_3 VARCHAR(10),
    suggested_description_3 VARCHAR(500),
    confidence_3 DECIMAL(4,3),
    user_selection VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hs_suggestion_company ON hs_code_suggestions(company_id);
CREATE INDEX idx_hs_suggestion_created_at ON hs_code_suggestions(created_at);
