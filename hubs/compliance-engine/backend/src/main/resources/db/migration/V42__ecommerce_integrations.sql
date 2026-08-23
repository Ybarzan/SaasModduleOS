-- V42__ecommerce_integrations.sql
-- P3.19: Intégrations e-commerce (Shopify, WooCommerce, PrestaShop)

CREATE TABLE ecommerce_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    store_url VARCHAR(300),
    api_key VARCHAR(500),
    api_secret TEXT,
    webhook_secret TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    sync_frequency_min INT NOT NULL DEFAULT 60,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE ecommerce_sync_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    orders_processed INT DEFAULT 0,
    orders_created INT DEFAULT 0,
    orders_failed INT DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_ecommerce_integrations_company ON ecommerce_integrations(company_id);
CREATE INDEX idx_ecommerce_integrations_platform_company ON ecommerce_integrations(platform, company_id);
CREATE INDEX idx_ecommerce_integrations_active ON ecommerce_integrations(company_id, is_active);
CREATE INDEX idx_ecommerce_sync_logs_integration ON ecommerce_sync_logs(integration_id);
CREATE INDEX idx_ecommerce_sync_logs_started ON ecommerce_sync_logs(started_at DESC);
