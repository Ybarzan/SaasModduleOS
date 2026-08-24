package com.incokalk.controller;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.service.OrchestrationSuggestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie que /v1/orchestration-suggestions/** applique effectivement le
 * controle de role (RolesAllowedAspect, pas @PreAuthorize -- voir la note
 * dans CarrierBookingControllerTest sur ce piege deja rencontre). Approuver
 * une suggestion prepare une future action reelle (ERP, etc.), meme si
 * aucun executeur ne la declenche encore -- meme niveau d'exigence que la
 * soumission d'une reservation transporteur.
 */
class OrchestrationSuggestionControllerTest extends ControllerTestBase {

    @MockBean
    private OrchestrationSuggestionService suggestionService;

    private OrchestrationSuggestion suggestion(UUID id, OrchestrationSuggestion.Status status) {
        Company company = new Company();
        company.setId(companyId);
        NotificationRule rule = new NotificationRule();
        rule.setId(UUID.randomUUID());

        OrchestrationSuggestion s = new OrchestrationSuggestion();
        s.setId(id);
        s.setCompany(company);
        s.setRule(rule);
        s.setActionType("SUGGEST_ERP_ORDER_ADJUSTMENT");
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("GET /v1/orchestration-suggestions → 403 pour un rôle USER")
    void listSuggestions_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/orchestration-suggestions").header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/orchestration-suggestions → 200 pour un rôle MANAGER")
    void listSuggestions_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(suggestionService.listSuggestions()).thenReturn(List.of());

        mockMvc.perform(get("/v1/orchestration-suggestions").header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/orchestration-suggestions/{id}/approve → 403 pour un rôle USER")
    void approve_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/v1/orchestration-suggestions/" + id + "/approve")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/orchestration-suggestions/{id}/approve → 200 pour un rôle MANAGER, ne change que le statut")
    void approve_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        UUID id = UUID.randomUUID();
        OrchestrationSuggestion approved = suggestion(id, OrchestrationSuggestion.Status.APPROVED);
        when(suggestionService.approve(any(), any(), any())).thenReturn(approved);

        mockMvc.perform(post("/v1/orchestration-suggestions/" + id + "/approve")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"OK, montant raisonnable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /v1/orchestration-suggestions/{id}/reject → 403 pour un rôle USER")
    void reject_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/v1/orchestration-suggestions/" + id + "/reject")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/orchestration-suggestions/{id}/reject → 200 pour un rôle ADMIN")
    void reject_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        UUID id = UUID.randomUUID();
        OrchestrationSuggestion rejected = suggestion(id, OrchestrationSuggestion.Status.REJECTED);
        when(suggestionService.reject(any(), any(), any())).thenReturn(rejected);

        mockMvc.perform(post("/v1/orchestration-suggestions/" + id + "/reject")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
