package com.incokalk.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUBLIC_DEV_ONLY (h2-console/swagger-ui/api-docs) n'était jamais fusionné dans
 * publicEndpoints, même en profil dev — ces routes exigeaient donc un JWT alors
 * qu'elles sont censées être librement accessibles en développement (cf. aussi
 * frameOptions désactivé pour h2-console, conditionné sur le même isDev).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SecurityConfigDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_accessibleWithoutAuth_inDevProfile() throws Exception {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void swaggerUi_accessibleWithoutAuth_inDevProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }
}

/** Garde-fou : hors profil dev, ces routes restent verrouillées derrière l'auth. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigNonDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_requiresAuth_outsideDevProfile() throws Exception {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().isUnauthorized());
    }
}
