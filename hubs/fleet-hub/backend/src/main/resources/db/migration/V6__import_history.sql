CREATE TABLE import_history (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES company(id),
    file_type VARCHAR(20) NOT NULL,
    file_name VARCHAR(255),
    rows_read INT NOT NULL DEFAULT 0,
    rows_imported INT NOT NULL DEFAULT 0,
    rows_skipped INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    imported_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_import_history_company FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE INDEX idx_import_history_company ON import_history(company_id, imported_at DESC);
