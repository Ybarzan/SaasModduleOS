-- CSRD / EU Taxonomy reporting
CREATE TABLE csrd_reports (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    report_period VARCHAR(20) NOT NULL,
    total_emissions DECIMAL(15, 2),
    scope1 DECIMAL(15, 2),
    scope2 DECIMAL(15, 2),
    scope3 DECIMAL(15, 2),
    offset_credits DECIMAL(15, 2),
    net_emissions DECIMAL(15, 2),
    report_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE csrd_emission_factors (
    id UUID PRIMARY KEY,
    transport_mode VARCHAR(50) NOT NULL,
    emission_factor_kg_co2_per_km DECIMAL(12, 6) NOT NULL,
    source VARCHAR(200),
    valid_from DATE NOT NULL,
    valid_to DATE
);

CREATE INDEX idx_csrd_reports_company ON csrd_reports(company_id);
CREATE INDEX idx_csrd_reports_period ON csrd_reports(report_period);
CREATE INDEX idx_csrd_emission_factors_mode ON csrd_emission_factors(transport_mode);

COMMENT ON TABLE csrd_reports IS 'CSRD/EU Taxonomy sustainability reports per company and reporting period';
COMMENT ON TABLE csrd_emission_factors IS 'Emission factors by transport mode for CSRD scope 3 category 4 (upstream transportation)';
