-- V3__payment_terms.sql

CREATE TABLE IF NOT EXISTS payment_terms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(30) NOT NULL,
    description TEXT,
    days_until_due INTEGER NOT NULL,
    early_payment_discount_percent DECIMAL(5,2) DEFAULT 0,
    early_payment_discount_days INTEGER DEFAULT 0,
    late_fee_percent DECIMAL(5,2) DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payment_terms_company ON payment_terms(company_id);
CREATE INDEX idx_payment_terms_code ON payment_terms(code);
