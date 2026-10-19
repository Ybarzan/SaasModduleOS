-- V4__client_invoices.sql
-- client_id : UUID brut, sans FK -- le compte client (ClientUser) reste côté
-- compliance-engine depuis l'extraction Finance & Trésorerie.

CREATE TABLE IF NOT EXISTS client_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    client_id UUID,
    payment_term_id UUID REFERENCES payment_terms(id) ON DELETE SET NULL,
    invoice_number VARCHAR(100) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    client_name VARCHAR(200),
    client_email VARCHAR(200),
    subtotal DECIMAL(15,2) NOT NULL,
    vat_amount DECIMAL(15,2) DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,
    amount_paid DECIMAL(15,2) DEFAULT 0,
    balance_due DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    early_payment_discount_amount DECIMAL(15,2),
    early_payment_discount_deadline DATE,
    late_fee_applied DECIMAL(15,2) DEFAULT 0,
    payment_reference VARCHAR(200),
    paid_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_client_invoice_company ON client_invoices(company_id);
CREATE INDEX idx_client_invoice_status ON client_invoices(status);
CREATE INDEX idx_client_invoice_due_date ON client_invoices(due_date);
CREATE INDEX idx_client_invoice_client ON client_invoices(client_id);
