package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.service.EtaPredictionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La page frontend /eta-predictions est gérée derrière requiredRole: 'MANAGER'
 * (frontend/src/config/navigation.ts) et appelle systématiquement
 * GET /v1/eta-predictions/stats au chargement. Cet endpoint doit donc rester
 * accessible à MANAGER (et rôles supérieurs), sous peine de 403 dès l'arrivée
 * sur la page pour tout MANAGER.
 */
class EtaPredictionControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtaPredictionService etaPredictionService;

    @Test
    @DisplayName("GET /v1/eta-predictions/stats → 200 pour un rôle MANAGER (cohérent avec le gating de navigation frontend)")
    void getStats_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 0L);
        stats.put("avgAccuracy", BigDecimal.ZERO);
        stats.put("onTimePercent", BigDecimal.ZERO);
        stats.put("avgDays", 0);
        when(etaPredictionService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/v1/eta-predictions/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/eta-predictions/stats → 403 pour un rôle USER")
    void getStats_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/eta-predictions/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }
}
