package com.incokalk.controller;

import com.incokalk.model.ApprovalHistory;
import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.ApprovalWorkflow;
import com.incokalk.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovalControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalService approvalService;

    private ApprovalWorkflow sampleWorkflow(String currency, boolean active) {
        return ApprovalWorkflow.builder()
            .id(UUID.randomUUID())
            .name("Workflow 1")
            .entityType(ApprovalWorkflow.EntityType.QUOTE)
            .isActive(active)
            .thresholdCurrency(currency)
            .build();
    }

    private ApprovalRequest sampleRequest(ApprovalRequest.ApprovalStatus status, String currency) {
        return ApprovalRequest.builder()
            .id(UUID.randomUUID())
            .entityType(ApprovalRequest.EntityType.QUOTE)
            .entityId(UUID.randomUUID())
            .requestedByUserId(userId)
            .status(status)
            .currency(currency)
            .build();
    }

    private ApprovalHistory sampleHistory() {
        return ApprovalHistory.builder()
            .id(UUID.randomUUID())
            .stepOrder(1)
            .action(ApprovalHistory.Action.APPROVED)
            .performedByUserId(userId)
            .build();
    }

    // ── GET /v1/approvals/workflows ─────────────────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/workflows → 200 avec la liste des workflows")
    void listWorkflows_success() throws Exception {
        when(approvalService.getWorkflows()).thenReturn(List.of(sampleWorkflow("EUR", true)));

        mockMvc.perform(get("/v1/approvals/workflows")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].thresholdCurrency").value("EUR"));
    }

    // ── POST /v1/approvals/workflows ────────────────────────────────────

    @Test
    @DisplayName("POST /v1/approvals/workflows → 200, isActive et thresholdCurrency fournis (branches non-null)")
    void createWorkflow_withIsActiveAndCurrencyProvided() throws Exception {
        when(approvalService.createWorkflow(any(), any())).thenReturn(sampleWorkflow("USD", false));

        String body = """
            {
              "name": "Workflow gros montants",
              "description": "Validation manager",
              "entityType": "QUOTE",
              "isActive": false,
              "thresholdAmount": 1000.00,
              "thresholdCurrency": "USD",
              "steps": [
                {"stepOrder": 1, "stepName": "Validation", "approverRole": "MANAGER", "isRequired": true}
              ]
            }
            """;

        mockMvc.perform(post("/v1/approvals/workflows")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thresholdCurrency").value("USD"));
    }

    @Test
    @DisplayName("POST /v1/approvals/workflows → 200, isActive et thresholdCurrency absents (branches par défaut)")
    void createWorkflow_withIsActiveAndCurrencyNull() throws Exception {
        when(approvalService.createWorkflow(any(), any())).thenReturn(sampleWorkflow("EUR", true));

        String body = """
            {
              "name": "Workflow standard",
              "entityType": "PURCHASE_ORDER"
            }
            """;

        mockMvc.perform(post("/v1/approvals/workflows")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thresholdCurrency").value("EUR"));
    }

    // ── PUT /v1/approvals/workflows/{id} ────────────────────────────────

    @Test
    @DisplayName("PUT /v1/approvals/workflows/{id} → 200, isActive et thresholdCurrency fournis (branches non-null)")
    void updateWorkflow_withIsActiveAndCurrencyProvided() throws Exception {
        UUID workflowId = UUID.randomUUID();
        when(approvalService.updateWorkflow(eq(workflowId), any(), any())).thenReturn(sampleWorkflow("GBP", true));

        String body = """
            {
              "name": "Workflow modifié",
              "entityType": "EXPENSE_REPORT",
              "isActive": true,
              "thresholdCurrency": "GBP"
            }
            """;

        mockMvc.perform(put("/v1/approvals/workflows/" + workflowId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thresholdCurrency").value("GBP"));
    }

    @Test
    @DisplayName("PUT /v1/approvals/workflows/{id} → 200, isActive et thresholdCurrency absents (branches par défaut)")
    void updateWorkflow_withIsActiveAndCurrencyNull() throws Exception {
        UUID workflowId = UUID.randomUUID();
        when(approvalService.updateWorkflow(eq(workflowId), any(), any())).thenReturn(sampleWorkflow("EUR", true));

        String body = """
            {
              "name": "Workflow modifié",
              "entityType": "CUSTOM"
            }
            """;

        mockMvc.perform(put("/v1/approvals/workflows/" + workflowId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thresholdCurrency").value("EUR"));
    }

    // ── DELETE /v1/approvals/workflows/{id} ─────────────────────────────

    @Test
    @DisplayName("DELETE /v1/approvals/workflows/{id} → 204 succès")
    void deleteWorkflow_success() throws Exception {
        UUID workflowId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/approvals/workflows/" + workflowId)
                .header("Authorization", authHeader()))
            .andExpect(status().isNoContent());
    }

    // ── GET /v1/approvals/requests ──────────────────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/requests → 200 avec la liste des requêtes")
    void listRequests_success() throws Exception {
        when(approvalService.getRequests())
            .thenReturn(List.of(sampleRequest(ApprovalRequest.ApprovalStatus.PENDING, "EUR")));

        mockMvc.perform(get("/v1/approvals/requests")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── GET /v1/approvals/requests/my ───────────────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/requests/my → 200 avec mes requêtes")
    void myRequests_success() throws Exception {
        when(approvalService.getMyRequests(userId))
            .thenReturn(List.of(sampleRequest(ApprovalRequest.ApprovalStatus.APPROVED, "EUR")));

        mockMvc.perform(get("/v1/approvals/requests/my")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    // ── GET /v1/approvals/requests/pending ──────────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/requests/pending → 200 avec les approbations en attente")
    void pendingApprovals_success() throws Exception {
        when(approvalService.getPendingApprovals())
            .thenReturn(List.of(sampleRequest(ApprovalRequest.ApprovalStatus.PENDING, "EUR")));

        mockMvc.perform(get("/v1/approvals/requests/pending")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── POST /v1/approvals/requests ─────────────────────────────────────

    @Test
    @DisplayName("POST /v1/approvals/requests → 200, currency fournie (branche non-null)")
    void createRequest_withCurrencyProvided() throws Exception {
        when(approvalService.createRequest(any())).thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.PENDING, "USD"));

        String body = """
            {
              "entityType": "QUOTE",
              "entityId": "%s",
              "entityReference": "Q-2026-001",
              "requestedByUserId": "%s",
              "amount": 500.00,
              "currency": "USD",
              "notes": "Urgent"
            }
            """.formatted(UUID.randomUUID(), userId);

        mockMvc.perform(post("/v1/approvals/requests")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("POST /v1/approvals/requests → 200, currency absente (branche par défaut EUR)")
    void createRequest_withCurrencyNull() throws Exception {
        when(approvalService.createRequest(any())).thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.PENDING, "EUR"));

        String body = """
            {
              "entityType": "CARRIER_INVOICE",
              "entityId": "%s",
              "requestedByUserId": "%s",
              "amount": 250.00
            }
            """.formatted(UUID.randomUUID(), userId);

        mockMvc.perform(post("/v1/approvals/requests")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("EUR"));
    }

    // ── PUT /v1/approvals/requests/{id}/approve ─────────────────────────

    @Test
    @DisplayName("PUT /v1/approvals/requests/{id}/approve → 200, corps fourni (branche non-null)")
    void approve_withBody() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.approve(eq(requestId), eq("OK approuvé"), eq(userId)))
            .thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.APPROVED, "EUR"));

        mockMvc.perform(put("/v1/approvals/requests/" + requestId + "/approve")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"OK approuvé\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PUT /v1/approvals/requests/{id}/approve → 200, corps absent (branche null)")
    void approve_withoutBody() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.approve(eq(requestId), isNull(), eq(userId)))
            .thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.APPROVED, "EUR"));

        mockMvc.perform(put("/v1/approvals/requests/" + requestId + "/approve")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // ── PUT /v1/approvals/requests/{id}/reject ──────────────────────────

    @Test
    @DisplayName("PUT /v1/approvals/requests/{id}/reject → 200, corps fourni (branche non-null)")
    void reject_withBody() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.reject(eq(requestId), eq("Motif de refus"), eq(userId)))
            .thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.REJECTED, "EUR"));

        mockMvc.perform(put("/v1/approvals/requests/" + requestId + "/reject")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Motif de refus\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PUT /v1/approvals/requests/{id}/reject → 200, corps absent (branche null)")
    void reject_withoutBody() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.reject(eq(requestId), isNull(), eq(userId)))
            .thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.REJECTED, "EUR"));

        mockMvc.perform(put("/v1/approvals/requests/" + requestId + "/reject")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ── PUT /v1/approvals/requests/{id}/cancel ──────────────────────────

    @Test
    @DisplayName("PUT /v1/approvals/requests/{id}/cancel → 200 succès")
    void cancel_success() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.cancel(requestId, userId))
            .thenReturn(sampleRequest(ApprovalRequest.ApprovalStatus.CANCELLED, "EUR"));

        mockMvc.perform(put("/v1/approvals/requests/" + requestId + "/cancel")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── GET /v1/approvals/requests/{id}/history ─────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/requests/{id}/history → 200 avec l'historique")
    void getRequestHistory_success() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(approvalService.getRequestHistory(requestId)).thenReturn(List.of(sampleHistory()));

        mockMvc.perform(get("/v1/approvals/requests/" + requestId + "/history")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("APPROVED"));
    }

    // ── GET /v1/approvals/stats ──────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/approvals/stats → 200 avec les statistiques")
    void getStats_success() throws Exception {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 10L);
        stats.put("pending", 2L);
        stats.put("approved", 7L);
        stats.put("rejected", 1L);
        when(approvalService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/v1/approvals/stats")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(10))
            .andExpect(jsonPath("$.pending").value(2));
    }
}
