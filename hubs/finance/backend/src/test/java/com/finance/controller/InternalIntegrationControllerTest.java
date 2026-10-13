package com.finance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partage opt-in d'agrégats Finance vers Praxio et compliance-engine :
 * réservé ADMIN, clé jamais exposée hors de la réponse d'activation -- même
 * principe que Logistix's PraxioIntegrationControllerTest et fleet-hub's
 * InternalIntegrationControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalIntegrationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken("admin@finance.fr");
        managerToken = loginToken("manager@finance.fr");
    }

    @Test
    void enable_generatesKey_settingsNeverExposesItAfterward() throws Exception {
        MvcResult enableRes = mvc.perform(post("/v1/internal-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(enableRes.getResponse().getContentAsString());
        String key = body.get("internalIntegrationApiKey").asText();
        assertNotNull(key);
        assertTrue(key.length() >= 32, "La clé doit être suffisamment longue pour ne pas être devinable");

        mvc.perform(get("/v1/internal-integration/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(true))
                .andExpect(jsonPath("$.internalIntegrationApiKey").doesNotExist());
    }

    @Test
    void disable_thenKeyIsRejectedByInternalEndpoint() throws Exception {
        MvcResult enableRes = mvc.perform(post("/v1/internal-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(enableRes.getResponse().getContentAsString()).get("internalIntegrationApiKey").asText();

        mvc.perform(post("/v1/internal-integration/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(false));

        mvc.perform(get("/v1/internal/dashboard-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manager_cannotReadOrChangeSettings_returns403() throws Exception {
        mvc.perform(get("/v1/internal-integration/settings").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/internal-integration/enable").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    private String loginToken(String email) throws Exception {
        String body = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, Map.class).get("token").toString();
    }
}
