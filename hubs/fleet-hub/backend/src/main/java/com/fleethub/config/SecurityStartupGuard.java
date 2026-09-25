package com.fleethub.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vérifie au démarrage (profil prod) que la configuration sensible n'utilise pas
 * des valeurs par défaut ou trop faibles. Échoue immédiatement sinon (fail-fast).
 */
@Component
public class SecurityStartupGuard {

    private static final List<String> KNOWN_WEAK_SECRETS = List.of(
            "fleet-hub-super-secret-key-change-me-in-production-2026-0123456789abcdef",
            "dev-only-change-me-in-prod-2026!secret-key-32chars!!");

    private static final String DEV_INTEGRATION_SECRET = "dev-only-integration-secret-change-me";

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.gest-password}")
    private String gestPassword;

    @Value("${app.cors.allowed-origins}")
    private String corsOrigins;

    @Value("${app.integration.secret-key}")
    private String integrationSecretKey;

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

        if (adminPassword == null || adminPassword.isBlank()
                || "admin".equals(adminPassword) || "gestion".equals(adminPassword)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : ADMIN_PASSWORD / GESTIONNAIRE_PASSWORD "
                            + "doivent être définis et différents des mots de passe par défaut.");
        }

        if (corsOrigins == null || corsOrigins.isBlank() || "*".equals(corsOrigins.trim())) {
            throw new IllegalStateException(
                    "Configuration de production invalide : APP_CORS_ALLOWED_ORIGINS doit être défini avec "
                            + "une liste explicite d'origines (jamais '*' — combiné à allowCredentials(true), "
                            + "cela autoriserait n'importe quel site à faire des requêtes authentifiées).");
        }

        if (integrationSecretKey == null || integrationSecretKey.isBlank()
                || DEV_INTEGRATION_SECRET.equals(integrationSecretKey)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : INTEGRATION_SECRET_KEY doit être défini "
                            + "et différent de la valeur par défaut de développement.");
        }
    }
}
