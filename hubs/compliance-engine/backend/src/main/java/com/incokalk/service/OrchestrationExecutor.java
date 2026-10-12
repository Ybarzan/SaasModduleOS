package com.incokalk.service;

import com.incokalk.model.OrchestrationSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Consomme une OrchestrationSuggestion au moment de son approbation et appelle
 * le systeme aval reel correspondant a son actionType.
 *
 * <p>Aucun type d'action n'est actuellement implemente : le seul type qui
 * existait, SUGGEST_ERP_ORDER_ADJUSTMENT (ErpProvider.exportOrders sur une
 * expedition, docs/03-plan-migration.md Phase 3 J6-J8), a ete retire avec le
 * domaine Transport -- il reposait entierement sur ShipmentOrderRepository et
 * la relation Carrier d'une expedition. Toute suggestion arrivant ici echoue
 * donc systematiquement (statut FAILED) jusqu'a ce qu'un nouveau type d'action
 * (hors Transport) soit branche. C'est un gap produit pre-existant que ce
 * refactor rend simplement visible, pas quelque chose que cette passe doit
 * combler.</p>
 *
 * <p>Ne leve jamais d'exception : mute le statut de la suggestion (FAILED) et
 * son executionResult ; c'est a l'appelant (OrchestrationSuggestionService) de
 * persister l'entite mutee.</p>
 */
@Slf4j
@Service
public class OrchestrationExecutor {

    public void execute(OrchestrationSuggestion suggestion) {
        try {
            fail(suggestion, "Type d'action non pris en charge par l'exécuteur : "
                    + suggestion.getActionType());
        } catch (Exception e) {
            log.error("[Executor] Échec inattendu pour la suggestion {}: {}", suggestion.getId(), e.getMessage(), e);
            fail(suggestion, "Erreur inattendue : " + e.getMessage());
        }
    }

    private void fail(OrchestrationSuggestion suggestion, String reason) {
        suggestion.setStatus(OrchestrationSuggestion.Status.FAILED);
        suggestion.setExecutionResult(reason);
        log.warn("[Executor] Suggestion {} passée en FAILED : {}", suggestion.getId(), reason);
    }
}
