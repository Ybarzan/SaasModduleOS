package com.fleethub.config;

import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Crée le premier opérateur plateforme ({@code SAAS_ADMIN}) s'il n'existe pas.
 * Indispensable en production où le {@link DataSeeder} (données de démonstration)
 * est désactivé ({@code app.seed.enabled=false}). S'exécute APRÈS le seed
 * (ordre 2 &gt; 1) pour éviter de court-circuiter la création du tenant démo.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class SaaSAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaaSAdminBootstrap.class);

    public static final String SAAS_ADMIN_USERNAME = "saasadmin";
    public static final String SAAS_ADMIN_EMAIL = "ops@fleethub.fr";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(SAAS_ADMIN_USERNAME).isPresent()) {
            return;
        }
        AppUser saasAdmin = new AppUser();
        saasAdmin.setUsername(SAAS_ADMIN_USERNAME);
        saasAdmin.setEmail(SAAS_ADMIN_EMAIL);
        saasAdmin.setPassword(passwordEncoder.encode(adminPassword));
        saasAdmin.setRole("SAAS_ADMIN");
        saasAdmin.setDisplayName("Opérateur Plateforme");
        saasAdmin.setEnabled(true);
        saasAdmin.setCreatedAt(LocalDateTime.now());
        userRepository.save(saasAdmin);
        log.info("SAAS_ADMIN '{}' créé (premier démarrage)", SAAS_ADMIN_USERNAME);
    }
}
