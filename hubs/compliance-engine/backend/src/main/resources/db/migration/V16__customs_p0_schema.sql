-- V16__customs_p0_schema.sql
-- P0: EORI, TARIC, Trade Agreements, Customs enhancement

-- ============================================================
-- EORI Numbers
-- ============================================================
CREATE TABLE eori_numbers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    eori VARCHAR(20) NOT NULL UNIQUE,
    holder_name VARCHAR(200) NOT NULL,
    holder_address VARCHAR(500),
    holder_country VARCHAR(2),
    type VARCHAR(10) NOT NULL DEFAULT 'EU',
    is_default BOOLEAN NOT NULL DEFAULT TRUE,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    validated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_eori_company ON eori_numbers(company_id);
CREATE INDEX idx_eori_number ON eori_numbers(eori);

-- ============================================================
-- TARIC Tariff Rates
-- ============================================================
CREATE TABLE taric_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hs_code VARCHAR(12) NOT NULL,
    description VARCHAR(500),
    origin_country VARCHAR(2) NOT NULL,
    destination_country VARCHAR(2) NOT NULL,
    duty_rate DOUBLE PRECISION NOT NULL,
    duty_type VARCHAR(3) NOT NULL DEFAULT 'AD',
    specific_amount DOUBLE PRECISION,
    specific_unit VARCHAR(20),
    trade_agreement_code VARCHAR(20),
    is_prefential BOOLEAN NOT NULL DEFAULT FALSE,
    prefential_origin_criteria VARCHAR(10),
    is_anti_dumping BOOLEAN NOT NULL DEFAULT FALSE,
    anti_dumping_duty DOUBLE PRECISION,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_taric_hs ON taric_rates(hs_code);
CREATE INDEX idx_taric_hs_origin ON taric_rates(hs_code, origin_country);
CREATE INDEX idx_taric_validity ON taric_rates(valid_from, valid_to);

-- ============================================================
-- Trade Agreements (APE/AGP)
-- ============================================================
CREATE TABLE trade_agreements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    partner_country VARCHAR(2) NOT NULL,
    partner_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    agreement_type VARCHAR(10) NOT NULL DEFAULT 'FTA',
    hs_chapters_covered VARCHAR(2000),
    origin_rules VARCHAR(2000),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_agreement_country ON trade_agreements(partner_country, is_active);

-- ============================================================
-- Seed: Trade Agreements EU (France-based perspective)
-- ============================================================
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered) VALUES
('EVFTA', 'Accord de libre-échange UE-Vietnam', 'VN', 'Vietnam', 'Suppression progressive des droits de douane sur 99% des lignes tarifaires. Application progressive jusqu''en 2027.', 'FTA', '01-97'),
('CETA', 'Accord économique et commercial global UE-Canada', 'CA', 'Canada', 'Élimination de 98% des droits de douane. Application depuis septembre 2017.', 'FTA', '01-97'),
('EUEJPA', 'UE-Japon Economic Partnership Agreement', 'JP', 'Japon', 'Suppression de 97% des droits de douane. En vigueur depuis février 2019.', 'FTA', '01-97'),
('EUKORF', 'Accord UE-Corée du Sud', 'KR', 'Corée du Sud', 'Zone de libre-échange depuis juillet 2011. Élimination de 96% des droits.', 'FTA', '01-97'),
('EUMAP', 'Accord UE-Maroc', 'MA', 'Maroc', 'Accord d''association. Libre-échange pour produits industriels.', 'FTA', '01-97'),
('EUTNPA', 'Accord UE-Tunisie', 'TN', 'Tunisie', 'Accord d''association. Libre-échange pour produits industriels.', 'FTA', '01-97'),
('EUCU', 'Union douanière UE-Turquie', 'TR', 'Turquie', 'Union douanière pour les produits industriels depuis 1996.', 'CU', '25-97'),
('EUGB', 'Accord de commerce et de coopération UE-UK (TCA)', 'GB', 'Royaume-Uni', 'Post-Brexit. Zéro droit et zéro quota si Rules of Origin respectées.', 'FTA', '01-97'),
('EUSGP', 'Accord UE-Singapour', 'SG', 'Singapour', 'Zone de libre-échange. Élimination de 99% des droits.', 'FTA', '01-97'),
('EUMERCOSUR', 'Accord UE-MERCOSUR (pending)', 'BR', 'Brésil', 'En cours de ratification. Accords partiels sur agriculture.', 'FTA', '01-97'),
('EUMEXICO', 'Accord UE-Mexique modernisé', 'MX', 'Mexique', 'Modernisation de l''accord global. Libre-échange élargi.', 'FTA', '01-97'),
('EUCHILE', 'Accord UE-Chili', 'CL', 'Chili', 'Accord d''association. Libre-échange pour produits industriels.', 'FTA', '01-97'),
('EUSINGAPORE', 'UE-Singapour', 'SG', 'Singapour', 'Identique à EUSGP', 'FTA', '01-97'),
('EUGOA', 'Accord de partenariat économique régional UE-Afrique Australe', 'ZA', 'Afrique du Sud', 'SADC-EPA. Libre-échange pour 98% des lignes.', 'PTA', '01-97')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- Seed: TARIC Rates — Principaux droits MFN pour la France
-- (Données réalistes basées sur le TARIC EU 2026)
-- ============================================================
INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, valid_from) VALUES
-- Textiles ( chapitres 61-62 : vêtements )
('6101', 'Vêtements pour hommes/femmes, tricot, manteaux', 'CN', 'FR', 12.0, 'AD', FALSE, '2026-01-01'),
('6101', 'Vêtements pour hommes/femmes, tricot, manteaux', 'BD', 'FR', 9.6, 'AD', FALSE, '2026-01-01'),
('6101', 'Vêtements pour hommes/femmes, tricot, manteaux', 'IN', 'FR', 12.0, 'AD', FALSE, '2026-01-01'),
('6101', 'Vêtements pour hommes/femmes, tricot, manteaux', 'VN', 'FR', 8.0, 'AD', TRUE, '2026-01-01'),
('6201', 'Vêtements pour hommes/femmes, pas tricot, manteaux', 'CN', 'FR', 12.0, 'AD', FALSE, '2026-01-01'),
('6201', 'Vêtements pour hommes/femmes, pas tricot, manteaux', 'VN', 'FR', 8.0, 'AD', TRUE, '2026-01-01'),
('6201', 'Vêtements pour hommes/femmes, pas tricot, manteaux', 'TR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Chaussures (chapitre 64)
('6403', 'Chaussures à semelles caoutchouc/plastique', 'CN', 'FR', 16.9, 'AD', FALSE, '2026-01-01'),
('6403', 'Chaussures à semelles caoutchouc/plastique', 'VN', 'FR', 12.0, 'AD', TRUE, '2026-01-01'),
('6403', 'Chaussures à semelles caoutchouc/plastique', 'IN', 'FR', 16.9, 'AD', FALSE, '2026-01-01'),

-- Électronique (chapitres 84-85)
('8471', 'Machines automatiques de traitement de données', 'CN', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('8471', 'Machines automatiques de traitement de données', 'VN', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),
('8517', 'Appareils de téléphonie/smartphones', 'CN', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('8517', 'Appareils de téléphonie/smartphones', 'KR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),
('8528', 'Écrans, moniteurs, projecteurs', 'CN', 'FR', 14.0, 'AD', FALSE, '2026-01-01'),
('8528', 'Écrans, moniteurs, projecteurs', 'KR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Produits agricoles (chapitre 02-24)
('0201', 'Viande de bovin, fraîche ou réfrigérée', 'BR', 'FR', 12.8, 'AD', FALSE, '2026-01-01'),
('0201', 'Viande de bovin, fraîche ou réfrigérée', 'AR', 'FR', 6.4, 'AD', FALSE, '2026-01-01'),
('1001', 'Blé et méteil', 'UA', 'FR', 95.0, 'SD', FALSE, '2026-01-01'),
('1001', 'Blé et méteil', 'CA', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Véhicules (chapitre 87)
('8703', 'Voitures et véhicules de tourisme', 'CN', 'FR', 6.5, 'AD', FALSE, '2026-01-01'),
('8703', 'Voitures et véhicules de tourisme', 'JP', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),
('8703', 'Voitures et véhicules de tourisme', 'KR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),
('8711', 'Motos et cycles', 'CN', 'FR', 6.5, 'AD', FALSE, '2026-01-01'),
('8711', 'Motos et cycles', 'JP', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Produits chimiques (chapitre 29-38)
('2941', 'Antibiotiques', 'CN', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('3004', 'Médicaments en doses', 'IN', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('3926', 'Articles en matières plastiques', 'CN', 'FR', 6.5, 'AD', FALSE, '2026-01-01'),
('3926', 'Articles en matières plastiques', 'TR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Métaux (chapitre 72-73)
('7210', 'Tôles en acier laminées', 'CN', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('7210', 'Tôles en acier laminées', 'TR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),
('7318', 'Vis, boulons, écrous en acier', 'CN', 'FR', 3.7, 'AD', FALSE, '2026-01-01'),
('7318', 'Vis, boulons, écrous en acier', 'TR', 'FR', 0.0, 'AD', TRUE, '2026-01-01'),

-- Intra-EU = 0%
('8471', 'Machines automatiques de traitement de données', 'DE', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('8517', 'Appareils de téléphonie/smartphones', 'DE', 'FR', 0.0, 'AD', FALSE, '2026-01-01'),
('6201', 'Vêtements pour hommes/femmes', 'IT', 'FR', 0.0, 'AD', FALSE, '2026-01-01');

-- ============================================================
-- Seed: EORI example (for dev/testing — will be overridden by users)
-- ============================================================
-- (No seed EORI — users create their own)

-- ============================================================
-- Enhance shipments table with customs fields
-- ============================================================
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS eori_id UUID REFERENCES eori_numbers(id);
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS customs_declaration_number VARCHAR(50);
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS customs_status VARCHAR(30) DEFAULT 'NONE';
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS duty_amount DOUBLE PRECISION;
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS vat_amount DOUBLE PRECISION;
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS landed_cost DOUBLE PRECISION;
ALTER TABLE shipment_orders ADD COLUMN IF NOT EXISTS country_of_origin VARCHAR(2);
