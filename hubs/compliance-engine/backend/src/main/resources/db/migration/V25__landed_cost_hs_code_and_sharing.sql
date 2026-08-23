-- V25__landed_cost_hs_code_and_sharing.sql
-- Add HS code + share token to landed_costs

ALTER TABLE landed_costs ADD COLUMN IF NOT EXISTS hs_code VARCHAR(10);
ALTER TABLE landed_costs ADD COLUMN IF NOT EXISTS share_token VARCHAR(64);
ALTER TABLE landed_costs ADD COLUMN IF NOT EXISTS transport_mode VARCHAR(10) DEFAULT 'SEA';

CREATE UNIQUE INDEX IF NOT EXISTS idx_landed_cost_share_token ON landed_costs(share_token) WHERE share_token IS NOT NULL;
