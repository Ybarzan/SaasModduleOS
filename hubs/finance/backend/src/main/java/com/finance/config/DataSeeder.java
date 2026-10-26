package com.finance.config;

import com.finance.model.Company;
import com.finance.model.Role;
import com.finance.model.User;
import com.finance.repository.CompanyRepository;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Jeu de données de démonstration (dev). Désactivé en prod par défaut
 * ({@code app.seed.enabled=false}) -- {@link AdminBootstrap} garantit malgré
 * tout qu'un compte admin existe dans ce cas. S'exécute AVANT ce dernier
 * (ordre 1 &lt; 2) pour que les 3 comptes de démo soient créés en premier.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Bean
    @Order(1)
    CommandLineRunner seed(UserRepository userRepository, CompanyRepository companyRepository) {
        return args -> {
            if (userRepository.count() > 0) return;

            Company demo = Company.builder().name("Finance Démo").build();
            companyRepository.save(demo);

            userRepository.save(User.builder()
                    .company(demo)
                    .email("admin@finance.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Administrateur Finance")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());

            userRepository.save(User.builder()
                    .company(demo)
                    .email("manager@finance.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Responsable Trésorerie")
                    .role(Role.MANAGER)
                    .enabled(true)
                    .build());

            userRepository.save(User.builder()
                    .company(demo)
                    .email("user@finance.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Opérateur Facturation")
                    .role(Role.USER)
                    .enabled(true)
                    .build());
        };
    }
}
