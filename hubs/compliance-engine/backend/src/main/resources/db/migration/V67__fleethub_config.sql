-- Intégration fleet-hub (docs/07-integration-fleet-hub.md) : par API, pas fusion de
-- backends. fleet-hub reste un service indépendant avec sa propre base -- ce config
-- stocke seulement les identifiants nécessaires pour appeler son API REST existante
-- (POST /api/auth/login puis GET /api/map/vehicles), même schéma d'esprit que
-- erp_configs (V8) pour un fournisseur externe tiers.

CREATE TABLE fleethub_configs (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE INDEX idx_fleethub_config_company ON fleethub_configs(company_id);
