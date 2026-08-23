-- V17__customs_p1_declarations.sql
-- P1: Customs declarations (DAU, DEB/Intrastat, ICS2, AES/EXS)

-- ============================================================
-- Customs Declarations (DAU — Document Administratif Unique)
-- ============================================================
CREATE TABLE IF NOT EXISTS customs_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    declaration_number VARCHAR(50),
    declaration_type VARCHAR(20) NOT NULL DEFAULT 'DAU_IMPORT',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    customs_office VARCHAR(20),
    customs_regime VARCHAR(10),
    customs_code VARCHAR(10),
    declared_value DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    origin_country VARCHAR(2),
    destination_country VARCHAR(2),
    hs_code VARCHAR(10),
    goods_description TEXT,
    net_weight DECIMAL(10,2),
    gross_weight DECIMAL(10,2),
    packages INTEGER,
    eori_id UUID REFERENCES eori_numbers(id) ON DELETE SET NULL,
    submitted_at TIMESTAMP,
    cleared_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customs_declaration_company ON customs_declarations(company_id);
CREATE INDEX idx_customs_declaration_status ON customs_declarations(status);
CREATE INDEX idx_customs_declaration_number ON customs_declarations(declaration_number);
CREATE INDEX idx_customs_declaration_shipment ON customs_declarations(shipment_id);

-- ============================================================
-- DEB Declarations (Déclaration d'Échanges de Biens — Intrastat)
-- ============================================================
CREATE TABLE IF NOT EXISTS deb_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    declaration_number VARCHAR(50),
    declaration_type VARCHAR(30) NOT NULL DEFAULT 'DEB_INTRODUCTION',
    period VARCHAR(7) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    partner_country VARCHAR(2),
    nature_of_transaction VARCHAR(5),
    mode_of_transport VARCHAR(5),
    net_mass DECIMAL(10,2),
    statistical_value DECIMAL(15,2),
    hs_code_8 VARCHAR(10),
    goods_description TEXT,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_deb_company ON deb_declarations(company_id);
CREATE INDEX idx_deb_period ON deb_declarations(period);
CREATE INDEX idx_deb_status ON deb_declarations(status);
CREATE INDEX idx_deb_shipment ON deb_declarations(shipment_id);

-- ============================================================
-- ICS2 Declarations (Import Control System 2)
-- ============================================================
CREATE TABLE IF NOT EXISTS ics2_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    declaration_number VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sender_eori VARCHAR(20),
    receiver_eori VARCHAR(20),
    vessel_name VARCHAR(100),
    voyage_number VARCHAR(20),
    container_number VARCHAR(20),
    hs_code_6 VARCHAR(10),
    goods_description TEXT,
    gross_weight DECIMAL(10,2),
    packages_count INTEGER,
    submitted_at TIMESTAMP,
    responded_at TIMESTAMP,
    response_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ics2_company ON ics2_declarations(company_id);
CREATE INDEX idx_ics2_status ON ics2_declarations(status);
CREATE INDEX idx_ics2_shipment ON ics2_declarations(shipment_id);

-- ============================================================
-- Export Declarations (AES — Export Accompanying Document / EXS)
-- ============================================================
CREATE TABLE IF NOT EXISTS export_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    declaration_number VARCHAR(50),
    declaration_type VARCHAR(10) NOT NULL DEFAULT 'AES',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    exporter_eori VARCHAR(20),
    destination_country VARCHAR(2),
    goods_description TEXT,
    hs_code VARCHAR(10),
    declared_value DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    net_weight DECIMAL(10,2),
    gross_weight DECIMAL(10,2),
    packages_count INTEGER,
    submitted_at TIMESTAMP,
    validated_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_export_company ON export_declarations(company_id);
CREATE INDEX idx_export_status ON export_declarations(status);
CREATE INDEX idx_export_shipment ON export_declarations(shipment_id);
