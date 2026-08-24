package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.repository.OrchestrationSuggestionRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Flux de decision humaine sur les propositions creees par NotificationService
 * (voir OrchestrationSuggestion, V65, docs/04-composants-techniques.md). Ce
 * service ne fait qu'approuver/rejeter -- il n'execute jamais rien de reel
 * (pas d'appel a ErpProvider ou autre systeme aval). L'executeur qui
 * consommerait une suggestion APPROVED est un chantier separe, pas encore
 * construit (Phase 3 J6-J8 de docs/03-plan-migration.md).
 */
@Service
@RequiredArgsConstructor
public class OrchestrationSuggestionService {

    private final OrchestrationSuggestionRepository suggestionRepo;

    @Transactional(readOnly = true)
    public List<OrchestrationSuggestion> listSuggestions() {
        return suggestionRepo.findByCompanyIdOrderByCreatedAtDesc(TenantContext.get());
    }

    @Transactional(readOnly = true)
    public Page<OrchestrationSuggestion> listSuggestions(Pageable pageable) {
        return suggestionRepo.findByCompanyIdOrderByCreatedAtDesc(TenantContext.get(), pageable);
    }

    @Transactional(readOnly = true)
    public List<OrchestrationSuggestion> listByStatus(OrchestrationSuggestion.Status status) {
        return suggestionRepo.findByCompanyIdAndStatusOrderByCreatedAtDesc(TenantContext.get(), status);
    }

    @Transactional(readOnly = true)
    public OrchestrationSuggestion getSuggestion(UUID id) {
        return suggestionRepo.findByIdAndCompanyId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion non trouvée"));
    }

    @Transactional
    public OrchestrationSuggestion approve(UUID id, UUID userId, String note) {
        return decide(id, userId, note, OrchestrationSuggestion.Status.APPROVED);
    }

    @Transactional
    public OrchestrationSuggestion reject(UUID id, UUID userId, String note) {
        return decide(id, userId, note, OrchestrationSuggestion.Status.REJECTED);
    }

    private OrchestrationSuggestion decide(UUID id, UUID userId, String note, OrchestrationSuggestion.Status decision) {
        OrchestrationSuggestion suggestion = getSuggestion(id);
        if (suggestion.getStatus() != OrchestrationSuggestion.Status.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Décision impossible : la suggestion est déjà " + suggestion.getStatus());
        }
        suggestion.setStatus(decision);
        suggestion.setDecidedAt(LocalDateTime.now());
        suggestion.setDecidedByUserId(userId);
        suggestion.setDecisionNote(note);
        return suggestionRepo.save(suggestion);
    }
}
