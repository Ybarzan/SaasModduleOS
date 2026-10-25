package com.logistix.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vérifie au démarrage (profil prod) que la configuration sensible n'utilise pas
 * des valeurs par défaut ou trop faibles. Échoue immédiatement sinon (fail-fast).
 * Même garde-fou que hubs/fleet-hub/backend/.../config/SecurityStartupGuard.java.
 */
@Component
public class SecurityStartupGuard {

    private static final List<String> KNOWN_WEAK_SECRETS = List.of(
            "dev-only-change-me-in-prod-2026!secret-key-32chars!!");

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.credential-encryption-key}")
    private String credentialEncryptionKey;

    @Value("${app.cors.allowed-origins}")
    private String corsOrigins;

    public SecurityStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void verify() {
        if (!environment.acceptsProfiles("prod")) {
            return;
        }

        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32
                || KNOWN_WEAK_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : JWT_SECRET doit être défini (>= 32 caractères, "
                            + "non identique au secret par défaut).");
        }

        if (adminPassword == null || adminPassword.isBlank() || "admin".equals(adminPassword)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : ADMIN_PASSWORD doit être défini et différent "
                            + "du mot de passe par défaut.");
        }

        if (credentialEncryptionKey == null || credentialEncryptionKey.isBlank()
                || "dev-only-insecure-credential-encryption-key-not-for-production".equals(credentialEncryptionKey)
                || credentialEncryptionKey.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : CREDENTIAL_ENCRYPTION_KEY doit être défini, "
                            + "différent de la valeur par défaut et différent de JWT_SECRET -- cette clé "
                            + "chiffre au repos la clé d'intégration fleet-hub.");
        }

        if (corsOrigins == null || corsOrigins.isBlank() || "*".equals(corsOrigins.trim())) {
            throw new IllegalStateException(
                    "Configuration de production invalide : APP_CORS_ALLOWED_ORIGINS doit être défini avec "
                            + "une liste explicite d'origines (jamais '*' — combiné à allowCredentials(true), "
                            + "cela autoriserait n'importe quel site à faire des requêtes authentifiées).");
        }
    }
}
