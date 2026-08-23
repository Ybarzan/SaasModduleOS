package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.DeniedPartyCheck;
import com.incokalk.service.DeniedPartyScreeningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le contrôle de rôle sur /v1/dps/**.
 *
 * Avant correctif, GET /v1/dps/stats était restreint à OWNER/ADMIN alors que la page
 * frontend "Screening parties" (frontend/src/config/navigation.ts) est accessible dès
 * le rôle MANAGER et appelle cet endpoint sans condition au chargement — un utilisateur
 * MANAGER se voyait donc opposer un 403 sur les statistiques DPS.
 */
class DeniedPartyScreeningControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeniedPartyScreeningService dpsService;

    @Test
    @DisplayName("GET /v1/dps/stats → 200 pour un rôle MANAGER (cohérent avec la page frontend gérée au niveau MANAGER)")
    void stats_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(dpsService.getStats()).thenReturn(Map.of("total", 5L, "CLEAR", 5L));

        mockMvc.perform(get("/v1/dps/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("GET /v1/dps/stats → 403 pour un rôle USER")
    void stats_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/dps/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/dps/stats → 200 pour un rôle ADMIN")
    void stats_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        when(dpsService.getStats()).thenReturn(Map.of("total", 2L));

        mockMvc.perform(get("/v1/dps/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/dps/history → 200 pour un rôle MANAGER")
    void history_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(dpsService.getHistory()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/v1/dps/history")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/dps/history → 403 pour un rôle USER")
    void history_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/dps/history")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/dps/{id} → délègue au service scopé par tenant (companyId + id)")
    void getById_delegatesToTenantScopedService() throws Exception {
        UUID checkId = UUID.randomUUID();
        DeniedPartyCheck check = DeniedPartyCheck.builder()
                .id(checkId)
                .checkedName("Acme")
                .build();
        when(dpsService.getById(checkId)).thenReturn(check);

        mockMvc.perform(get("/v1/dps/" + checkId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedName").value("Acme"));
    }
}
