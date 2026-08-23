-- ============================================================
-- IncoKalk — Schema initial
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255),
    company     VARCHAR(255),
    plan        VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_keys (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL,
    key_hash    VARCHAR(255) NOT NULL,
    key_prefix  VARCHAR(30)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    plan        VARCHAR(20)  NOT NULL,
    daily_limit INTEGER      NOT NULL DEFAULT 10,
    calls_today INTEGER      NOT NULL DEFAULT 0,
    total_calls INTEGER      NOT NULL DEFAULT 0,
    last_used   TIMESTAMP,
    expires_at  TIMESTAMP,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS simulations (
    id                   UUID PRIMARY KEY,
    user_id              UUID,
    incoterm_code        VARCHAR(5)   NOT NULL,
    origin_country       VARCHAR(3)   NOT NULL,
    destination_country  VARCHAR(3)   NOT NULL,
    goods_value          NUMERIC(15,2) NOT NULL,
    currency             VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    transport_mode       VARCHAR(20),
    hs_code              VARCHAR(10),
    total_buyer_cost     NUMERIC(15,2),
    result_json          VARCHAR(4000),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS customs_rates (
    id                  SERIAL PRIMARY KEY,
    hs_code             VARCHAR(10)  NOT NULL,
    origin_country      VARCHAR(3)   NOT NULL,
    destination_country VARCHAR(3)   NOT NULL,
    duty_rate           NUMERIC(6,4) NOT NULL,
    preferential_rate   NUMERIC(6,4),
    agreement_name      VARCHAR(100),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(hs_code, origin_country, destination_country)
);

-- Index performances
CREATE INDEX idx_api_keys_prefix    ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_user      ON api_keys(user_id);
CREATE INDEX idx_simulations_user   ON simulations(user_id);
CREATE INDEX idx_simulations_date   ON simulations(created_at);
CREATE INDEX idx_users_email        ON users(email);

-- Taux douaniers initiaux
INSERT INTO customs_rates (hs_code, origin_country, destination_country, duty_rate, preferential_rate, agreement_name) VALUES
('8471', 'CN', 'FR', 0.00, NULL, 'IT Agreement WTO'),
('8517', 'CN', 'FR', 0.00, NULL, 'IT Agreement WTO'),
('6101', 'CN', 'FR', 0.12, NULL, NULL),
('6201', 'CN', 'FR', 0.12, NULL, NULL),
('6403', 'CN', 'FR', 0.17, NULL, NULL),
('8471', 'KR', 'FR', 0.00, 0.00, 'UE-Coree du Sud'),
('8517', 'KR', 'FR', 0.00, 0.00, 'UE-Coree du Sud'),
('6101', 'MA', 'FR', 0.12, 0.00, 'UE-Maroc'),
('6201', 'MA', 'FR', 0.12, 0.00, 'UE-Maroc'),
('8471', 'IN', 'FR', 0.00, NULL, NULL),
('6101', 'IN', 'FR', 0.12, NULL, NULL)
ON CONFLICT (hs_code, origin_country, destination_country) DO UPDATE
SET duty_rate = EXCLUDED.duty_rate, preferential_rate = EXCLUDED.preferential_rate, agreement_name = EXCLUDED.agreement_name;