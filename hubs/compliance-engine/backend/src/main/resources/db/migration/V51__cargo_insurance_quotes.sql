-- V51__cargo_insurance_quotes.sql
-- Assurance cargo : persistance des devis et souscription de polices

CREATE TABLE IF NOT EXISTS cargo_insurance_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    goods_value DECIMAL(15,2) NOT NULL,
    weight_kg DECIMAL(12,2),
    transport_mode VARCHAR(10),
    goods_category VARCHAR(30),
    origin_country VARCHAR(3),
    destination_country VARCHAR(3),
    currency VARCHAR(3) DEFAULT 'EUR',
    premium_rate DECIMAL(10,6) NOT NULL,
    premium_amount DECIMAL(15,2) NOT NULL,
    coverage_amount DECIMAL(15,2) NOT NULL,
    coverage_type VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'QUOTE',
    policy_number VARCHAR(40),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cargo_insurance_quotes_company ON cargo_insurance_quotes(company_id);
CREATE INDEX idx_cargo_insurance_quotes_status ON cargo_insurance_quotes(status);
