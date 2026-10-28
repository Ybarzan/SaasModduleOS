package com.incokalk.service;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.service.erp.ErpAdjustmentNote;
import com.incokalk.service.erp.ErpExportResult;
import com.incokalk.service.erp.ErpProvider;
import com.incokalk.service.erp.ErpProviderRegistry;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consomme une OrchestrationSuggestion au moment de son approbation et appelle
 * le systeme aval reel correspondant a son actionType. Seul type reconnu pour
 * l'instant : SUGGEST_ERP_ORDER_ADJUSTMENT (docs/03-plan-migration.md, Phase 3
 * J6-J8).
 *
 * <p>Avant le retrait du domaine Transport (commit dc1e04d), ce type appelait
 * ErpProvider.exportOrders(config, List.of(shipment)) avec un ShipmentOrder lu
 * localement -- retiree avec le reste. Recharge desormais l'expedition reelle
 * depuis fleet-hub (FleetHubIntegrationClient) et revalide pour de vrai la
 * gouvernance de la regle (transporteurs autorises, budget maximum) avant
 * toute tentative d'export.</p>
 *
 * <p>L'export lui-meme (ErpProvider.exportAdjustmentNote) ne CREE jamais de
 * commande fournisseur de zero -- ca demanderait de resoudre un partenaire/
 * produit ERP qu'on n'a aucun moyen fiable d'identifier depuis une expedition
 * fleet-hub. Il cherche une commande fournisseur EXISTANTE par reference et y
 * poste une note -- reussite reelle (EXECUTED) si trouvee, repli honnete
 * (NEEDS_MANUAL_EXPORT, jamais EXECUTED) sinon, avec la raison exacte de
 * l'echec d'export dans executionResult. Implemente pour Odoo/QuickBooks
 * (reutilisent un mecanisme d'auth deja prouve par les imports existants) ;
 * SAP renvoie un echec explicite (son auth meme pour les imports n'est jamais
 * verifiee contre un vrai systeme, voir SapProvider).</p>
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
    private final ErpProviderRegistry erpProviderRegistry;

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
        String description = describeAdjustment(shipment, config);

        Optional<ErpProvider> provider = erpProviderRegistry.getProvider(config.getErpType());
        ErpExportResult exportResult = provider.isPresent() && shipment.getOrderNumber() != null
                ? provider.get().exportAdjustmentNote(config, new ErpAdjustmentNote(shipment.getOrderNumber(), description))
                : ErpExportResult.fail(provider.isEmpty()
                        ? "Aucun connecteur disponible pour le type ERP " + config.getErpType()
                        : "Expédition sans référence (orderNumber) -- impossible de retrouver une commande fournisseur");

        if (exportResult.success()) {
            suggestion.setStatus(OrchestrationSuggestion.Status.EXECUTED);
            suggestion.setExecutionResult(exportResult.message() + " -- " + description);
            log.info("[Executor] Suggestion {} EXECUTED : {}", suggestion.getId(), exportResult.message());
        } else {
            suggestion.setStatus(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
            suggestion.setExecutionResult("Export automatique impossible (" + exportResult.message() + ") -- " + description);
            log.info("[Executor] Suggestion {} passée en NEEDS_MANUAL_EXPORT ({})", suggestion.getId(), exportResult.message());
        }
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

    /** Description de l'ajustement -- utilisée à la fois comme note postée sur
     *  la commande fournisseur ERP en cas d'export réussi, et comme texte à
     *  reporter à la main en cas de repli NEEDS_MANUAL_EXPORT. */
    private String describeAdjustment(ShipmentSummary shipment, ErpConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ajustement (").append(config.getErpType())
                .append(" / ").append(config.getName()).append(") : ");
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
        return sb.toString();
    }

    private void fail(OrchestrationSuggestion suggestion, String reason) {
        suggestion.setStatus(OrchestrationSuggestion.Status.FAILED);
        suggestion.setExecutionResult(reason);
        log.warn("[Executor] Suggestion {} passée en FAILED : {}", suggestion.getId(), reason);
    }
}
