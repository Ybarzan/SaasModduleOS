package com.incokalk.service;

import com.incokalk.controller.auth.ApprovalController;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ApprovalService — Tests unitaires")
class ApprovalServiceTest {

    @Mock ApprovalWorkflowRepository workflowRepo;
    @Mock ApprovalStepRepository stepRepo;
    @Mock ApprovalRequestRepository requestRepo;
    @Mock ApprovalHistoryRepository historyRepo;
    @Mock CompanyRepository companyRepo;
    @Mock RoleChecker roleChecker;
    @InjectMocks ApprovalService service;

    UUID companyId;
    UUID workflowId;
    UUID requestId;
    UUID userId;
    Company company;
    ApprovalWorkflow workflow;
    ApprovalRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        userId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .company(company)
                .name("Test Workflow")
                .entityType(ApprovalWorkflow.EntityType.QUOTE)
                .isActive(true)
                .build();
        request = ApprovalRequest.builder()
                .id(requestId)
                .company(company)
                .entityType(ApprovalRequest.EntityType.QUOTE)
                .entityId(UUID.randomUUID())
                .entityReference("REF-001")
                .status(ApprovalRequest.ApprovalStatus.PENDING)
                .requestedByUserId(userId)
                .currentStep(1)
                .totalSteps(1)
                .build();
    }

    @Test
    @DisplayName("getWorkflows → returns active workflows")
    void getWorkflows() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(workflowRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(workflow));
            assertThat(service.getWorkflows()).hasSize(1);
            assertThat(service.getWorkflows().get(0).getName()).isEqualTo("Test Workflow");
        }
    }

    @Test
    @DisplayName("createWorkflow → success with steps")
    void createWorkflow_withSteps() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(workflowRepo.save(any(ApprovalWorkflow.class))).thenReturn(workflow);
            when(stepRepo.saveAll(anyList())).thenReturn(List.of());

            var stepDto = new ApprovalController.CreateStep();
            stepDto.setStepOrder(1);
            stepDto.setStepName("Manager Approval");
            stepDto.setApproverRole("MANAGER");

            var result = service.createWorkflow(workflow, List.of(stepDto));
            assertThat(result).isNotNull();
            verify(stepRepo).saveAll(anyList());
        }
    }

    @Test
    @DisplayName("createWorkflow → success without steps")
    void createWorkflow_withoutSteps() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(workflowRepo.save(any(ApprovalWorkflow.class))).thenReturn(workflow);

            var result = service.createWorkflow(workflow, null);
            assertThat(result).isNotNull();
            verify(stepRepo, never()).saveAll(anyList());
        }
    }

    @Test
    @DisplayName("createWorkflow → company not found throws")
    void createWorkflow_companyNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.createWorkflow(workflow, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("updateWorkflow → success")
    void updateWorkflow() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(workflowRepo.findByCompanyIdAndId(companyId, workflowId)).thenReturn(Optional.of(workflow));
            when(workflowRepo.save(any(ApprovalWorkflow.class))).thenReturn(workflow);

            var updated = ApprovalWorkflow.builder().name("Updated").build();
            var result = service.updateWorkflow(workflowId, updated, null);
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Updated");
        }
    }

    @Test
    @DisplayName("updateWorkflow → not found throws")
    void updateWorkflow_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(workflowRepo.findByCompanyIdAndId(companyId, workflowId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateWorkflow(workflowId, workflow, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("deleteWorkflow → success")
    void deleteWorkflow() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(workflowRepo.findByCompanyIdAndId(companyId, workflowId)).thenReturn(Optional.of(workflow));
            doNothing().when(workflowRepo).delete(workflow);
            service.deleteWorkflow(workflowId);
            verify(workflowRepo).delete(workflow);
        }
    }

    @Test
    @DisplayName("deleteWorkflow → not found throws")
    void deleteWorkflow_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(workflowRepo.findByCompanyIdAndId(companyId, workflowId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteWorkflow(workflowId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getRequests → returns requests")
    void getRequests() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId)).thenReturn(List.of(request));
            assertThat(service.getRequests()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getMyRequests → filters by current user")
    void getMyRequests() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var myRequest = ApprovalRequest.builder().requestedByUserId(userId).build();
            var otherRequest = ApprovalRequest.builder().requestedByUserId(UUID.randomUUID()).build();
            when(requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId))
                    .thenReturn(List.of(myRequest, otherRequest));
            assertThat(service.getMyRequests(userId)).hasSize(1);
        }
    }

    @Test
    @DisplayName("getPendingApprovals → returns pending requests")
    void getPendingApprovals() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.PENDING))
                    .thenReturn(List.of(request));
            assertThat(service.getPendingApprovals()).hasSize(1);
        }
    }

    @Test
    @DisplayName("createRequest → success with workflow")
    void createRequest() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(workflowRepo.findByCompanyIdAndEntityTypeAndIsActiveTrue(companyId, ApprovalWorkflow.EntityType.QUOTE))
                    .thenReturn(Optional.of(workflow));
            when(stepRepo.findByWorkflowIdOrderByStepOrder(workflowId)).thenReturn(List.of());
            when(requestRepo.save(any(ApprovalRequest.class))).thenReturn(request);

            var result = service.createRequest(request);
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING);
        }
    }

    @Test
    @DisplayName("createRequest → company not found throws")
    void createRequest_companyNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.createRequest(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("approve → advances to next step")
    void approve_advances() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var multiStepReq = ApprovalRequest.builder()
                    .id(requestId).company(company)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .currentStep(1).totalSteps(2).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(multiStepReq));
            when(requestRepo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.approve(requestId, "Approved step 1", userId);
            assertThat(result.getCurrentStep()).isEqualTo(2);
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING);
            assertThat(result.getDecidedByUserId()).isEqualTo(userId);
            verify(historyRepo).save(any(ApprovalHistory.class));
        }
    }

    @Test
    @DisplayName("approve → final step approves")
    void approve_finalStep() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(request));
            when(requestRepo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.approve(requestId, "Final approval", userId);
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.APPROVED);
        }
    }

    @Test
    @DisplayName("approve → not found throws")
    void approve_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.approve(requestId, "notes", userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("approve → not pending throws")
    void approve_notPending() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var approvedReq = ApprovalRequest.builder().id(requestId).status(ApprovalRequest.ApprovalStatus.APPROVED).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(approvedReq));
            assertThatThrownBy(() -> service.approve(requestId, "notes", userId))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("approve → étape avec approbateur désigné différent de l'utilisateur → refus (403)")
    void approve_wrongDesignatedApprover_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID designatedApproverId = UUID.randomUUID();
            var step = ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflow(workflow)
                    .stepOrder(1)
                    .stepName("Validation finance")
                    .approverUserId(designatedApproverId)
                    .build();
            var reqWithWorkflow = ApprovalRequest.builder()
                    .id(requestId).company(company).workflow(workflow)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .currentStep(1).totalSteps(1).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(reqWithWorkflow));
            when(stepRepo.findByWorkflowIdOrderByStepOrder(workflowId)).thenReturn(List.of(step));

            assertThatThrownBy(() -> service.approve(requestId, "notes", userId))
                    .isInstanceOf(SecurityException.class);
            verify(requestRepo, never()).save(any());
        }
    }

    @Test
    @DisplayName("approve → approbateur désigné correspondant → succès")
    void approve_matchingDesignatedApprover_succeeds() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var step = ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflow(workflow)
                    .stepOrder(1)
                    .stepName("Validation finance")
                    .approverUserId(userId)
                    .build();
            var reqWithWorkflow = ApprovalRequest.builder()
                    .id(requestId).company(company).workflow(workflow)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .currentStep(1).totalSteps(1).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(reqWithWorkflow));
            when(stepRepo.findByWorkflowIdOrderByStepOrder(workflowId)).thenReturn(List.of(step));
            when(requestRepo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.approve(requestId, "notes", userId);
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.APPROVED);
        }
    }

    @Test
    @DisplayName("approve → étape avec rôle requis que l'utilisateur n'a pas → refus (403)")
    void approve_insufficientRole_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var step = ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflow(workflow)
                    .stepOrder(1)
                    .stepName("Validation admin")
                    .approverRole("ADMIN")
                    .build();
            var reqWithWorkflow = ApprovalRequest.builder()
                    .id(requestId).company(company).workflow(workflow)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .currentStep(1).totalSteps(1).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(reqWithWorkflow));
            when(stepRepo.findByWorkflowIdOrderByStepOrder(workflowId)).thenReturn(List.of(step));
            when(roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)).thenReturn(false);

            assertThatThrownBy(() -> service.approve(requestId, "notes", userId))
                    .isInstanceOf(SecurityException.class);
            verify(requestRepo, never()).save(any());
        }
    }

    @Test
    @DisplayName("reject → success")
    void reject() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(request));
            when(requestRepo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.reject(requestId, "Rejected by manager", userId);
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.REJECTED);
            assertThat(result.getDecisionNotes()).isEqualTo("Rejected by manager");
            assertThat(result.getDecidedByUserId()).isEqualTo(userId);
            verify(historyRepo).save(any(ApprovalHistory.class));
        }
    }

    @Test
    @DisplayName("reject → not found throws")
    void reject_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.reject(requestId, "notes", userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("reject → approbateur désigné différent de l'utilisateur → refus (403)")
    void reject_wrongDesignatedApprover_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID designatedApproverId = UUID.randomUUID();
            var step = ApprovalStep.builder()
                    .id(UUID.randomUUID())
                    .workflow(workflow)
                    .stepOrder(1)
                    .stepName("Validation finance")
                    .approverUserId(designatedApproverId)
                    .build();
            var reqWithWorkflow = ApprovalRequest.builder()
                    .id(requestId).company(company).workflow(workflow)
                    .status(ApprovalRequest.ApprovalStatus.PENDING)
                    .currentStep(1).totalSteps(1).build();
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(reqWithWorkflow));
            when(stepRepo.findByWorkflowIdOrderByStepOrder(workflowId)).thenReturn(List.of(step));

            assertThatThrownBy(() -> service.reject(requestId, "notes", userId))
                    .isInstanceOf(SecurityException.class);
            verify(requestRepo, never()).save(any());
        }
    }

    @Test
    @DisplayName("cancel → success")
    void cancel() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(request));
            when(requestRepo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.cancel(requestId, userId);
            assertThat(result.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.CANCELLED);
            verify(historyRepo).save(any(ApprovalHistory.class));
        }
    }

    @Test
    @DisplayName("getStats → returns counts")
    void getStats() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId)).thenReturn(List.of(request, request));
            when(requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.PENDING)).thenReturn(1L);
            when(requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.APPROVED)).thenReturn(1L);
            when(requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.REJECTED)).thenReturn(0L);

            Map<String, Object> stats = service.getStats();
            assertThat(stats.get("total")).isEqualTo(2L);
            assertThat(stats.get("pending")).isEqualTo(1L);
            assertThat(stats.get("approved")).isEqualTo(1L);
            assertThat(stats.get("rejected")).isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("getRequestHistory → returns history")
    void getRequestHistory() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.of(request));
            var history = ApprovalHistory.builder()
                    .request(request)
                    .action(ApprovalHistory.Action.APPROVED)
                    .performedByUserId(userId)
                    .build();
            when(historyRepo.findByRequestIdOrderByPerformedAtAsc(requestId)).thenReturn(List.of(history));
            assertThat(service.getRequestHistory(requestId)).hasSize(1);
        }
    }

    @Test
    @DisplayName("getRequestHistory → requête d'une autre société → exception (pas de fuite cross-tenant)")
    void getRequestHistory_wrongCompany_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            // La requête existe mais appartient à une autre société : le repo scopé ne la retourne pas.
            when(requestRepo.findByCompanyIdAndId(companyId, requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRequestHistory(requestId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(historyRepo, never()).findByRequestIdOrderByPerformedAtAsc(any());
        }
    }
}
