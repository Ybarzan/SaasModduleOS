package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.service.erp.ErpProvider;
import com.incokalk.service.erp.ErpProviderRegistry;
import com.incokalk.service.erp.ErpSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consomme une OrchestrationSuggestion au moment de son approbation et
 * appelle le systeme aval reel correspondant a son actionType. Seul type
 * reconnu pour l'instant : SUGGEST_ERP_ORDER_ADJUSTMENT -> ErpProvider.exportOrders
 * sur l'unique expedition concernee (docs/03-plan-migration.md, Phase 3 J6-J8).
 *
 * Un seul appel externe par execution ici, donc pas de pattern de saga/
 * compensation necessaire -- a revoir des qu'un deuxieme type d'action
 * coordonne plusieurs systemes (cf. docs/04-composants-techniques.md).
 *
 * Ne leve jamais d'exception : mute le statut de la suggestion (EXECUTED/FAILED)
 * et son executionResult ; c'est a l'appelant (OrchestrationSuggestionService)
 * de persister l'entite mutee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationExecutor {

    private final ShipmentOrderRepository shipmentOrderRepo;
    private final ErpConfigRepository erpConfigRepo;
    private final ErpProviderRegistry providerRegistry;

    public void execute(OrchestrationSuggestion suggestion) {
        try {
            switch (suggestion.getActionType()) {
                case "SUGGEST_ERP_ORDER_ADJUSTMENT" -> executeErpOrderAdjustment(suggestion);
                default -> fail(suggestion, "Type d'action non pris en charge par l'exécuteur : "
                        + suggestion.getActionType());
            }
        } catch (Exception e) {
            log.error("[Executor] Échec inattendu pour la suggestion {}: {}", suggestion.getId(), e.getMessage(), e);
            fail(suggestion, "Erreur inattendue : " + e.getMessage());
        }
    }

    private void executeErpOrderAdjustment(OrchestrationSuggestion suggestion) {
        UUID companyId = suggestion.getCompany().getId();

        if (suggestion.getShipmentId() == null) {
            fail(suggestion, "Aucune expédition associée à la suggestion");
            return;
        }
        ShipmentOrder shipment = shipmentOrderRepo
                .findByIdAndCompanyId(suggestion.getShipmentId(), companyId)
                .orElse(null);
        if (shipment == null) {
            fail(suggestion, "Expédition introuvable (peut-être supprimée depuis la proposition)");
            return;
        }

        String governanceViolation = checkGovernance(suggestion.getRule(), shipment);
        if (governanceViolation != null) {
            fail(suggestion, "Contrainte de gouvernance non respectée : " + governanceViolation);
            return;
        }

        List<ErpConfig> activeConfigs = erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId);
        if (activeConfigs.isEmpty()) {
            fail(suggestion, "Aucune configuration ERP active pour cette entreprise");
            return;
        }
        ErpConfig config = activeConfigs.get(0);
        Optional<ErpProvider> provider = providerRegistry.getProvider(config.getErpType());
        if (provider.isEmpty()) {
            fail(suggestion, "Aucun connecteur disponible pour le type ERP " + config.getErpType());
            return;
        }

        ErpSyncResult result = provider.get().exportOrders(config, List.of(shipment));
        if (result != null && result.isSuccess()) {
            suggestion.setStatus(OrchestrationSuggestion.Status.EXECUTED);
            suggestion.setExecutionResult("Synchronisé vers " + config.getErpType() + " (" + config.getName() + ")");
        } else {
            String reason = result != null ? result.getErrorMessage() : "réponse vide du connecteur ERP";
            fail(suggestion, "Échec de synchronisation ERP : " + reason);
        }
    }

    /** Renvoie une description de la violation, ou null si la gouvernance est respectée. */
    private String checkGovernance(NotificationRule rule, ShipmentOrder shipment) {
        if (rule.getAllowedCarrierIds() != null && !rule.getAllowedCarrierIds().isBlank()) {
            Carrier carrier = shipment.getCarrier();
            if (carrier == null) {
                return "aucun transporteur sur l'expédition alors que la règle restreint les transporteurs autorisés";
            }
            List<String> allowed = Arrays.stream(rule.getAllowedCarrierIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (allowed.stream().noneMatch(id -> id.equalsIgnoreCase(carrier.getId().toString()))) {
                return "transporteur " + carrier.getId() + " non autorisé pour cette règle";
            }
        }

        if (rule.getMaxBudgetAmount() != null) {
            Double cost = shipment.getFinalCost() != null ? shipment.getFinalCost() : shipment.getQuotedCost();
            if (cost != null && BigDecimal.valueOf(cost).compareTo(rule.getMaxBudgetAmount()) > 0) {
                return "coût de l'expédition (" + cost + ") dépasse le budget maximum autorisé ("
                        + rule.getMaxBudgetAmount() + ")";
            }
        }

        return null;
    }

    private void fail(OrchestrationSuggestion suggestion, String reason) {
        suggestion.setStatus(OrchestrationSuggestion.Status.FAILED);
        suggestion.setExecutionResult(reason);
        log.warn("[Executor] Suggestion {} passée en FAILED : {}", suggestion.getId(), reason);
    }
}
