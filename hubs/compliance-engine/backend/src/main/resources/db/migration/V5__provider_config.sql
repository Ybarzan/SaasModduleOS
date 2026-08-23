-- ============================================================
-- V5 — Phase 2: External API Provider Configuration
-- ============================================================

CREATE TABLE provider_configs (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    api_key_encrypted VARCHAR(500),
    api_secret VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    config_json TEXT,
    last_health_check TIMESTAMP,
    health_status VARCHAR(20) DEFAULT 'UNKNOWN',
    consecutive_failures INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE provider_rate_cache (
    id UUID PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL,
    cache_key VARCHAR(255) NOT NULL UNIQUE,
    response_json TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_provider_config_company ON provider_configs(company_id);
CREATE INDEX idx_rate_cache_key ON provider_rate_cache(cache_key);
CREATE INDEX idx_rate_cache_expires ON provider_rate_cache(expires_at);
