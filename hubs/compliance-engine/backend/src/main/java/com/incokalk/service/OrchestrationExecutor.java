package com.incokalk.service;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Consomme une OrchestrationSuggestion au moment de son approbation et appelle
 * le systeme aval reel correspondant a son actionType. Seul type reconnu pour
 * l'instant : SUGGEST_ERP_ORDER_ADJUSTMENT (docs/03-plan-migration.md, Phase 3
 * J6-J8).
 *
 * <p>Avant le retrait du domaine Transport (commit dc1e04d), ce type appelait
 * ErpProvider.exportOrders(config, List.of(shipment)) avec un ShipmentOrder lu
 * localement. exportOrders n'existe plus du tout dans ErpProvider -- aucun
 * connecteur d'export vers l'ERP n'a ete reconstruit (voir ErpSyncService :
 * les 3 directions EXPORT concernees restent des no-op "en attente de
 * l'integration fleet-hub"). Plutot que d'inventer un appel ERP qui n'existe
 * pas (EXECUTED mensonger) ou de rejeter systematiquement une suggestion par
 * ailleurs valide (FAILED trompeur), ce qui suit restaure la partie qui PEUT
 * etre faite honnetement :</p>
 * <ul>
 *   <li>recharger l'expedition reelle depuis fleet-hub (FleetHubIntegrationClient,
 *       qui remplace ShipmentOrderRepository) ;</li>
 *   <li>revalider pour de vrai la gouvernance de la regle (transporteurs
 *       autorises, budget maximum) ;</li>
 *   <li>si tout passe, decrire precisement l'ajustement a faire et marquer la
 *       suggestion NEEDS_MANUAL_EXPORT -- jamais EXECUTED.</li>
 * </ul>
 * Construire un vrai connecteur d'export (appel reel a l'API Odoo/SAP/
 * QuickBooks) reste un chantier separe, explicitement hors scope ici : non
 * testable contre un systeme reel depuis cet environnement.
 *
 * <p>Ne leve jamais d'exception : mute le statut de la suggestion et son
 * executionResult ; c'est a l'appelant (OrchestrationSuggestionService) de
 * persister l'entite mutee.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationExecutor {

    private final FleetHubIntegrationClient fleetHubIntegrationClient;
    private final ErpConfigRepository erpConfigRepo;

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

        ShipmentSummary shipment;
        try {
            shipment = fleetHubIntegrationClient.getShipmentSummary(suggestion.getShipmentId(), companyId);
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            fail(suggestion, "Expédition introuvable sur fleet-hub (supprimée depuis la proposition, ou "
                    + "intégration non configurée) : " + e.getMessage());
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

        suggestion.setStatus(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
        suggestion.setExecutionResult(describeAdjustment(shipment, config));
        log.info("[Executor] Suggestion {} passée en NEEDS_MANUAL_EXPORT (expédition et gouvernance validées, "
                + "aucun connecteur d'export ERP disponible)", suggestion.getId());
    }

    /** Renvoie une description de la violation, ou null si la gouvernance est respectée. */
    private String checkGovernance(NotificationRule rule, ShipmentSummary shipment) {
        if (rule.getAllowedCarrierIds() != null && !rule.getAllowedCarrierIds().isBlank()) {
            UUID carrierId = shipment.getCarrierId();
            if (carrierId == null) {
                return "aucun transporteur sur l'expédition alors que la règle restreint les transporteurs autorisés";
            }
            List<String> allowed = Arrays.stream(rule.getAllowedCarrierIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (allowed.stream().noneMatch(id -> id.equalsIgnoreCase(carrierId.toString()))) {
                return "transporteur " + carrierId + " non autorisé pour cette règle";
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

    /** Description lisible par un humain de l'ajustement à faire manuellement
     *  dans l'ERP tant qu'aucun connecteur d'export n'existe. */
    private String describeAdjustment(ShipmentSummary shipment, ErpConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ajustement à reporter manuellement dans ").append(config.getErpType())
                .append(" (").append(config.getName()).append(") : ");
        sb.append(shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "expédition");
        sb.append(" vers ").append(shipment.getConsigneeName() != null ? shipment.getConsigneeName() : "destinataire inconnu");
        if (shipment.getConsigneeCountry() != null) {
            sb.append(" (").append(shipment.getConsigneeCountry()).append(")");
        }
        if (shipment.getGoodsValue() != null) {
            sb.append(", valeur ").append(shipment.getGoodsValue())
                    .append(" ").append(shipment.getCurrency() != null ? shipment.getCurrency() : "");
        }
        if (shipment.getCarrierName() != null) {
            sb.append(", transporteur ").append(shipment.getCarrierName());
        }
        sb.append(" -- export automatique non disponible, voir docs/03-plan-migration.md Phase 3 J6-J8.");
        return sb.toString();
    }

    private void fail(OrchestrationSuggestion suggestion, String reason) {
        suggestion.setStatus(OrchestrationSuggestion.Status.FAILED);
        suggestion.setExecutionResult(reason);
        log.warn("[Executor] Suggestion {} passée en FAILED : {}", suggestion.getId(), reason);
    }
}
