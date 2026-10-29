-- Clé d'intégration SORTANTE vers fleet-hub (en-tête X-Internal-Integration-Key),
-- générée côté fleet-hub quand une société y active le partage puis collée ici
-- par un admin (voir CompanySettingsController) -- distincte d'internal_integration_api_key
-- (V2, qui est ENTRANTE : d'autres hubs appelant Finance). Chiffrée au repos
-- (AES/GCM, voir CredentialEncryptionService) : VARCHAR(255) dès le départ pour
-- l'IV + le texte chiffré encodés en base64, jamais recherchée par égalité.
ALTER TABLE companies ADD COLUMN fleethub_api_key VARCHAR(255);
