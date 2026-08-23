package com.incokalk.controller;

import com.incokalk.service.CsrdReportingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CsrdReportingControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CsrdReportingService csrdReportingService;

    @Test
    @DisplayName("GET /v1/csrd/report → rapport CSRD complet")
    void getCsrdReport() throws Exception {
        Map<String, Object> report = Map.of(
                "reportPeriod", "2026-Q3",
                "companyId", companyId,
                "totalEmissionsCO2", 1250.75,
                "emissionsByScope", Map.of(
                        "scope1", 375.23,
                        "scope2", 250.15,
                        "scope3", 625.37
                ),
                "emissionsByLane", List.of(
                        Map.of("lane", "Paris → Lyon", "co2Tonnes", 120.5, "percentage", 40.0),
                        Map.of("lane", "Marseille → Lille", "co2Tonnes", 180.2, "percentage", 60.0)
                ),
                "offsetCreditsPurchased", 500.0,
                "offsetCreditsRetired", 300.0,
                "netEmissions", 950.75,
                "esrsE1Compliant", true,
                "recommendations", List.of(
                        "Ensure all scope 3 emissions are mapped across the full value chain.",
                        "Implement a double materiality assessment to identify key CSRD impacts."
                )
        );

        when(csrdReportingService.getCsrdReport()).thenReturn(report);

        mockMvc.perform(get("/v1/csrd/report")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportPeriod").value("2026-Q3"))
                .andExpect(jsonPath("$.totalEmissionsCO2").value(1250.75))
                .andExpect(jsonPath("$.esrsE1Compliant").value(true))
                .andExpect(jsonPath("$.emissionsByScope.scope1").value(375.23))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.recommendations.length()").value(2));
    }
}