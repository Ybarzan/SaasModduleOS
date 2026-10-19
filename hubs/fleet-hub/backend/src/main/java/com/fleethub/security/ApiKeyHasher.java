package com.fleethub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash (non réversible) des clés d'intégration ENTRANTES vérifiées par
 * égalité (internalIntegrationApiKey, marketplaceApiKey) : ces clés ne sont
 * jamais relues en clair après génération (affichées une seule fois),
 * seulement comparées. Un hash simple, non salé, suffit : la clé source a
 * 128 bits d'entropie (SecureRandom), donc aucune attaque par
 * dictionnaire/table arc-en-ciel n'est praticable -- contrairement à un mot
 * de passe utilisateur à faible entropie, saler n'apporte ici aucune
 * protection supplémentaire. Même algorithme que logistix/finance.
 */
public final class ApiKeyHasher {

    private ApiKeyHasher() {
    }

    public static String hash(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
