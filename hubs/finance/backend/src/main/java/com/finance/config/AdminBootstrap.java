package com.finance.config;

import com.finance.model.Company;
import com.finance.model.Role;
import com.finance.model.User;
import com.finance.repository.CompanyRepository;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le premier compte admin ({@code admin@finance.fr}) s'il n'existe pas.
 * Indispensable en production où {@link DataSeeder} (données de démonstration)
 * est désactivé par défaut ({@code app.seed.enabled=false}) : sans ce
 * bootstrap, un premier démarrage en profil prod ne crée aucun utilisateur et
 * il est impossible de se connecter. S'exécute APRÈS le seed (ordre 2 &gt; 1)
 * pour éviter de créer un doublon quand le seed est activé -- il vérifie de
 * toute façon l'email précis plutôt que {@code count() > 0}, pour rester
 * correct même si l'ordre venait à changer.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    public static final String ADMIN_EMAIL = "admin@finance.fr";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return;
        }
        Company company = Company.builder().name("Finance").build();
        companyRepository.save(company);

        userRepository.save(User.builder()
                .company(company)
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(adminPassword))
                .displayName("Administrateur")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        log.info("Compte admin '{}' créé (premier démarrage)", ADMIN_EMAIL);
    }
}
