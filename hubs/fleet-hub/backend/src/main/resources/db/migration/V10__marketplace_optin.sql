ALTER TABLE company ADD COLUMN marketplace_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE company ADD COLUMN marketplace_api_key VARCHAR(64);
