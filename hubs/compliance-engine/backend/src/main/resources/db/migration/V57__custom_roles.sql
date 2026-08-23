-- Rôles personnalisés par entreprise avec permissions granulaires par module,
-- en complément (pas en remplacement) de la hiérarchie fixe OWNER/ADMIN/MANAGER/USER
-- qui continue de gater les endpoints existants via @RolesAllowed.
CREATE TABLE custom_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    permissions TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (company_id, name)
);

CREATE INDEX idx_custom_roles_company_id ON custom_roles(company_id);

ALTER TABLE company_roles ADD COLUMN custom_role_id UUID REFERENCES custom_roles(id) ON DELETE SET NULL;
