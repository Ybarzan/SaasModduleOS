package com.fleethub;

import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * En production, le seeder de démonstration est désactivé
 * ({@code app.seed.enabled=false}) : le premier opérateur plateforme doit
 * être créé par {@code SaaSAdminBootstrap} (ApplicationRunner @Order(2)).
 * Ce test valide ce bootstrap avec le seed coupé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.seed.enabled=false",
        "app.security.admin-password=boot-pass-123"
})
class SaaSAdminBootstrapTest {

    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void firstSaasAdminIsBootstrapped_whenSeedDisabled() {
        AppUser saasAdmin = userRepository.findByUsername("saasadmin")
                .orElseThrow(() -> new AssertionError("saasadmin doit être créé par le bootstrap"));

        assertEquals("SAAS_ADMIN", saasAdmin.getRole());
        assertEquals("ops@fleethub.fr", saasAdmin.getEmail());
        assertTrue(saasAdmin.isEnabled(), "L'opérateur plateforme doit être actif");
        assertTrue(passwordEncoder.matches("boot-pass-123", saasAdmin.getPassword()),
                "Le mot de passe doit venir d'ADMIN_PASSWORD (app.security.admin-password)");
    }
}
