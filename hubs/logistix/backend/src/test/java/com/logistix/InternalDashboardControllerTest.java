package com.logistix;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Résumé d'activité pour le tableau de bord cross-hub de Praxio, via
 * X-Internal-Integration-Key : authentification machine-à-machine (pas un
 * JWT) -- même principe que fleet-hub's InternalShipmentControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalDashboardControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken("admin@logistix.fr");
    }

    @Test
    void getDashboardSummary_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/v1/internal/dashboard-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardSummary_withWrongKey_returns401() throws Exception {
        mvc.perform(get("/v1/internal/dashboard-summary").header("X-Internal-Integration-Key", "clef-incorrecte"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardSummary_withValidKey_returnsSummaryShape() throws Exception {
        String key = enableIntegrationAndGetKey();

        mvc.perform(get("/v1/internal/dashboard-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeWarehouses").isNumber())
                .andExpect(jsonPath("$.openReceivingOrders").isNumber())
                .andExpect(jsonPath("$.openDiscrepancies").isNumber());
    }

    private String enableIntegrationAndGetKey() throws Exception {
        MvcResult res = mvc.perform(post("/v1/praxio-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("praxioIntegrationApiKey").asText();
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
