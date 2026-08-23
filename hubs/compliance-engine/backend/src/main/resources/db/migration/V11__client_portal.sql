CREATE TABLE client_users (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    phone           VARCHAR(50),
    is_active       BOOLEAN DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_client_email_company UNIQUE (company_id, email)
);

CREATE TABLE shared_links (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id     UUID NOT NULL REFERENCES shipment_orders(id) ON DELETE CASCADE,
    token           VARCHAR(36) NOT NULL UNIQUE,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    label           VARCHAR(255),
    expires_at      TIMESTAMP,
    is_active       BOOLEAN DEFAULT TRUE,
    access_count    INTEGER DEFAULT 0,
    last_accessed_at TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_users_company ON client_users(company_id);
CREATE INDEX idx_client_users_email ON client_users(email);
CREATE INDEX idx_shared_links_token ON shared_links(token);
CREATE INDEX idx_shared_links_company ON shared_links(company_id);
CREATE INDEX idx_shared_links_shipment ON shared_links(shipment_id);
