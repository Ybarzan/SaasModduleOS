package com.praxio.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Vérifie au démarrage (profil prod) que la configuration sensible n'utilise pas
 * de valeurs par défaut. Échoue immédiatement sinon (fail-fast). Praxio n'a pas
 * de JWT_SECRET (voir SecurityConfig : un seul mot de passe admin en Basic Auth),
 * donc ce garde-fou ne couvre que le mot de passe admin et le CORS -- même
 * principe que hubs/fleet-hub/backend/.../config/SecurityStartupGuard.java.
 */
@Component
public class SecurityStartupGuard {

    private final Environment environment;

    @Value("${praxio.admin-password}")
    private String adminPassword;

    @Value("${praxio.sso-encryption-key}")
    private String ssoEncryptionKey;

    @Value("${praxio.cors.allowed-origins}")
    private String corsOrigins;

    public SecurityStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void verify() {
        if (!environment.acceptsProfiles("prod")) {
            return;
        }

        if (adminPassword == null || adminPassword.isBlank() || "admin".equals(adminPassword)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : ADMIN_PASSWORD doit être défini et différent "
                            + "du mot de passe par défaut -- ce mot de passe protège le tableau de bord "
                            + "cross-hub et les identifiants SSO de tous les hubs.");
        }

        if (ssoEncryptionKey == null || ssoEncryptionKey.isBlank()
                || "dev-only-insecure-sso-encryption-key-not-for-production".equals(ssoEncryptionKey)
                || ssoEncryptionKey.equals(adminPassword)) {
            throw new IllegalStateException(
                    "Configuration de production invalide : SSO_ENCRYPTION_KEY doit être défini, différent "
                            + "de la valeur par défaut et différent de ADMIN_PASSWORD -- cette clé chiffre "
                            + "au repos les mots de passe SSO de tous les hubs.");
        }

        if (corsOrigins == null || corsOrigins.isBlank() || "*".equals(corsOrigins.trim())) {
            throw new IllegalStateException(
                    "Configuration de production invalide : APP_CORS_ALLOWED_ORIGINS doit être défini avec "
                            + "une liste explicite d'origines (jamais '*' en production).");
        }
    }
}
