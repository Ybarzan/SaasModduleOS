-- V20__p4_approvals_carbon_finance.sql
-- P4: Approval Workflows, Carbon Offsets, Payment Terms, Client Invoices

-- ============================================================
-- Approval Workflows
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    entity_type VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    threshold_amount DECIMAL(15,2),
    threshold_currency VARCHAR(3) DEFAULT 'EUR',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_workflow_company ON approval_workflows(company_id);
CREATE INDEX idx_approval_workflow_entity_type ON approval_workflows(entity_type);

-- ============================================================
-- Approval Steps
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES approval_workflows(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    approver_role VARCHAR(20),
    approver_user_id UUID,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_steps_workflow ON approval_steps(workflow_id);

-- ============================================================
-- Approval Requests
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    workflow_id UUID REFERENCES approval_workflows(id) ON DELETE SET NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    entity_reference VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by_user_id UUID NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    amount DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    current_step INTEGER DEFAULT 1,
    total_steps INTEGER DEFAULT 1,
    notes TEXT,
    decision_notes TEXT,
    decided_by_user_id UUID,
    decided_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_request_company ON approval_requests(company_id);
CREATE INDEX idx_approval_request_status ON approval_requests(status);
CREATE INDEX idx_approval_request_entity ON approval_requests(entity_type, entity_id);
CREATE INDEX idx_approval_request_requestor ON approval_requests(requested_by_user_id);

-- ============================================================
-- Approval History
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    step_order INTEGER,
    step_name VARCHAR(200),
    action VARCHAR(20) NOT NULL,
    performed_by_user_id UUID NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_history_request ON approval_history(request_id);

-- ============================================================
-- Carbon Offsets
-- ============================================================
CREATE TABLE IF NOT EXISTS carbon_offsets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    co2_emissions_kg DECIMAL(12,2) NOT NULL,
    offset_credits_purchased DECIMAL(12,2) DEFAULT 0,
    offset_credits_retired DECIMAL(12,2) DEFAULT 0,
    offset_provider VARCHAR(100),
    offset_project_name VARCHAR(200),
    offset_project_type VARCHAR(50),
    offset_cost_per_ton DECIMAL(10,2),
    offset_total_cost DECIMAL(15,2),
    offset_currency VARCHAR(3) DEFAULT 'EUR',
    certification_id VARCHAR(100),
    retired_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'TRACKING',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_carbon_offset_company ON carbon_offsets(company_id);
CREATE INDEX idx_carbon_offset_shipment ON carbon_offsets(shipment_id);
CREATE INDEX idx_carbon_offset_status ON carbon_offsets(status);

-- ============================================================
-- Payment Terms
-- ============================================================
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

-- ============================================================
-- Client Invoices
-- ============================================================
CREATE TABLE IF NOT EXISTS client_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    client_user_id UUID REFERENCES client_users(id) ON DELETE SET NULL,
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
CREATE INDEX idx_client_invoice_client ON client_invoices(client_user_id);
