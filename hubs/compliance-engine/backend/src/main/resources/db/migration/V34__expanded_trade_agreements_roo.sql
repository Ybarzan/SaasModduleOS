-- V34__expanded_trade_agreements_roo.sql
-- Expanded trade agreements + Rules of Origin + EUR.1 certificate support

-- ============================================================
-- EUR.1 Certificates table
-- ============================================================
CREATE TABLE IF NOT EXISTS eur1_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    certificate_number VARCHAR(50) NOT NULL UNIQUE,
    agreement_code VARCHAR(20) NOT NULL,
    origin_country VARCHAR(2) NOT NULL,
    importer_name VARCHAR(200) NOT NULL,
    exporter_name VARCHAR(200) NOT NULL,
    hs_code VARCHAR(12) NOT NULL,
    goods_description VARCHAR(500),
    net_weight_kg DOUBLE PRECISION,
    gross_weight_kg DOUBLE PRECISION,
    origin_criteria VARCHAR(10),
    production_method VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issue_date DATE NOT NULL,
    valid_until DATE,
    issuer_name VARCHAR(200),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_eur1_company ON eur1_certificates(company_id);
CREATE INDEX IF NOT EXISTS idx_eur1_number ON eur1_certificates(certificate_number);

-- ============================================================
-- Expanded Trade Agreements — 40+ new real EU FTAs
-- All use ON CONFLICT (code) DO NOTHING for idempotency
-- ============================================================

-- === Asia-Pacific FTAs ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUNZ', 'EU-New Zealand Free Trade Agreement', 'NZ', 'New Zealand', 'Signed June 2023. Elimination of 97% of tariffs over 7 years. Phased elimination for dairy and beef.', 'FTA', '01-97', 'WO + CTH/CTSH. Cumulation EU-NZ.'),
('EUAUS', 'EU-Australia Free Trade Agreement', 'AU', 'Australia', 'Signed 2023, pending ratification. Elimination of 99% of tariffs. Sensitive agricultural products phased.', 'FTA', '01-97', 'WO + CTH/CTSH. Cumulation EU-AU.'),
('EUIDN', 'EU-Indonesia Partnership Agreement', 'ID', 'Indonesia', 'Under negotiation since 2016. Currently GSP preferences apply.', 'PTA', '01-97', NULL),
('EUTHA', 'EU-Thailand Free Trade Agreement', 'TH', 'Thailand', 'Negotiations relaunched 2023. Existing GSP+ preferences for Thailand.', 'PTA', '01-97', NULL),
('EUMYS', 'EU-Malaysia Partnership Agreement', 'MY', 'Malaysia', 'Under negotiation. GSP preferences currently applicable.', 'PTA', '01-97', NULL),
('EUPHL', 'EU-Philippines Free Trade Agreement', 'PH', 'Philippines', 'Under negotiation. GSP+ preferences currently applicable.', 'PTA', '01-97', NULL),
('EUASEAN', 'EU-ASEAN Comprehensive Agreement', 'ID', 'ASEAN', 'Framework for ASEAN-EU trade. Individual bilateral FTAs negotiated with member states.', 'FTA', '01-97', 'Cumulation across ASEAN members where bilateral FTAs exist.')
ON CONFLICT (code) DO NOTHING;

-- === Modernized / Updated FTAs ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUCHILE2', 'EU-Chile Modernized Association Agreement', 'CL', 'Chili', 'Modernized agreement signed 2022. Enhanced rules of origin, digital trade, services. Replaces original EUCHILE.', 'FTA', '01-97', 'WO + CTH/CTSH. Bilateral cumulation EU-CL. Full roll-up permitted.'),
('EUMEX2', 'EU-Mexico Modernized Global Agreement', 'MX', 'Mexique', 'Modernized agreement in force 2020. Enhanced IP, services, sustainable development chapters.', 'FTA', '01-97', 'WO + CTH/CTSH. Bilateral cumulation EU-MX.')
ON CONFLICT (code) DO NOTHING;

-- === Eastern Partnership (DCFTA) ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUUKR', 'EU-Ukraine Deep and Comprehensive FTA (DCFTA)', 'UA', 'Ukraine', 'DCFTA in force since 2016. Full liberalization of industrial goods. Agricultural quotas managed.', 'FTA', '25-97', 'WO + CTSH/CTH. Bilateral cumulation. Diagonal cumulation possible with MD, GE.'),
('EUMDA', 'EU-Moldova Deep and Comprehensive FTA (DCFTA)', 'MD', 'Moldavie', 'DCFTA in force since 2016. Progressive liberalization. Phasing out duties on most industrial goods.', 'FTA', '25-97', 'WO + CTSH/CTH. Bilateral cumulation EU-MD.'),
('EUGE', 'EU-Georgia Deep and Comprehensive FTA (DCFTA)', 'GE', 'Georgie', 'DCFTA in force since 2016. Liberalization of trade in goods and services.', 'FTA', '25-97', 'WO + CTSH/CTH. Bilateral cumulation EU-GE.')
ON CONFLICT (code) DO NOTHING;

-- === Western Balkans Stabilisation Agreements ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUAL', 'EU-Albania Stabilisation and Association Agreement', 'AL', 'Albanie', 'SAA in force since 2009. Autonomous trade preferences for Albania. Zero duties on most industrial goods.', 'PTA', '01-97', 'WO + CTH. Cumulation with other Western Balkans partners.'),
('EUMNE', 'EU-Montenegro Stabilisation and Association Agreement', 'ME', 'Montenegro', 'SAA in force since 2010. Autonomous trade liberalization. Accession candidate.', 'PTA', '01-97', 'WO + CTH. Cumulation with Western Balkans.'),
('EUMKD', 'EU-North Macedonia Stabilisation and Association Agreement', 'MK', 'Macdoine du Nord', 'SAA in force since 2004. FTA with zero tariffs on industrial goods. Stabilisation framework.', 'PTA', '01-97', 'WO + CTH. Cumulation with Western Balkans.'),
('EUSRB', 'EU-Serbia Stabilisation and Association Agreement', 'RS', 'Serbie', 'SAA in force since 2013. Zero duties on industrial goods. Accession candidate.', 'PTA', '01-97', 'WO + CTH. Cumulation with Western Balkans.')
ON CONFLICT (code) DO NOTHING;

-- === Mediterranean / ENP South ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUEGY', 'EU-Egypt Association Agreement', 'EG', 'Egypte', 'Association agreement in force since 2004. Free trade for industrial goods. Zero duties on most manufactured products.', 'PTA', '01-97', 'WO + CTSH. No cumulation.'),
('EUMAR', 'EU-Morocco Association Agreement (updated)', 'MA', 'Maroc', 'Advanced status. Free trade for industrial goods since 2000. Agriculture Protocols for citrus, tomatoes, strawberries.', 'PTA', '01-97', 'WO + CTSH. Bilateral cumulation.'),
('EUTUN', 'EU-Tunisia Association Agreement (updated)', 'TN', 'Tunisie', 'Association agreement since 1998. Free trade for industrial goods. Liberalization of agriculture ongoing.', 'PTA', '01-97', 'WO + CTSH. Bilateral cumulation.'),
('EUJOR', 'EU-Jordan Association Agreement', 'JO', 'Jordanie', 'Association agreement in force since 2002. Qualified Industrial Zones (QIZ) regime. Preferential access.', 'PTA', '01-97', 'WO + CTSH. QIZ rules apply for textiles.'),
('EULBN', 'EU-Lebanon Association Agreement', 'LB', 'Liban', 'Association agreement in force since 2003. Free trade for industrial products. Pan-Euro-Med cumulation.', 'PTA', '01-97', 'WO + CTSH. Pan-Euro-Med cumulation.'),
('EUISR', 'EU-Israel Association Agreement', 'IL', 'Israel', 'Association agreement since 2000. Free trade for all industrial goods. Agriculture liberalization partial.', 'FTA', '01-97', 'WO. Diagonal cumulation via Euro-Med.'),
('EUPSE', 'EU-Palestine Interim Association Agreement', 'PS', 'Palestine', 'Interim association agreement since 1997. Duty-free and quota-free for industrial goods from West Bank and Gaza.', 'PTA', '01-97', 'WO. Products originating in West Bank and Gaza Strip.')
ON CONFLICT (code) DO NOTHING;

-- === Africa — EPAs and FTAs ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUSADC', 'EU-SADC Economic Partnership Agreement', 'ZA', 'Afrique du Sud', 'SADC EPA with Botswana, Lesotho, Namibia, Eswatini, Angola, Mozambique. In force 2016. 98% duty-free access.', 'FTA', '01-97', 'WO + CTSH/CTH. Cumulation SADC-EPA group.'),
('EUKEN', 'EU-Kenya Economic Partnership Agreement', 'KE', 'Kenya', 'Eastern非洲 EPA (with Tanzania, Uganda, Rwanda, Burundi). Signed 2023. Duty-free, quota-free.', 'FTA', '01-97', 'WO + CTSH. Cumulation EAC-EPA group.'),
('EUCIV', 'EU-Cote d''Ivoire Economic Partnership Agreement', 'CI', 'Cote d''Ivoire', 'West Africa EPA group. Provisional application since 2016. Asymmetric liberalization.', 'PTA', '01-97', 'WO + CTSH. Cumulation WA-EPA group.'),
('EUGHA', 'EU-Ghana Economic Partnership Agreement', 'GH', 'Ghana', 'West Africa EPA group. Provisional application since 2016. Duty-free for 100% of EU exports.', 'PTA', '01-97', 'WO + CTSH. Cumulation WA-EPA group.'),
('EUSN', 'EU-Senegal Economic Partnership Agreement', 'SN', 'Senegal', 'West Africa EPA. Provisional application. Liberalization of EU exports, reciprocal for Senegal.', 'PTA', '01-97', 'WO + CTSH. Cumulation WA-EPA group.'),
('EUMG', 'EU-Madagascar Economic Partnership Agreement', 'MG', 'Madagascar', 'Indian Ocean EPA (with Mauritius, Seychelles, Comoros). Duty-free access for most products.', 'PTA', '01-97', 'WO + CTSH. Cumulation Indian Ocean EPA group.'),
('EUMU', 'EU-Mauritius Economic Partnership Agreement', 'MU', 'Ile Maurice', 'Indian Ocean EPA. Duty-free and quota-free for EPA group members.', 'PTA', '01-97', 'WO + CTSH. Cumulation Indian Ocean EPA group.'),
('EUSC', 'EU-Seychelles Economic Partnership Agreement', 'SC', 'Seychelles', 'Indian Ocean EPA. Provisional application. Free trade for industrial goods.', 'PTA', '01-97', 'WO + CTSH. Cumulation Indian Ocean EPA group.'),
('EUGA', 'EU-Gabon Partnership Agreement', 'GA', 'Gabon', 'Central Africa EPA group (CEMAC). Liberalization ongoing. GSP+ preferences currently applicable.', 'PTA', '01-97', NULL),
('EUCM', 'EU-Cameroon Economic Partnership Agreement', 'CM', 'Cameroun', 'Central Africa EPA (CEMAC). In force 2014. Asymmetric liberalization over 15 years.', 'PTA', '01-97', 'WO + CTSH. Cumulation CEMAC-EPA group.'),
('EUTZ', 'EU-Tanzania Economic Partnership Agreement', 'TZ', 'Tanzanie', 'Eastern Africa EPA (EAC). Signed 2023. Duty-free, quota-free.', 'FTA', '01-97', 'WO + CTSH. Cumulation EAC-EPA group.')
ON CONFLICT (code) DO NOTHING;

-- === Americas ===
INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUURU', 'EU-Uruguay Association Agreement', 'UY', 'Uruguay', 'Negotiations ongoing as part of EU-Mercosur. Currently GSP preferences apply.', 'PTA', '01-97', NULL),
('EUCOLPEE', 'EU-Colombia-Peru-Ecuador Trade Agreement', 'CO', 'Colombie/Perou/Equateur', 'Trade agreement in force since 2013 (Colombia/Peru), 2017 (Ecuador). Andean Community. 97% duty-free.', 'FTA', '01-97', 'WO + CTSH/CTH. Cumulation Andean Community.'),
('EUCAM', 'EU-Central America Association Agreement', 'HN', 'Amerique centrale', 'Association agreement with Guatemala, Honduras, El Salvador, Nicaragua, Costa Rica, Panama. In force 2013.', 'FTA', '01-97', 'WO + CTH/CTSH. Cumulation Central America.'),
('EUCARIFOR', 'EU-CARICOM-OECS Economic Partnership Agreement', 'JM', 'CARICOM/OECS', 'EPA with CARICOM states (Jamaica, Trinidad, Barbados, etc.) and OECS. In force 2008.', 'PTA', '01-97', 'WO + CTSH. Cumulation CARICOM.')
ON CONFLICT (code) DO NOTHING;
