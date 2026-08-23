CREATE TABLE IF NOT EXISTS eta_model_coefficients (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    feature_name VARCHAR(50) NOT NULL,
    feature_value VARCHAR(100) NOT NULL,
    coefficient DECIMAL(12,6) NOT NULL DEFAULT 0,
    samples_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    trained_at TIMESTAMP,
    intercept DECIMAL(12,6) NOT NULL DEFAULT 0,
    r_squared DECIMAL(8,6),
    CONSTRAINT uq_eta_model_feature UNIQUE (company_id, feature_name, feature_value)
);

CREATE TABLE IF NOT EXISTS taric_sync_log (
    id UUID PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    hs_codes_processed INT NOT NULL DEFAULT 0,
    rates_upserted INT NOT NULL DEFAULT 0,
    rates_failed INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_eta_model_active ON eta_model_coefficients(company_id, is_active);
CREATE INDEX IF NOT EXISTS idx_taric_sync_status ON taric_sync_log(status);
