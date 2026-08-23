package com.incokalk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class SecurityPropertiesValidator implements ApplicationRunner {

    private static final List<String> FORBIDDEN_JWT_SECRETS = List.of(
        "IncoKalkDevSecretKey_2026_ChangeMeForProd!",
        "dev-only-insecure-jwt-secret-not-for-production-use"
    );

    private final Environment environment;

    @Value("${incokalk.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${incokalk.security.api-key.salt:}")
    private String apiKeySalt;

    public SecurityPropertiesValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (isDev) {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                log.warn("⚠️  JWT_SECRET non défini — mode dev avec secret par défaut (INSECURÉ)");
            }
            return;
        }

        boolean hasErrors = false;

        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("❌ FATAL: JWT_SECRET non défini. Définissez la variable d'env JWT_SECRET (minimum 32 caractères)");
            hasErrors = true;
        } else if (jwtSecret.length() < 32) {
            log.error("❌ FATAL: JWT_SECRET trop court ({} caractères). Minimum recommandé: 32 caractères", jwtSecret.length());
            hasErrors = true;
        } else if (FORBIDDEN_JWT_SECRETS.contains(jwtSecret)) {
            log.error("❌ FATAL: JWT_SECRET correspond à un secret de développement connu. Définissez un secret unique et aléatoire (ex: openssl rand -base64 48)");
            hasErrors = true;
        }

        if (apiKeySalt == null || apiKeySalt.isBlank()) {
            log.error("❌ FATAL: API_KEY_SALT non défini. Définissez la variable d'env API_KEY_SALT");
            hasErrors = true;
        }

        if (hasErrors) {
            log.error("🔒 L'application ne peut pas démarrer sans les secrets de sécurité configurés.");
            log.error("   Copiez infrastructure/docker/.env.example en infrastructure/docker/.env");
            log.error("   et configurez JWT_SECRET et API_KEY_SALT avec des valeurs sécurisées.");
            throw new IllegalStateException("Secrets de sécurité manquants en mode production");
        }

        log.info("✅ Validation des secrets de sécurité réussie");
    }
}
