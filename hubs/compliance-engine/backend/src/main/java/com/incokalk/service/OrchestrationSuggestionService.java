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
 * (voir OrchestrationSuggestion, V65/V66, docs/04-composants-techniques.md).
 * L'approbation declenche immediatement OrchestrationExecutor -- c'est
 * l'approbation humaine elle-meme qui sert de declencheur d'execution, il n'y
 * a pas de file d'attente separee entre APPROVED et l'appel reel au systeme
 * aval (Phase 3 J6-J8 de docs/03-plan-migration.md).
 */
@Service
@RequiredArgsConstructor
public class OrchestrationSuggestionService {

    private final OrchestrationSuggestionRepository suggestionRepo;
    private final OrchestrationExecutor executor;

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
        OrchestrationSuggestion suggestion = getSuggestion(id);
        if (suggestion.getStatus() != OrchestrationSuggestion.Status.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Décision impossible : la suggestion est déjà " + suggestion.getStatus());
        }
        suggestion.setDecidedAt(LocalDateTime.now());
        suggestion.setDecidedByUserId(userId);
        suggestion.setDecisionNote(note);
        suggestion.setStatus(OrchestrationSuggestion.Status.APPROVED);
        executor.execute(suggestion);
        return suggestionRepo.save(suggestion);
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
