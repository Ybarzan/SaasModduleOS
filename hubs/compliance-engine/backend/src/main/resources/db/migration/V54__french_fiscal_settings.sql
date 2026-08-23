-- Ajoute les colonnes de paramétrage TVA / DEB / Intrastat manquantes,
-- nécessaires à la page de configuration fiscale française (FrenchFiscal.tsx).
ALTER TABLE french_fiscal_config
    ADD COLUMN vat_rate NUMERIC(5,2) DEFAULT 20,
    ADD COLUMN vat_number VARCHAR(30),
    ADD COLUMN intra_eu_scheme VARCHAR(20) DEFAULT 'normal',
    ADD COLUMN deb_frequency VARCHAR(20) DEFAULT 'monthly',
    ADD COLUMN deb_threshold NUMERIC(15,2) DEFAULT 460000,
    ADD COLUMN intrastat_dispatch_threshold NUMERIC(15,2) DEFAULT 460000,
    ADD COLUMN intrastat_arrival_threshold NUMERIC(15,2) DEFAULT 460000,
    ADD COLUMN intrastat_declaration_type VARCHAR(20) DEFAULT 'simplified';

CREATE UNIQUE INDEX idx_french_fiscal_company_unique ON french_fiscal_config(company_id);
