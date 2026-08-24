package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.repository.OrchestrationSuggestionRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrchestrationSuggestionService — Tests unitaires")
class OrchestrationSuggestionServiceTest {

    @Mock OrchestrationSuggestionRepository suggestionRepo;
    @Mock OrchestrationExecutor executor;

    private OrchestrationSuggestionService service;

    private UUID companyId, userId, suggestionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OrchestrationSuggestionService(suggestionRepo, executor);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        suggestionId = UUID.randomUUID();
        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OrchestrationSuggestion pending() {
        OrchestrationSuggestion s = new OrchestrationSuggestion();
        s.setId(suggestionId);
        s.setActionType("SUGGEST_ERP_ORDER_ADJUSTMENT");
        s.setStatus(OrchestrationSuggestion.Status.PENDING_APPROVAL);
        return s;
    }

    @Test
    @DisplayName("listSuggestions : délègue au repo avec le companyId courant")
    void listSuggestions_delegatesWithTenantCompanyId() {
        when(suggestionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());

        assertThat(service.listSuggestions()).isEmpty();
        verify(suggestionRepo).findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Test
    @DisplayName("getSuggestion : introuvable dans le tenant courant → ResourceNotFoundException")
    void getSuggestion_notFound_throws() {
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSuggestion(suggestionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("approve : PENDING_APPROVAL → APPROVED, horodate, attribue la décision, déclenche l'exécuteur")
    void approve_pending_transitionsToApproved() {
        OrchestrationSuggestion suggestion = pending();
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.of(suggestion));
        when(suggestionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        OrchestrationSuggestion result = service.approve(suggestionId, userId, "Montant raisonnable");

        assertThat(result.getStatus()).isEqualTo(OrchestrationSuggestion.Status.APPROVED);
        assertThat(result.getDecidedByUserId()).isEqualTo(userId);
        assertThat(result.getDecisionNote()).isEqualTo("Montant raisonnable");
        assertThat(result.getDecidedAt()).isNotNull();
        verify(executor).execute(suggestion);
    }

    @Test
    @DisplayName("approve : l'exécuteur mute le statut vers EXECUTED, la décision persiste ce résultat")
    void approve_executorMarksExecuted_persistsFinalStatus() {
        OrchestrationSuggestion suggestion = pending();
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.of(suggestion));
        when(suggestionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.doAnswer(inv -> {
            OrchestrationSuggestion s = inv.getArgument(0);
            s.setStatus(OrchestrationSuggestion.Status.EXECUTED);
            s.setExecutionResult("Synchronisé vers odoo (Odoo prod)");
            return null;
        }).when(executor).execute(any());

        OrchestrationSuggestion result = service.approve(suggestionId, userId, null);

        assertThat(result.getStatus()).isEqualTo(OrchestrationSuggestion.Status.EXECUTED);
        assertThat(result.getExecutionResult()).isEqualTo("Synchronisé vers odoo (Odoo prod)");
    }

    @Test
    @DisplayName("reject : PENDING_APPROVAL → REJECTED")
    void reject_pending_transitionsToRejected() {
        OrchestrationSuggestion suggestion = pending();
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.of(suggestion));
        when(suggestionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        OrchestrationSuggestion result = service.reject(suggestionId, userId, "Coût trop élevé");

        assertThat(result.getStatus()).isEqualTo(OrchestrationSuggestion.Status.REJECTED);
        assertThat(result.getDecisionNote()).isEqualTo("Coût trop élevé");
    }

    @Test
    @DisplayName("approve : déjà décidée → IllegalStateException, ne re-sauvegarde rien")
    void approve_alreadyDecided_throws() {
        OrchestrationSuggestion suggestion = pending();
        suggestion.setStatus(OrchestrationSuggestion.Status.REJECTED);
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.approve(suggestionId, userId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
        verify(suggestionRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("reject : déjà exécutée → IllegalStateException")
    void reject_alreadyExecuted_throws() {
        OrchestrationSuggestion suggestion = pending();
        suggestion.setStatus(OrchestrationSuggestion.Status.EXECUTED);
        when(suggestionRepo.findByIdAndCompanyId(suggestionId, companyId)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.reject(suggestionId, userId, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("listByStatus : délègue au repo avec companyId + statut")
    void listByStatus_delegates() {
        when(suggestionRepo.findByCompanyIdAndStatusOrderByCreatedAtDesc(
                eq(companyId), eq(OrchestrationSuggestion.Status.PENDING_APPROVAL))).thenReturn(List.of(pending()));

        List<OrchestrationSuggestion> result = service.listByStatus(OrchestrationSuggestion.Status.PENDING_APPROVAL);

        assertThat(result).hasSize(1);
    }
}
