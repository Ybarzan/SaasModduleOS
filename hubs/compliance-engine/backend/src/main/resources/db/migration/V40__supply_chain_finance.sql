-- V40__supply_chain_finance.sql
-- P4.25: Supply Chain Finance

CREATE TABLE IF NOT EXISTS invoice_financing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES client_invoices(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    requested_amount DECIMAL(15,2) NOT NULL,
    finance_amount DECIMAL(15,2),
    fee_amount DECIMAL(15,2),
    fee_percent DECIMAL(5,2) DEFAULT 2.50,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    funded_at TIMESTAMP,
    repayment_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_financing_company ON invoice_financing(company_id);
CREATE INDEX idx_invoice_financing_invoice ON invoice_financing(invoice_id);
CREATE INDEX idx_invoice_financing_status ON invoice_financing(status);
