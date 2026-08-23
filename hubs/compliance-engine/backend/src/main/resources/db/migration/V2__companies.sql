-- ============================================================
-- V2 — Multi-Tenant: Companies
-- ============================================================

CREATE TABLE IF NOT EXISTS companies (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(100) NOT NULL UNIQUE,
    logo_url            VARCHAR(500),
    plan                VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    stripe_customer_id  VARCHAR(100),
    settings            VARCHAR(2000) NOT NULL DEFAULT '{}',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_companies_slug ON companies(slug);
