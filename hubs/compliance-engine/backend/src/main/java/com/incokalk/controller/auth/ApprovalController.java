package com.incokalk.controller.auth;

import com.incokalk.model.ApprovalHistory;
import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.ApprovalWorkflow;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval Workflows", description = "Gestion des workflows d'approbation")
@RequiresPlan(Company.Plan.STARTER)
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/workflows")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les workflows d'approbation")
    public ResponseEntity<List<ApprovalWorkflow>> listWorkflows() {
        return ResponseEntity.ok(approvalService.getWorkflows());
    }

    @PostMapping("/workflows")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un workflow d'approbation")
    public ResponseEntity<ApprovalWorkflow> createWorkflow(
            @Valid @RequestBody CreateWorkflow dto) {
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .entityType(dto.getEntityType())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .thresholdAmount(dto.getThresholdAmount())
                .thresholdCurrency(dto.getThresholdCurrency() != null ? dto.getThresholdCurrency() : "EUR")
                .build();
        return ResponseEntity.ok(approvalService.createWorkflow(workflow, dto.getSteps()));
    }

    @PutMapping("/workflows/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour un workflow d'approbation")
    public ResponseEntity<ApprovalWorkflow> updateWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody CreateWorkflow dto) {
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .entityType(dto.getEntityType())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .thresholdAmount(dto.getThresholdAmount())
                .thresholdCurrency(dto.getThresholdCurrency() != null ? dto.getThresholdCurrency() : "EUR")
                .build();
        return ResponseEntity.ok(approvalService.updateWorkflow(id, workflow, dto.getSteps()));
    }

    @DeleteMapping("/workflows/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un workflow d'approbation")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id) {
        approvalService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/requests")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les requêtes d'approbation")
    public ResponseEntity<List<ApprovalRequest>> listRequests() {
        return ResponseEntity.ok(approvalService.getRequests());
    }

    @GetMapping("/requests/my")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Mes requêtes d'approbation")
    public ResponseEntity<List<ApprovalRequest>> myRequests(HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(approvalService.getMyRequests(userId));
    }

    @GetMapping("/requests/pending")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Approbations en attente")
    public ResponseEntity<List<ApprovalRequest>> pendingApprovals() {
        return ResponseEntity.ok(approvalService.getPendingApprovals());
    }

    @PostMapping("/requests")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Créer une requête d'approbation")
    public ResponseEntity<ApprovalRequest> createRequest(
            @Valid @RequestBody CreateRequest dto) {
        ApprovalRequest request = ApprovalRequest.builder()
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .entityReference(dto.getEntityReference())
                .requestedByUserId(dto.getRequestedByUserId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "EUR")
                .notes(dto.getNotes())
                .build();
        return ResponseEntity.ok(approvalService.createRequest(request));
    }

    @PutMapping("/requests/{id}/approve")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Approuver une requête")
    public ResponseEntity<ApprovalRequest> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest dto,
            HttpServletRequest httpReq) {
        String notes = dto != null ? dto.getNotes() : null;
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(approvalService.approve(id, notes, userId));
    }

    @PutMapping("/requests/{id}/reject")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rejeter une requête")
    public ResponseEntity<ApprovalRequest> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest dto,
            HttpServletRequest httpReq) {
        String notes = dto != null ? dto.getNotes() : null;
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(approvalService.reject(id, notes, userId));
    }

    @PutMapping("/requests/{id}/cancel")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Annuler une requête")
    public ResponseEntity<ApprovalRequest> cancel(@PathVariable UUID id, HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(approvalService.cancel(id, userId));
    }

    @GetMapping("/requests/{id}/history")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique d'une requête")
    public ResponseEntity<List<ApprovalHistory>> getRequestHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(approvalService.getRequestHistory(id));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques d'approbation")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(approvalService.getStats());
    }

    // ==================== Inner records ====================

    @Data
    public static class CreateWorkflow {
        private String name;
        private String description;
        private ApprovalWorkflow.EntityType entityType;
        private Boolean isActive;
        private BigDecimal thresholdAmount;
        private String thresholdCurrency;
        private List<CreateStep> steps;
    }

    @Data
    public static class CreateStep {
        private Integer stepOrder;
        private String stepName;
        private String approverRole;
        private UUID approverUserId;
        private Boolean isRequired;
    }

    @Data
    public static class CreateRequest {
        private ApprovalRequest.EntityType entityType;
        private UUID entityId;
        private String entityReference;
        private UUID requestedByUserId;
        private BigDecimal amount;
        private String currency;
        private String notes;
    }

    @Data
    public static class ApproveRequest {
        private String notes;
    }
}
