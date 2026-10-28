package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.config.ApiKeyCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Collage de la clé webhook compliance-engine (GET/PUT/DELETE settings),
 * jamais réaffichée une fois enregistrée, et réservée ADMIN -- même principe
 * que InternalIntegrationControllerTest / MarketplaceControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ComplianceEngineWebhookControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApiKeyCrypto apiKeyCrypto;

    private String adminToken;
    private Long companyId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "compliance-webhook-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Compliance Webhook Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(registerRes.getResponse().getContentAsString());
        adminToken = body.get("token").asText();
        companyId = body.get("companyId").asLong();
    }

    @Test
    void settings_defaultsToNotConfigured() throws Exception {
        mvc.perform(get("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void update_storesKey_andReportsConfigured() throws Exception {
        mvc.perform(put("/api/compliance-engine-webhook/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookKey\":\"key-from-compliance-engine\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));

        mvc.perform(get("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));

        // En base, la colonne ne contient jamais la clé en clair (chiffrée via
        // ApiKeyCrypto, déjà utilisé pour les clés API fournisseurs), mais
        // déchiffrer la valeur stockée doit redonner exactement la clé d'origine.
        String storedValue = jdbcTemplate.queryForObject(
                "SELECT compliance_engine_webhook_key FROM company WHERE id = ?", String.class, companyId);
        assertThat(storedValue).isNotEqualTo("key-from-compliance-engine");
        assertThat(apiKeyCrypto.decrypt(storedValue)).isEqualTo("key-from-compliance-engine");
    }

    @Test
    void update_blankKey_returns400() throws Exception {
        mvc.perform(put("/api/compliance-engine-webhook/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_clearsKey() throws Exception {
        mvc.perform(put("/api/compliance-engine-webhook/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookKey\":\"key-from-compliance-engine\"}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        mvc.perform(get("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void settings_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/compliance-engine-webhook/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void settings_asGestionnaire_returns403() throws Exception {
        String email = "compliance-webhook-gest-" + System.nanoTime() + "@test.fr";
        MvcResult inviteRes = mvc.perform(post("/api/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Martin\",\"email\":\""
                                + email + "\",\"role\":\"GESTIONNAIRE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode inviteBody = objectMapper.readTree(inviteRes.getResponse().getContentAsString());
        String inviteToken = inviteBody.get("inviteUrl").asText().split("token=")[1];

        mvc.perform(post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult loginRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String gestToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + gestToken))
                .andExpect(status().isForbidden());
    }
}
