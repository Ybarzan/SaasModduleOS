package com.incokalk.controller;

import com.incokalk.dto.taric.TaricMeasureDto;
import com.incokalk.model.CompanyRole;
import com.incokalk.service.taric.TaricApiClient;
import com.incokalk.service.taric.TaricSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/taric/sync/** et /v1/taric/stats appliquent effectivement le
 * contrôle de rôle.
 *
 * Avant correctif, ces endpoints étaient annotés avec @PreAuthorize alors que la
 * sécurité de méthode Spring (@EnableMethodSecurity) n'est jamais activée dans ce
 * projet — seule l'AOP @RolesAllowed (RolesAllowedAspect) est réellement appliquée.
 * @PreAuthorize était donc silencieusement ignoré et n'importe quel utilisateur
 * authentifié (y compris un simple USER) pouvait déclencher la synchronisation
 * TARIC coûteuse (appels à l'API EU) réservée à OWNER/ADMIN.
 */
class TaricControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaricSyncService taricSyncService;

    @MockBean
    private TaricApiClient taricApiClient;

    @Test
    @DisplayName("POST /v1/taric/sync/{hsCode} → 403 pour un rôle USER (sync réservée à OWNER/ADMIN)")
    void refreshRates_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(post("/v1/taric/sync/8703")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/taric/sync/{hsCode} → 403 pour un rôle MANAGER (sync réservée à OWNER/ADMIN)")
    void refreshRates_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/taric/sync/8703")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/taric/sync/{hsCode} → 200 pour un rôle ADMIN")
    void refreshRates_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        when(taricSyncService.refreshHsCode(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new TaricMeasureDto()));

        mockMvc.perform(post("/v1/taric/sync/8703")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/taric/sync/daily → 403 pour un rôle USER (sync réservée à OWNER/ADMIN)")
    void triggerDailySync_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(post("/v1/taric/sync/daily")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/taric/sync/daily → 200 pour un rôle OWNER")
    void triggerDailySync_allowedForOwner() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.OWNER);
        when(taricSyncService.getTotalRates()).thenReturn(10L);
        when(taricSyncService.getDistinctHsCount()).thenReturn(5L);

        mockMvc.perform(post("/v1/taric/sync/daily")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/taric/stats → 403 pour un rôle USER (lecture réservée à OWNER/ADMIN/MANAGER)")
    void getStats_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/taric/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/taric/stats → 200 pour un rôle MANAGER")
    void getStats_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(taricSyncService.getTotalRates()).thenReturn(10L);
        when(taricSyncService.getDistinctHsCount()).thenReturn(5L);

        mockMvc.perform(get("/v1/taric/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/taric/lookup → 200 pour un simple USER (lecture publique-authentifiée)")
    void lookupRates_allowedForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        when(taricApiClient.fetchRates(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new TaricMeasureDto()));

        mockMvc.perform(get("/v1/taric/lookup")
                        .param("hsCode", "8703")
                        .param("origin", "CN")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }
}
