package com.logistix.config;

import com.logistix.model.Company;
import com.logistix.model.Role;
import com.logistix.model.User;
import com.logistix.repository.CompanyRepository;
import com.logistix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Jeu de données de démonstration (dev). Désactivé en prod par défaut
 * ({@code app.seed.enabled=false}).
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Bean
    CommandLineRunner seed(UserRepository userRepository, CompanyRepository companyRepository) {
        return args -> {
            if (userRepository.count() > 0) return;

            Company demo = Company.builder().name("Logistix Démo").build();
            companyRepository.save(demo);

            userRepository.save(User.builder()
                    .company(demo)
                    .email("admin@logistix.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Administrateur Logistix")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());

            userRepository.save(User.builder()
                    .company(demo)
                    .email("manager@logistix.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Responsable Entrepôt")
                    .role(Role.MANAGER)
                    .enabled(true)
                    .build());

            userRepository.save(User.builder()
                    .company(demo)
                    .email("user@logistix.fr")
                    .password(passwordEncoder.encode(adminPassword))
                    .displayName("Opérateur Réception")
                    .role(Role.USER)
                    .enabled(true)
                    .build());
        };
    }
}
