CREATE TABLE admin_ip_allowlist (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    label VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_admin_ip UNIQUE (ip_address)
);
