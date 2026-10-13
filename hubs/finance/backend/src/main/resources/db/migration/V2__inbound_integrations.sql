-- V2__inbound_integrations.sql
-- Intégration entrante opt-in (clé machine-à-machine X-Internal-Integration-Key),
-- partagée par tous les appelants (Praxio pour le tableau de bord cross-hub,
-- compliance-engine pour FinanceIntegrationClient).

ALTER TABLE companies ADD COLUMN internal_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN internal_integration_api_key VARCHAR(64);
