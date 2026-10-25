-- fleethub_api_key / finance_api_key passent en clair à chiffré (AES/GCM + IV
-- aléatoire, base64) : la colonne doit pouvoir contenir le ciphertext, plus
-- large que la clé en clair d'origine. Voir EncryptedApiKeyConverter.
ALTER TABLE companies ALTER COLUMN fleethub_api_key TYPE VARCHAR(255);
ALTER TABLE companies ALTER COLUMN finance_api_key TYPE VARCHAR(255);
