-- V53__cumulation.sql
-- Cumulation préférentielle : bilatérale + régionale (diagonale), roll-up
-- Complète V47 (régimes préférentiels) et V34 (accords + règles d'origine)

-- ============================================================
-- 1. Groupes de cumul régionaux
-- ============================================================
CREATE TABLE IF NOT EXISTS cumulation_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    member_countries jsonb NOT NULL DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO cumulation_groups (code, name, description, member_countries) VALUES
('PANEUROMED', 'Convention pan-euro-méditerranéenne (PEM)',
 'Cumul diagonal entre UE, AELE, Balkans occidentaux et partenaires méditerranéens disposant de règles d''origine identiques.',
 '["FR","DE","IT","ES","PT","NL","BE","LU","AT","FI","SE","DK","IE","GR","PL","CZ","SK","HU","RO","BG","HR","SI","EE","LV","LT","CY","MT","CH","NO","IS","LI","TR","GB","MA","TN","DZ","EG","IL","JO","LB","PS","SY","AL","ME","MK","RS","BA","XK"]'),
('ASEAN', 'Association des nations de l''Asie du Sud-Est',
 'Cumul régional entre États membres ASEAN couverts par un accord avec l''UE (règles d''origine harmonisées).',
 '["ID","VN","TH","MY","SG","PH","BN","KH","LA","MM"]'),
('SADC', 'EPA SADC (Afrique australe)',
 'Cumul régional entre les États de l''EPA SADC signataires.',
 '["ZA","BW","LS","NA","SZ","MZ","AO"]'),
('EAC', 'EPA EAC (Afrique de l''Est)',
 'Cumul régional entre les États de l''EPA Communauté d''Afrique de l''Est.',
 '["KE","TZ","UG","RW","BI"]'),
('WA-EPA', 'EPA Afrique de l''Ouest',
 'Cumul régional entre les États de l''EPA Afrique de l''Ouest (CEDEAO).',
 '["CI","GH","SN","BJ","BF","CV","GM","GN","GW","LR","ML","MR","NE","NG","SL","TG"]'),
('CEMAC', 'EPA CEMAC (Afrique centrale)',
 'Cumul régional entre les États de l''EPA Afrique centrale.',
 '["CM","GA","CG","TD","CF","GQ"]'),
('INDIAN_OCEAN', 'EPA Océan Indien (COMESA/COI)',
 'Cumul régional entre les États de l''EPA océan Indien.',
 '["MG","MU","SC","KM"]'),
('ANDEAN', 'Communauté andine',
 'Cumul régional de l''accord UE-Andes (Colombie, Pérou, Équateur).',
 '["CO","PE","EC","BO"]'),
('CENTRAL_AMERICA', 'Amérique centrale',
 'Cumul régional de l''accord UE-Amérique centrale.',
 '["HN","GT","SV","NI","CR","PA"]'),
('CARICOM', 'CARICOM / OECS (EPA)',
 'Cumul régional de l''EPA UE-CARICOM.',
 '["JM","TT","BB","BZ","GD","GY","SR","AG","BS","DM","KN","LC","VC"]'),
('WESTERN_BALKANS', 'Balkans occidentaux (SAA)',
 'Cumul entre les partenaires des accords de stabilisation et d''association.',
 '["AL","ME","MK","RS","BA","XK"]'),
('EASTERN_PARTNERSHIP', 'Partenariat oriental (DCFTA)',
 'Cumul diagonal entre l''Ukraine, la Moldavie et la Géorgie (DCFTA).',
 '["UA","MD","GE"]')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. Étendre trade_agreements avec le modèle de cumul structuré
--    cumulation_type : NONE | BILATERAL | DIAGONAL | FULL
-- ============================================================
ALTER TABLE trade_agreements
    ADD COLUMN IF NOT EXISTS cumulation_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS cumulation_group_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS allows_rollup BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS va_threshold_pct DOUBLE PRECISION;

-- --- Cumul bilatéral (matières originaires UE cumulables) ---
UPDATE trade_agreements
SET cumulation_type = 'BILATERAL', va_threshold_pct = 35
WHERE code IN ('EVFTA','CETA','EUEJPA','EUJEPA','EUKORF','EUMAP','EUTNPA','EUCU','EUGB',
               'EUSGP','EUSINGAPORE','EUMEXICO','EUMEX2','EUCHILE','EUCHILE2','EUNZ','EUAUS','EUMAR','EUTUN');

-- --- Cumul diagonal régional (groupes) ---
UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'SADC', va_threshold_pct = 35
WHERE code IN ('EUGOA','EUSADC');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'EAC', va_threshold_pct = 35
WHERE code IN ('EUKEN','EUTZ');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'WA-EPA', va_threshold_pct = 35
WHERE code IN ('EUCIV','EUGHA','EUSN');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'INDIAN_OCEAN', va_threshold_pct = 35
WHERE code IN ('EUMG','EUMU','EUSC');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'CEMAC', va_threshold_pct = 35
WHERE code IN ('EUCM','EUGA');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'ANDEAN', va_threshold_pct = 35
WHERE code = 'EUCOLPEE';

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'CENTRAL_AMERICA', va_threshold_pct = 35
WHERE code = 'EUCAM';

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'CARICOM', va_threshold_pct = 35
WHERE code = 'EUCARIFOR';

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'ASEAN', va_threshold_pct = 35
WHERE code IN ('EUASEAN');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'WESTERN_BALKANS', va_threshold_pct = 35
WHERE code IN ('EUAL','EUMNE','EUMKD','EUSRB');

UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'EASTERN_PARTNERSHIP', va_threshold_pct = 35
WHERE code IN ('EUUKR','EUMDA','EUGE','AEUM','AEUMD','AEUGE');

-- --- Cumul pan-euro-méditerranéen (diagonal + roll-up) ---
UPDATE trade_agreements
SET cumulation_type = 'DIAGONAL', cumulation_group_code = 'PANEUROMED', allows_rollup = TRUE, va_threshold_pct = 35
WHERE code IN ('EULBN','EUISR','EUPSE','EUMAP','EUTNPA');

-- --- Roll-up explicite (le statut originaire est conservé lors d'ouvraisons ultérieures) ---
UPDATE trade_agreements
SET allows_rollup = TRUE
WHERE code IN ('EUCHILE2','EUMEX2');
