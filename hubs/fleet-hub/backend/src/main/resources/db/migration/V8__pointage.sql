-- Pointage chauffeur : portail séparé (rôle CHAUFFEUR) où le chauffeur déclare
-- lui-même début / pause / fin de service. Alimente les KPI du back-office en
-- complément de la tachygraphie, sans jamais donner accès au back-office lui-même.

-- Code d'accès public (non devinable) permettant au portail de charger la liste
-- des chauffeurs d'une société avant authentification (l'identifiant numérique
-- de company ne doit jamais être exposé publiquement, il est séquentiel).
ALTER TABLE company ADD COLUMN access_code VARCHAR(12);
UPDATE company SET access_code = upper(substr(md5(id::text || 'fleethub-pointage-salt'), 1, 8));
ALTER TABLE company ALTER COLUMN access_code SET NOT NULL;
ALTER TABLE company ADD CONSTRAINT uk_company_access_code UNIQUE (access_code);

-- Lien optionnel entre un compte applicatif et un chauffeur : seuls les comptes
-- de rôle CHAUFFEUR portent cette colonne (mot de passe = PIN à 4 chiffres).
ALTER TABLE app_user ADD COLUMN driver_id BIGINT;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_driver FOREIGN KEY (driver_id) REFERENCES driver;
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_driver UNIQUE (driver_id);

CREATE TABLE pointage_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEBUT', 'PAUSE_DEBUT', 'PAUSE_FIN', 'FIN')),
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT fk_pointage_company FOREIGN KEY (company_id) REFERENCES company,
    CONSTRAINT fk_pointage_driver FOREIGN KEY (driver_id) REFERENCES driver
);
CREATE INDEX idx_pointage_driver_time ON pointage_event (driver_id, occurred_at);
CREATE INDEX idx_pointage_company_time ON pointage_event (company_id, occurred_at);
