package com.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paramètres d'intégration SORTANTE fleet-hub (distincte de
 * /v1/internal-integration/** qui est ENTRANTE) : réservé ADMIN, statut sans
 * jamais exposer la clé elle-même -- même principe que Logistix's
 * CompanySettingsControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanySettingsControllerTest {

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
    void getStatus_defaultsToNotConfigured() throws Exception {
        mvc.perform(get("/v1/company/fleethub-key").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void setKey_thenStatusIsConfigured_butKeyNeverEchoedBack() throws Exception {
        mvc.perform(put("/v1/company/fleethub-key")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"abc123def456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        mvc.perform(get("/v1/company/fleethub-key").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void clearKey_thenStatusIsNotConfigured() throws Exception {
        mvc.perform(put("/v1/company/fleethub-key")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"abc123def456\"}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/v1/company/fleethub-key").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void manager_cannotReadOrSetKey_returns403() throws Exception {
        mvc.perform(get("/v1/company/fleethub-key").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/v1/company/fleethub-key")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"abc123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setKey_blankValue_rejectedByValidation() throws Exception {
        mvc.perform(put("/v1/company/fleethub-key")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
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
