package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.service.DebAutoGenerationService;
import com.incokalk.service.DebAutoGenerationService.DebGenerationResult;
import com.incokalk.service.FrenchFiscalService;
import com.incokalk.service.FrenchFiscalService.FrenchDutyBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrenchFiscalControllerTest extends ControllerTestBase {

    @MockBean
    private FrenchFiscalService frenchFiscalService;
    @MockBean
    private DebAutoGenerationService debAutoGenService;

    private String bearer(CompanyRole.Role role) {
        return "Bearer " + generateJwtToken(UUID.randomUUID(), companyId, role);
    }

    private FrenchDutyBreakdown breakdown() {
        return new FrenchDutyBreakdown("MC", BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "MFN", "");
    }

    @Test
    @DisplayName("POST /v1/french-fiscal/calculate → 200 pour un rôle USER (calcul ouvert)")
    void calculate_allowedForUser() throws Exception {
        when(frenchFiscalService.calculateFrenchDuties(any(), any(), any(), any(), any(), any()))
                .thenReturn(breakdown());

        mockMvc.perform(post("/v1/french-fiscal/calculate")
                        .header("Authorization", bearer(CompanyRole.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hsCode\":\"220300\",\"cifValue\":1000,\"originCountry\":\"CN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/french-fiscal/deb/auto-generate → 403 pour un rôle USER (persistance réservée)")
    void debAutoGenerate_forbiddenForUser() throws Exception {
        mockMvc.perform(post("/v1/french-fiscal/deb/auto-generate")
                        .header("Authorization", bearer(CompanyRole.Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"direction\":\"IMPORT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/french-fiscal/deb/auto-generate → 200 pour un rôle ADMIN")
    void debAutoGenerate_allowedForAdmin() throws Exception {
        when(debAutoGenService.generateDebFromShipment(any(), any()))
                .thenReturn(new DebGenerationResult("DEB-2026-08-001", "2026-08", null, "ok"));

        mockMvc.perform(post("/v1/french-fiscal/deb/auto-generate")
                        .header("Authorization", bearer(CompanyRole.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"" + companyId + "\",\"direction\":\"IMPORT\"}"))
                .andExpect(status().isOk());
    }
}
