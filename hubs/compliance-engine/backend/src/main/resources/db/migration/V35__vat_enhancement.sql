-- V35__vat_enhancement.sql
-- VAT rates table with all 27 EU countries, margin scheme support

-- ============================================================
-- VAT Rates table
-- ============================================================
CREATE TABLE IF NOT EXISTS vat_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code VARCHAR(2) NOT NULL,
    rate_type VARCHAR(20) NOT NULL,
    rate DOUBLE PRECISION NOT NULL,
    description VARCHAR(200),
    hs_chapters VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT true,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    margin_rate DOUBLE PRECISION,
    applies_to VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(country_code, rate_type, valid_from)
);
CREATE INDEX IF NOT EXISTS idx_vat_country ON vat_rates(country_code);

-- ============================================================
-- Standard VAT rates — All 27 EU countries (2024/2025)
-- ============================================================
INSERT INTO vat_rates (country_code, rate_type, rate, description, applies_to) VALUES
('FR', 'STANDARD', 20.0, 'TVA standard France', 'BOTH'),
('DE', 'STANDARD', 19.0, 'Umsatzsteuer standard Deutschland', 'BOTH'),
('IT', 'STANDARD', 22.0, 'IVA standard Italia', 'BOTH'),
('ES', 'STANDARD', 21.0, 'IVA standard Espana', 'BOTH'),
('PT', 'STANDARD', 23.0, 'IVA standard Portugal', 'BOTH'),
('NL', 'STANDARD', 21.0, 'BTW standard Nederland', 'BOTH'),
('BE', 'STANDARD', 21.0, 'TVA standard Belgique', 'BOTH'),
('LU', 'STANDARD', 17.0, 'TVA standard Luxembourg', 'BOTH'),
('AT', 'STANDARD', 20.0, 'USt standard Oesterreich', 'BOTH'),
('FI', 'STANDARD', 24.0, 'ALV standard Suomi', 'BOTH'),
('SE', 'STANDARD', 25.0, 'Moms standard Sverige', 'BOTH'),
('DK', 'STANDARD', 25.0, 'Moms standard Danmark', 'BOTH'),
('IE', 'STANDARD', 23.0, 'VAT standard Ireland', 'BOTH'),
('GR', 'STANDARD', 24.0, 'FPA standard Ellada', 'BOTH'),
('PL', 'STANDARD', 23.0, 'VAT standard Polska', 'BOTH'),
('CZ', 'STANDARD', 21.0, 'DPH standard Cesko', 'BOTH'),
('SK', 'STANDARD', 20.0, 'DPH standard Slovensko', 'BOTH'),
('HU', 'STANDARD', 27.0, 'AFA standard Magyarorszag', 'BOTH'),
('RO', 'STANDARD', 19.0, 'TVA standard Romania', 'BOTH'),
('BG', 'STANDARD', 20.0, 'DDS standard Balgariya', 'BOTH'),
('HR', 'STANDARD', 25.0, 'PDV standard Hrvatska', 'BOTH'),
('SI', 'STANDARD', 22.0, 'DDV standard Slovenija', 'BOTH'),
('EE', 'STANDARD', 22.0, 'KBM standard Eesti', 'BOTH'),
('LV', 'STANDARD', 21.0, 'PVN standard Latvija', 'BOTH'),
('LT', 'STANDARD', 21.0, 'PVM standard Lietuva', 'BOTH'),
('CY', 'STANDARD', 19.0, 'FPA standard Kypros', 'BOTH'),
('MT', 'STANDARD', 18.0, 'VAT standard Malta', 'BOTH')
ON CONFLICT (country_code, rate_type, valid_from) DO NOTHING;

-- ============================================================
-- Reduced VAT rates — Countries with reduced rates
-- ============================================================
INSERT INTO vat_rates (country_code, rate_type, rate, description, applies_to) VALUES
-- France: 5.5% (general reduced), 10.0% (intermediate)
('FR', 'REDUCED', 5.5, 'TVA reduced generale France', 'GOODS'),
('FR', 'REDUCED', 10.0, 'TVA reduced intermediaire France', 'GOODS'),
-- Germany: 7.0%
('DE', 'REDUCED', 7.0, 'Umsatzsteuer erm Deutschland', 'GOODS'),
-- Italy: 4.0%, 5.0%, 10.0%
('IT', 'REDUCED', 4.0, 'IVA ridotta Italia', 'GOODS'),
('IT', 'REDUCED', 5.0, 'IVA ridotta Italia', 'GOODS'),
('IT', 'REDUCED', 10.0, 'IVA ridotta Italia', 'GOODS'),
-- Spain: 4.0%, 10.0%
('ES', 'REDUCED', 4.0, 'IVA reducida Espana', 'GOODS'),
('ES', 'REDUCED', 10.0, 'IVA reducida Espana', 'GOODS'),
-- Portugal: 6.0%, 13.0%
('PT', 'REDUCED', 6.0, 'IVA reduzido Portugal', 'GOODS'),
('PT', 'REDUCED', 13.0, 'IVA reduzido Portugal', 'GOODS'),
-- Netherlands: 9.0%
('NL', 'REDUCED', 9.0, 'BTW verlaagd Nederland', 'GOODS'),
-- Belgium: 6.0%, 12.0%
('BE', 'REDUCED', 6.0, 'TVA reduced Belgique', 'GOODS'),
('BE', 'REDUCED', 12.0, 'TVA reduced Belgique', 'GOODS'),
-- Luxembourg: 3.0%, 8.0%
('LU', 'REDUCED', 3.0, 'TVA reduite Luxembourg', 'GOODS'),
('LU', 'REDUCED', 8.0, 'TVA reduite Luxembourg', 'GOODS'),
-- Austria: 10.0%, 13.0%
('AT', 'REDUCED', 10.0, 'USt erm Oesterreich', 'GOODS'),
('AT', 'REDUCED', 13.0, 'USt erm Oesterreich', 'GOODS'),
-- Finland: 10.0%, 14.0%
('FI', 'REDUCED', 10.0, 'ALV alennettu Suomi', 'GOODS'),
('FI', 'REDUCED', 14.0, 'ALV alennettu Suomi', 'GOODS'),
-- Sweden: 6.0%, 12.0%
('SE', 'REDUCED', 6.0, 'Moms reducerad Sverige', 'GOODS'),
('SE', 'REDUCED', 12.0, 'Moms reducerad Sverige', 'GOODS'),
-- Denmark: no reduced rate (single rate)
-- Ireland: 9.0%, 13.5%
('IE', 'REDUCED', 9.0, 'VAT reduced Ireland', 'GOODS'),
('IE', 'REDUCED', 13.5, 'VAT reduced Ireland', 'GOODS'),
-- Greece: 6.0%, 13.0%
('GR', 'REDUCED', 6.0, 'FPA reduced Ellada', 'GOODS'),
('GR', 'REDUCED', 13.0, 'FPA reduced Ellada', 'GOODS'),
-- Poland: 5.0%, 8.0%
('PL', 'REDUCED', 5.0, 'VAT obnizony Polska', 'GOODS'),
('PL', 'REDUCED', 8.0, 'VAT obnizony Polska', 'GOODS'),
-- Czech Republic: 10.0%, 15.0%
('CZ', 'REDUCED', 10.0, 'DPH snizena Cesko', 'GOODS'),
('CZ', 'REDUCED', 15.0, 'DPH snizena Cesko', 'GOODS'),
-- Slovakia: 10.0%
('SK', 'REDUCED', 10.0, 'DPH znizena Slovensko', 'GOODS'),
-- Hungary: 5.0%, 18.0%
('HU', 'REDUCED', 5.0, 'AFA csokkentett Magyarorszag', 'GOODS'),
('HU', 'REDUCED', 18.0, 'AFA csokkentett Magyarorszag', 'GOODS'),
-- Romania: 5.0%, 9.0%
('RO', 'REDUCED', 5.0, 'TVA redus Romania', 'GOODS'),
('RO', 'REDUCED', 9.0, 'TVA redus Romania', 'GOODS'),
-- Bulgaria: 9.0%
('BG', 'REDUCED', 9.0, 'DDS namalen Balgariya', 'GOODS'),
-- Croatia: 5.0%, 13.0%
('HR', 'REDUCED', 5.0, 'PDV snizen Hrvatska', 'GOODS'),
('HR', 'REDUCED', 13.0, 'PDV snizen Hrvatska', 'GOODS'),
-- Slovenia: 5.0%, 9.5%
('SI', 'REDUCED', 5.0, 'DDV zmanjsan Slovenija', 'GOODS'),
('SI', 'REDUCED', 9.5, 'DDV zmanjsan Slovenija', 'GOODS'),
-- Estonia: 9.0%
('EE', 'REDUCED', 9.0, 'KBM langetatud Eesti', 'GOODS'),
-- Latvia: 5.0%, 12.0%
('LV', 'REDUCED', 5.0, 'PVN samazinats Latvija', 'GOODS'),
('LV', 'REDUCED', 12.0, 'PVN samazinats Latvija', 'GOODS'),
-- Lithuania: 5.0%, 9.0%
('LT', 'REDUCED', 5.0, 'PVM sumazintas Lietuva', 'GOODS'),
('LT', 'REDUCED', 9.0, 'PVM sumazintas Lietuva', 'GOODS'),
-- Cyprus: 5.0%, 9.0%
('CY', 'REDUCED', 5.0, 'FPA reduced Kypros', 'GOODS'),
('CY', 'REDUCED', 9.0, 'FPA reduced Kypros', 'GOODS'),
-- Malta: 5.0%, 7.0%
('MT', 'REDUCED', 5.0, 'VAT reduced Malta', 'GOODS'),
('MT', 'REDUCED', 7.0, 'VAT reduced Malta', 'GOODS')
ON CONFLICT (country_code, rate_type, valid_from) DO NOTHING;

-- ============================================================
-- Super-reduced VAT rates
-- ============================================================
INSERT INTO vat_rates (country_code, rate_type, rate, description, applies_to) VALUES
('FR', 'SUPER_REDUCED', 2.1, 'TVA super-reduite France (medicaments, livres)', 'GOODS')
ON CONFLICT (country_code, rate_type, valid_from) DO NOTHING;

-- ============================================================
-- Zero rates
-- ============================================================
INSERT INTO vat_rates (country_code, rate_type, rate, description, applies_to) VALUES
('IE', 'ZERO', 0.0, 'VAT zero-rated Ireland', 'GOODS'),
('DK', 'ZERO', 0.0, 'Moms nul Danmark (udvalgte varer)', 'GOODS'),
('CY', 'ZERO', 0.0, 'FPA zero Kypros (certain goods)', 'GOODS'),
('MT', 'ZERO', 0.0, 'VAT zero Malta (certain goods)', 'GOODS'),
('LU', 'ZERO', 0.0, 'TVA zero Luxembourg (certain goods)', 'GOODS'),
('PL', 'ZERO', 0.0, 'VAT zero Polska (certain goods)', 'GOODS'),
('SE', 'ZERO', 0.0, 'Moms zero Sverige (certain goods)', 'GOODS'),
('FI', 'ZERO', 0.0, 'ALV zero Suomi (certain goods)', 'GOODS'),
('AT', 'ZERO', 0.0, 'USt zero Oesterreich (certain goods)', 'GOODS'),
('BE', 'ZERO', 0.0, 'TVA zero Belgique (certain goods)', 'GOODS'),
('NL', 'ZERO', 0.0, 'BTW zero Nederland (bepaalde goederen)', 'GOODS'),
('DE', 'ZERO', 0.0, 'Umsatzsteuer zero Deutschland', 'GOODS'),
('IT', 'ZERO', 0.0, 'IVA zero Italia (determinati beni)', 'GOODS'),
('ES', 'ZERO', 0.0, 'IVA zero Espana (determinados bienes)', 'GOODS'),
('PT', 'ZERO', 0.0, 'IVA zero Portugal (determinados bens)', 'GOODS'),
('GR', 'ZERO', 0.0, 'FPA zero Ellada', 'GOODS'),
('CZ', 'ZERO', 0.0, 'DPH zero Cesko', 'GOODS'),
('SK', 'ZERO', 0.0, 'DPH zero Slovensko', 'GOODS'),
('HU', 'ZERO', 0.0, 'AFA zero Magyarorszag', 'GOODS'),
('RO', 'ZERO', 0.0, 'TVA zero Romania', 'GOODS'),
('BG', 'ZERO', 0.0, 'DDS zero Balgariya', 'GOODS'),
('HR', 'ZERO', 0.0, 'PDV zero Hrvatska', 'GOODS'),
('SI', 'ZERO', 0.0, 'DDV zero Slovenija', 'GOODS'),
('EE', 'ZERO', 0.0, 'KBM zero Eesti', 'GOODS'),
('LV', 'ZERO', 0.0, 'PVN zero Latvija', 'GOODS'),
('LT', 'ZERO', 0.0, 'PVM zero Lietuva', 'GOODS')
ON CONFLICT (country_code, rate_type, valid_from) DO NOTHING;

-- ============================================================
-- Margin scheme entries (for second-hand goods, art, antiques)
-- ============================================================
INSERT INTO vat_rates (country_code, rate_type, rate, description, margin_rate, applies_to) VALUES
('FR', 'MARGIN', 20.0, 'Regime de la marge — biens d''occasion France', 20.0, 'GOODS'),
('DE', 'MARGIN', 19.0, 'Differenzbesteuerung — gebrauchte Gegenstaende Deutschland', 19.0, 'GOODS'),
('IT', 'MARGIN', 22.0, 'Regime del margine — beni usati Italia', 22.0, 'GOODS'),
('ES', 'MARGIN', 21.0, 'Regimen del margen — bienes usados Espana', 21.0, 'GOODS'),
('NL', 'MARGIN', 21.0, 'Margeregeling — gebruikte goederen Nederland', 21.0, 'GOODS'),
('BE', 'MARGIN', 21.0, 'Regime de la marge — biens d''occasion Belgique', 21.0, 'GOODS'),
('AT', 'MARGIN', 20.0, 'Differenzbesteuerung — gebrauchte Gegenstaende Oesterreich', 20.0, 'GOODS'),
('PT', 'MARGIN', 23.0, 'Regime da margem — bens usados Portugal', 23.0, 'GOODS'),
('PL', 'MARGIN', 23.0, 'Marza — towary uzywane Polska', 23.0, 'GOODS'),
('SE', 'MARGIN', 25.0, 'Marginskatt — begagnade varor Sverige', 25.0, 'GOODS'),
('DK', 'MARGIN', 25.0, 'Avanceordningen — brugte varer Danmark', 25.0, 'GOODS'),
('IE', 'MARGIN', 23.0, 'Margin scheme — second-hand goods Ireland', 23.0, 'GOODS'),
('FI', 'MARGIN', 24.0, 'Marginaaliverojärjestelmä — käytetyt tavarat Suomi', 24.0, 'GOODS'),
('GR', 'MARGIN', 24.0, 'Katharismenos foros — methisea proionta Ellada', 24.0, 'GOODS'),
('CZ', 'MARGIN', 21.0, 'Režim marže — použité zboží Cesko', 21.0, 'GOODS'),
('HU', 'MARGIN', 27.0, 'Árkülönbség-adó — használt cikkek Magyarorszag', 27.0, 'GOODS'),
('RO', 'MARGIN', 19.0, 'TVA la marja — bunuri second-hand Romania', 19.0, 'GOODS'),
('BG', 'MARGIN', 20.0, 'DDS varhu marja — polzvani stoki Balgariya', 20.0, 'GOODS'),
('SK', 'MARGIN', 20.0, 'Režim marže — použitý tovar Slovensko', 20.0, 'GOODS'),
('LU', 'MARGIN', 17.0, 'Regime de la marge — biens d''occasion Luxembourg', 17.0, 'GOODS'),
('HR', 'MARGIN', 25.0, 'PDV na maržu — rabljena roba Hrvatska', 25.0, 'GOODS'),
('SI', 'MARGIN', 22.0, 'DDV po marži — rabljeni izdelki Slovenija', 22.0, 'GOODS'),
('EE', 'MARGIN', 22.0, 'KBM marginaalile — kasutatud kaup Eesti', 22.0, 'GOODS'),
('LV', 'MARGIN', 21.0, 'PVN pei maržas — lietotas preces Latvija', 21.0, 'GOODS'),
('LT', 'MARGIN', 21.0, 'PVM pagal maržą — naudotos prekės Lietuva', 21.0, 'GOODS'),
('CY', 'MARGIN', 19.0, 'FPA kat''arithmosi — deftera katalimata Kypros', 19.0, 'GOODS'),
('MT', 'MARGIN', 18.0, 'VAT on margin — second-hand goods Malta', 18.0, 'GOODS')
ON CONFLICT (country_code, rate_type, valid_from) DO NOTHING;
