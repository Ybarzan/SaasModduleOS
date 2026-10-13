package com.finance.controller;

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
 * Agrégats Finance pour Praxio et compliance-engine, via
 * X-Internal-Integration-Key : authentification machine-à-machine (pas un
 * JWT) -- même principe que Logistix's InternalDashboardControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalFinanceControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken("admin@finance.fr");
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
                .andExpect(jsonPath("$.overdueInvoicesCount").isNumber())
                .andExpect(jsonPath("$.overdueInvoicesAmount").isNumber());
    }

    @Test
    void getInvoiceTimeliness_withValidKey_returnsShape() throws Exception {
        String key = enableIntegrationAndGetKey();

        mvc.perform(get("/v1/internal/invoice-timeliness").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opportunities").isNumber())
                .andExpect(jsonPath("$.defects").isNumber());
    }

    @Test
    void getFinancialTotals_withValidKey_returnsShape() throws Exception {
        String key = enableIntegrationAndGetKey();

        mvc.perform(get("/v1/internal/financial-totals").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").isNumber())
                .andExpect(jsonPath("$.totalCost").isNumber())
                .andExpect(jsonPath("$.totalMargin").isNumber());
    }

    @Test
    void getCarbonTotals_withValidKey_returnsShape() throws Exception {
        String key = enableIntegrationAndGetKey();

        mvc.perform(get("/v1/internal/carbon-totals").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.co2EmissionsKg").isNumber())
                .andExpect(jsonPath("$.offsetCreditsRetired").isNumber())
                .andExpect(jsonPath("$.offsetTotalCost").isNumber());
    }

    @Test
    void getFxExposureReport_withValidKey_returnsShape() throws Exception {
        String key = enableIntegrationAndGetKey();

        mvc.perform(get("/v1/internal/fx-exposure-report").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("EUR"))
                .andExpect(jsonPath("$.currencyExposures").isArray());
    }

    @Test
    void getFxExposureReport_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/v1/internal/fx-exposure-report"))
                .andExpect(status().isUnauthorized());
    }

    private String enableIntegrationAndGetKey() throws Exception {
        MvcResult res = mvc.perform(post("/v1/internal-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("internalIntegrationApiKey").asText();
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
