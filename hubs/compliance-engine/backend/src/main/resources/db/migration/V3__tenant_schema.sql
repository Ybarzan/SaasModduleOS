-- ============================================================
-- V3 — Multi-Tenant: FK + Roles
-- ============================================================

-- Add company_id to users
ALTER TABLE users ADD COLUMN company_id UUID;
ALTER TABLE users ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- Add company_id to simulations (aggregated per tenant)
ALTER TABLE simulations ADD COLUMN company_id UUID;
ALTER TABLE simulations ADD CONSTRAINT fk_simulations_company FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_simulations_company ON simulations(company_id);

-- Add company_id to api_keys
ALTER TABLE api_keys ADD COLUMN company_id UUID;
ALTER TABLE api_keys ADD CONSTRAINT fk_api_keys_company FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_api_keys_company ON api_keys(company_id);

-- Company roles
CREATE TABLE IF NOT EXISTS company_roles (
    id          UUID PRIMARY KEY,
    company_id  UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    role        VARCHAR(20)  NOT NULL,  -- OWNER, ADMIN, MANAGER, USER
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(company_id, user_id)
);

CREATE INDEX idx_company_roles_company ON company_roles(company_id);
CREATE INDEX idx_company_roles_user ON company_roles(user_id);

-- Drop legacy company column (renamed to companyName on entity)
ALTER TABLE users DROP COLUMN IF EXISTS company;
