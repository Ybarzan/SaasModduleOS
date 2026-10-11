-- V2__warehouses.sql
-- Entrepots. company_id est une reference logique (pas de FK) vers la societe
-- proprietaire, au meme titre que dans compliance-engine d'origine.

CREATE TABLE IF NOT EXISTS warehouses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    branch_id UUID,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_warehouses_company ON warehouses(company_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_branch ON warehouses(branch_id);
