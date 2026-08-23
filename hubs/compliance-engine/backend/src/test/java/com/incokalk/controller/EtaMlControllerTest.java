package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.service.ml.EtaRegressionModel;
import com.incokalk.service.ml.EtaTrainingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/eta-ml/** applique effectivement le contrôle de rôle.
 *
 * Avant correctif, ces endpoints étaient annotés avec @PreAuthorize alors que la
 * sécurité de méthode Spring (@EnableMethodSecurity) n'est jamais activée dans ce
 * projet — seule l'AOP @RolesAllowed (RolesAllowedAspect) est réellement appliquée.
 * @PreAuthorize était donc silencieusement ignoré et ces endpoints étaient ouverts à
 * n'importe quel utilisateur authentifié, quel que soit son rôle.
 */
class EtaMlControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtaTrainingService etaTrainingService;

    @Test
    @DisplayName("POST /v1/eta-ml/train → 403 pour un rôle USER (train réservé à OWNER/ADMIN)")
    void trainModel_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(post("/v1/eta-ml/train")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/eta-ml/train → 403 pour un rôle MANAGER (train réservé à OWNER/ADMIN)")
    void trainModel_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/eta-ml/train")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/eta-ml/train → 200 pour un rôle ADMIN")
    void trainModel_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        when(etaTrainingService.trainModel(any())).thenReturn(new EtaRegressionModel());

        mockMvc.perform(post("/v1/eta-ml/train")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/eta-ml/model → 403 pour un rôle USER (lecture réservée à OWNER/ADMIN/MANAGER)")
    void getModelInfo_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/eta-ml/model")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/eta-ml/model → 200 pour un rôle MANAGER")
    void getModelInfo_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(etaTrainingService.loadModel(any())).thenReturn(
                new EtaRegressionModel(1.0, Collections.emptyMap(), 0.5, 20));

        mockMvc.perform(get("/v1/eta-ml/model")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }
}
