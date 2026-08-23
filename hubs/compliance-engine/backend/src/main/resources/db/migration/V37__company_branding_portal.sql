-- Company branding for white-label client portal
CREATE TABLE company_branding (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL UNIQUE REFERENCES companies(id),
    logo_url VARCHAR(500),
    logo_dark_url VARCHAR(500),
    favicon_url VARCHAR(500),
    primary_color VARCHAR(7) DEFAULT '#2563EB',
    secondary_color VARCHAR(7) DEFAULT '#1E40AF',
    accent_color VARCHAR(7) DEFAULT '#F59E0B',
    font_family VARCHAR(100) DEFAULT 'Inter, system-ui, sans-serif',
    custom_domain VARCHAR(255),
    ssl_enabled BOOLEAN DEFAULT TRUE,
    default_language VARCHAR(5) DEFAULT 'FR',
    supported_languages VARCHAR(100) DEFAULT 'FR,EN',
    portal_title VARCHAR(200),
    portal_tagline VARCHAR(500),
    footer_text VARCHAR(500),
    custom_css TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_branding_domain ON company_branding(custom_domain);

COMMENT ON TABLE company_branding IS 'Configuration branding white-label pour le portail client';
