package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partage opt-in d'expéditions vers compliance-engine / Logistix :
 * activation/désactivation, clé jamais exposée hors de la réponse
 * d'activation -- même principe que MarketplaceControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalIntegrationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "internal-integ-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Internal Integ Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        adminToken = objectMapper.readTree(registerRes.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void settings_defaultsToDisabled() throws Exception {
        mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(false))
                .andExpect(jsonPath("$.internalIntegrationApiKey").doesNotExist());
    }

    @Test
    void enable_generatesKey_settingsNeverExposesItAfterward() throws Exception {
        MvcResult enableRes = mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(enableRes.getResponse().getContentAsString());
        String key = body.get("internalIntegrationApiKey").asText();
        assertNotNull(key);
        assertTrue(key.length() >= 32, "La clé doit être suffisamment longue pour ne pas être devinable");

        mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(true))
                .andExpect(jsonPath("$.internalIntegrationApiKey").doesNotExist());
    }

    @Test
    void disable_thenKeyIsRejected() throws Exception {
        MvcResult enableRes = mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(enableRes.getResponse().getContentAsString()).get("internalIntegrationApiKey").asText();

        mvc.perform(post("/api/internal-integrations/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(false));

        mvc.perform(get("/api/internal/shipments/" + java.util.UUID.randomUUID())
                        .header("X-Internal-Integration-Key", key))
                .andExpect(status().isUnauthorized());
    }
}
