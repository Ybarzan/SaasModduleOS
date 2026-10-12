ALTER TABLE company ADD COLUMN internal_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE company ADD COLUMN internal_integration_api_key VARCHAR(64);
