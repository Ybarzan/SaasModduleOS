-- ============================================================
-- V41 — P4.23 Multi-branch / Filiales
-- ============================================================

CREATE TABLE company_branches (
    id UUID PRIMARY KEY,
    parent_company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    branch_company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    branch_name VARCHAR(200) NOT NULL,
    branch_code VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    consolidation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_branches_parent ON company_branches(parent_company_id);
CREATE INDEX idx_company_branches_branch ON company_branches(branch_company_id);
CREATE UNIQUE INDEX idx_company_branches_parent_branch ON company_branches(parent_company_id, branch_company_id);

CREATE TABLE inter_branch_transfers (
    id UUID PRIMARY KEY,
    from_branch_id UUID NOT NULL,
    to_branch_id UUID NOT NULL,
    goods_description TEXT,
    quantity DECIMAL(15, 2) NOT NULL,
    unit VARCHAR(50) NOT NULL DEFAULT 'UNIT',
    reference_number VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    initiated_by UUID,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inter_branch_transfers_from ON inter_branch_transfers(from_branch_id);
CREATE INDEX idx_inter_branch_transfers_to ON inter_branch_transfers(to_branch_id);
CREATE INDEX idx_inter_branch_transfers_status ON inter_branch_transfers(status);
