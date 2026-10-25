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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Résumé d'activité fleet-hub pour le tableau de bord cross-hub de Praxio,
 * via X-Internal-Integration-Key -- même clé et même principe que
 * InternalShipmentControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalDashboardControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken() throws Exception {
        String email = "internal-dash-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Internal Dash Co\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String enableIntegrationAndGetKey(String token) throws Exception {
        MvcResult res = mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("internalIntegrationApiKey").asText();
    }

    @Test
    void getDashboardSummary_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/api/internal/dashboard-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardSummary_withWrongKey_returns401() throws Exception {
        mvc.perform(get("/api/internal/dashboard-summary").header("X-Internal-Integration-Key", "clef-incorrecte"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardSummary_withValidKey_returnsSummaryShape() throws Exception {
        String token = registerAndGetToken();
        String key = enableIntegrationAndGetKey(token);

        MvcResult res = mvc.perform(get("/api/internal/dashboard-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeShipments").isNumber())
                .andExpect(jsonPath("$.availableTrucks").isNumber())
                .andExpect(jsonPath("$.openCarrierBookings").isNumber())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(0, body.get("activeShipments").asLong());
    }

    @Test
    void getDashboardSummary_disabledIntegration_rejectsKey() throws Exception {
        String token = registerAndGetToken();
        String key = enableIntegrationAndGetKey(token);

        mvc.perform(post("/api/internal-integrations/disable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/internal/dashboard-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBillingSummary_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/api/internal/billing-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBillingSummary_withWrongKey_returns401() throws Exception {
        mvc.perform(get("/api/internal/billing-summary").header("X-Internal-Integration-Key", "clef-incorrecte"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBillingSummary_withValidKey_returnsSummaryShape() throws Exception {
        String token = registerAndGetToken();
        String key = enableIntegrationAndGetKey(token);

        mvc.perform(get("/api/internal/billing-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").isString())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.stripeConfigured").isBoolean());
    }
}
