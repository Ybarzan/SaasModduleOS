ALTER TABLE companies ADD COLUMN praxio_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN praxio_integration_api_key VARCHAR(64);
