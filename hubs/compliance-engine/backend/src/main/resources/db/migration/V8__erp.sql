CREATE TABLE erp_configs (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    erp_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    api_endpoint VARCHAR(500),
    api_key VARCHAR(500),
    api_secret VARCHAR(500),
    database_name VARCHAR(255),
    username VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    sync_status VARCHAR(20) DEFAULT 'IDLE',
    last_error TEXT,
    config_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE erp_sync_logs (
    id UUID PRIMARY KEY,
    erp_config_id UUID NOT NULL,
    company_id UUID NOT NULL,
    sync_type VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    records_total INTEGER DEFAULT 0,
    records_synced INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (erp_config_id) REFERENCES erp_configs(id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE INDEX idx_erp_config_company ON erp_configs(company_id);
CREATE INDEX idx_erp_sync_logs_company ON erp_sync_logs(company_id);
CREATE INDEX idx_erp_sync_logs_config ON erp_sync_logs(erp_config_id);
