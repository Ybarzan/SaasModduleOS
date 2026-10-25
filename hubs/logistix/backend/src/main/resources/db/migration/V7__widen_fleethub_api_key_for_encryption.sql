-- fleethub_api_key passe en clair à chiffré (AES/GCM + IV aléatoire, base64) :
-- la colonne doit pouvoir contenir le ciphertext, plus large que la clé en
-- clair d'origine. Voir EncryptedApiKeyConverter. praxio_integration_api_key
-- n'a pas besoin d'être élargie : un hash SHA-256 hex tient dans les 64
-- caractères déjà alloués.
ALTER TABLE companies ALTER COLUMN fleethub_api_key TYPE VARCHAR(255);
