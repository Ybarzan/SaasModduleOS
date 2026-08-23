package com.incokalk.service;

import com.incokalk.controller.auth.ApprovalController;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalWorkflowRepository workflowRepo;
    private final ApprovalStepRepository stepRepo;
    private final ApprovalRequestRepository requestRepo;
    private final ApprovalHistoryRepository historyRepo;
    private final CompanyRepository companyRepo;
    private final RoleChecker roleChecker;

    @Transactional(readOnly = true)
    public List<ApprovalWorkflow> getWorkflows() {
        UUID companyId = TenantContext.get();
        return workflowRepo.findByCompanyIdAndIsActiveTrue(companyId);
    }

    @Transactional
    public ApprovalWorkflow createWorkflow(ApprovalWorkflow workflow, List<ApprovalController.CreateStep> stepDtos) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));
        workflow.setCompany(company);

        ApprovalWorkflow saved = workflowRepo.save(workflow);

        if (stepDtos != null && !stepDtos.isEmpty()) {
            List<ApprovalStep> steps = stepDtos.stream().map(dto -> {
                ApprovalStep step = new ApprovalStep();
                step.setWorkflow(saved);
                step.setStepOrder(dto.getStepOrder());
                step.setStepName(dto.getStepName());
                step.setApproverRole(dto.getApproverRole());
                step.setApproverUserId(dto.getApproverUserId());
                step.setRequired(dto.getIsRequired() != null ? dto.getIsRequired() : true);
                return step;
            }).toList();
            stepRepo.saveAll(steps);
        }

        log.info("[Approval] Workflow créé: {} pour company={}", saved.getName(), companyId);
        return saved;
    }

    @Transactional
    public ApprovalWorkflow updateWorkflow(UUID id, ApprovalWorkflow updated, List<ApprovalController.CreateStep> stepDtos) {
        UUID companyId = TenantContext.get();
        ApprovalWorkflow existing = workflowRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow non trouvé"));

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setEntityType(updated.getEntityType());
        existing.setActive(updated.isActive());
        existing.setThresholdAmount(updated.getThresholdAmount());
        existing.setThresholdCurrency(updated.getThresholdCurrency());

        if (stepDtos != null) {
            List<ApprovalStep> oldSteps = stepRepo.findByWorkflowIdOrderByStepOrder(existing.getId());
            stepRepo.deleteAll(oldSteps);

            List<ApprovalStep> newSteps = stepDtos.stream().map(dto -> {
                ApprovalStep step = new ApprovalStep();
                step.setWorkflow(existing);
                step.setStepOrder(dto.getStepOrder());
                step.setStepName(dto.getStepName());
                step.setApproverRole(dto.getApproverRole());
                step.setApproverUserId(dto.getApproverUserId());
                step.setRequired(dto.getIsRequired() != null ? dto.getIsRequired() : true);
                return step;
            }).toList();
            stepRepo.saveAll(newSteps);
        }

        return workflowRepo.save(existing);
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        UUID companyId = TenantContext.get();
        ApprovalWorkflow workflow = workflowRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow non trouvé"));
        workflowRepo.delete(workflow);
        log.info("[Approval] Workflow supprimé: {} pour company={}", id, companyId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getRequests() {
        UUID companyId = TenantContext.get();
        return requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getMyRequests(UUID userId) {
        UUID companyId = TenantContext.get();
        List<ApprovalRequest> all = requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId);
        return all.stream()
                .filter(r -> userId.equals(r.getRequestedByUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingApprovals() {
        UUID companyId = TenantContext.get();
        return requestRepo.findByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.PENDING);
    }

    @Transactional
    public ApprovalRequest createRequest(ApprovalRequest request) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));
        request.setCompany(company);

        Optional<ApprovalWorkflow> workflowOpt = workflowRepo
                .findByCompanyIdAndEntityTypeAndIsActiveTrue(companyId,
                    ApprovalWorkflow.EntityType.valueOf(request.getEntityType().name()));
        workflowOpt.ifPresent(request::setWorkflow);

        if (workflowOpt.isPresent()) {
            List<ApprovalStep> steps = stepRepo.findByWorkflowIdOrderByStepOrder(workflowOpt.get().getId());
            request.setTotalSteps(steps.isEmpty() ? 1 : steps.size());
        }

        ApprovalRequest saved = requestRepo.save(request);
        log.info("[Approval] Requête créée: {} pour company={}", saved.getEntityReference(), companyId);
        return saved;
    }

    @Transactional
    public ApprovalRequest approve(UUID requestId, String notes, UUID userId) {
        UUID companyId = TenantContext.get();
        ApprovalRequest request = requestRepo.findByCompanyIdAndId(companyId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Requête non trouvée"));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Seules les requêtes en attente peuvent être approuvées");
        }

        assertAuthorizedApprover(request, userId, companyId);

        request.setDecisionNotes(notes);
        request.setDecidedByUserId(userId);
        request.setDecidedAt(LocalDateTime.now());

        if (request.getCurrentStep() >= request.getTotalSteps()) {
            request.setStatus(ApprovalRequest.ApprovalStatus.APPROVED);
            log.info("[Approval] Requête {} approuvée (final)", requestId);
        } else {
            request.setCurrentStep(request.getCurrentStep() + 1);
            log.info("[Approval] Requête {} avancée à l'étape {}", requestId, request.getCurrentStep());
        }

        ApprovalHistory history = ApprovalHistory.builder()
                .request(request)
                .stepOrder(request.getCurrentStep())
                .action(ApprovalHistory.Action.APPROVED)
                .performedByUserId(userId)
                .notes(notes)
                .build();
        historyRepo.save(history);

        return requestRepo.save(request);
    }

    @Transactional
    public ApprovalRequest reject(UUID requestId, String notes, UUID userId) {
        UUID companyId = TenantContext.get();
        ApprovalRequest request = requestRepo.findByCompanyIdAndId(companyId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Requête non trouvée"));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Seules les requêtes en attente peuvent être rejetées");
        }

        assertAuthorizedApprover(request, userId, companyId);

        request.setStatus(ApprovalRequest.ApprovalStatus.REJECTED);
        request.setDecisionNotes(notes);
        request.setDecidedByUserId(userId);
        request.setDecidedAt(LocalDateTime.now());

        ApprovalHistory history = ApprovalHistory.builder()
                .request(request)
                .stepOrder(request.getCurrentStep())
                .action(ApprovalHistory.Action.REJECTED)
                .performedByUserId(userId)
                .notes(notes)
                .build();
        historyRepo.save(history);

        log.info("[Approval] Requête {} rejetée", requestId);
        return requestRepo.save(request);
    }

    @Transactional
    public ApprovalRequest cancel(UUID requestId, UUID userId) {
        UUID companyId = TenantContext.get();
        ApprovalRequest request = requestRepo.findByCompanyIdAndId(companyId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Requête non trouvée"));

        if (request.getStatus() != ApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Seules les requêtes en attente peuvent être annulées");
        }

        request.setStatus(ApprovalRequest.ApprovalStatus.CANCELLED);

        ApprovalHistory history = ApprovalHistory.builder()
                .request(request)
                .stepOrder(request.getCurrentStep())
                .action(ApprovalHistory.Action.CANCELLED)
                .performedByUserId(userId)
                .build();
        historyRepo.save(history);

        log.info("[Approval] Requête {} annulée", requestId);
        return requestRepo.save(request);
    }

    /**
     * Vérifie que l'utilisateur qui approuve/rejette est bien l'approbateur désigné pour
     * l'étape courante du workflow (approverUserId précis, ou approverRole sinon). Si la
     * requête n'a pas de workflow rattaché, ou que l'étape courante n'a pas d'approbateur
     * défini, aucune restriction supplémentaire n'est appliquée au-delà du rôle vérifié par
     * {@code @RolesAllowed} sur le contrôleur (le modèle ne permet pas d'être plus strict).
     */
    private void assertAuthorizedApprover(ApprovalRequest request, UUID userId, UUID companyId) {
        ApprovalWorkflow workflow = request.getWorkflow();
        if (workflow == null) {
            return;
        }
        List<ApprovalStep> steps = stepRepo.findByWorkflowIdOrderByStepOrder(workflow.getId());
        ApprovalStep currentStep = steps.stream()
                .filter(s -> Objects.equals(s.getStepOrder(), request.getCurrentStep()))
                .findFirst()
                .orElse(null);
        if (currentStep == null) {
            return;
        }
        if (currentStep.getApproverUserId() != null) {
            if (!currentStep.getApproverUserId().equals(userId)) {
                throw new SecurityException("Vous n'êtes pas l'approbateur désigné pour cette étape");
            }
            return;
        }
        if (currentStep.getApproverRole() != null && !currentStep.getApproverRole().isBlank()) {
            CompanyRole.Role required = CompanyRole.Role.valueOf(currentStep.getApproverRole());
            if (!roleChecker.hasRole(userId, companyId, required)) {
                throw new SecurityException("Votre rôle ne permet pas d'approuver cette étape");
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        long total = requestRepo.findByCompanyIdOrderByRequestedAtDesc(companyId).size();
        long pending = requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.PENDING);
        long approved = requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.APPROVED);
        long rejected = requestRepo.countByCompanyIdAndStatus(companyId, ApprovalRequest.ApprovalStatus.REJECTED);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        return stats;
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> getRequestHistory(UUID requestId) {
        UUID companyId = TenantContext.get();
        requestRepo.findByCompanyIdAndId(companyId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Requête non trouvée"));
        return historyRepo.findByRequestIdOrderByPerformedAtAsc(requestId);
    }
}
