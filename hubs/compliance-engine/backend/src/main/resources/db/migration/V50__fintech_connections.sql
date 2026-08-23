-- V50__fintech_connections.sql
-- P4.25: Intégrations fintech (Qonto, Spendesk) — connexions bancaires & dépenses

CREATE TABLE IF NOT EXISTS fintech_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    api_key VARCHAR(255),
    api_secret VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fintech_connections_company ON fintech_connections(company_id);
CREATE INDEX idx_fintech_connections_provider ON fintech_connections(provider);
