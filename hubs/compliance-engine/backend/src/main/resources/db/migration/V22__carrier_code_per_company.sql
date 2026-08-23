-- Make carrier code unique per company instead of globally unique
ALTER TABLE carriers DROP CONSTRAINT IF EXISTS carriers_code_key;
ALTER TABLE carriers ADD CONSTRAINT uk_carriers_company_code UNIQUE (company_id, code);
