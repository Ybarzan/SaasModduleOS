package com.fleethub.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupGuard.class);

    private static final List<String> KNOWN_WEAK_SECRETS = List.of(
            "fleet-hub-super-secret-key-change-me-in-production-2026-0123456789abcdef");

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.gest-password}")
    private String gestPassword;

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

        if (adminPassword == null || adminPassword.isBlank()
                || "admin".equals(adminPassword) || "gestion".equals(adminPassword)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : ADMIN_PASSWORD / GESTIONNAIRE_PASSWORD "
                            + "doivent être définis et différents des mots de passe par défaut.");
        }

        if (corsOrigins == null || corsOrigins.isBlank() || "*".equals(corsOrigins.trim())) {
            log.warn("Production : app.cors.allowed-origins est vide ou '*' — pensez à restreindre "
                    + "les origines autorisées (APP_CORS_ALLOWED_ORIGINS).");
        }
    }
}
