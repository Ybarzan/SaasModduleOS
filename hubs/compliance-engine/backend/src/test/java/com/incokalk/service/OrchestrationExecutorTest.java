package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SUGGEST_ERP_ORDER_ADJUSTMENT appelait ErpProvider.exportOrders(config,
 * List.of(shipment)) avec un ShipmentOrder lu localement, avant le retrait du
 * domaine Transport. exportOrders n'existe plus du tout dans ErpProvider et
 * aucun connecteur d'export vers l'ERP n'a été reconstruit (voir
 * ErpSyncService) -- restaurer un appel ERP réel reste un chantier séparé, non
 * testable contre un vrai système depuis cet environnement.
 *
 * Ce qui est restauré ici et testé en conditions réelles : recharger
 * l'expédition depuis fleet-hub (FleetHubIntegrationClient), revalider la
 * gouvernance de la règle (transporteurs autorisés, budget), et si tout passe,
 * décrire l'ajustement à faire plutôt que de mentir sur un appel ERP qui n'a
 * pas eu lieu -- NEEDS_MANUAL_EXPORT, jamais EXECUTED.
 */
@DisplayName("OrchestrationExecutor — Tests unitaires")
class OrchestrationExecutorTest {

    private FleetHubIntegrationClient fleetHubIntegrationClient;
    private ErpConfigRepository erpConfigRepo;
    private OrchestrationExecutor executor;

    private UUID companyId, shipmentId, ruleId;
    private Company company;
    private NotificationRule rule;

    @BeforeEach
    void setUp() {
        fleetHubIntegrationClient = mock(FleetHubIntegrationClient.class);
        erpConfigRepo = mock(ErpConfigRepository.class);
        executor = new OrchestrationExecutor(fleetHubIntegrationClient, erpConfigRepo);

        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
        ruleId = UUID.randomUUID();

        company = Company.builder().id(companyId).build();
        rule = NotificationRule.builder().id(ruleId).company(company)
                .name("ETA dégradé").eventType("SHIPMENT_STATUS_CHANGE")
                .actionType("SUGGEST_ERP_ORDER_ADJUSTMENT").build();
    }

    private OrchestrationSuggestion suggestion() {
        return OrchestrationSuggestion.builder()
                .id(UUID.randomUUID()).company(company).rule(rule).shipmentId(shipmentId)
                .actionType("SUGGEST_ERP_ORDER_ADJUSTMENT")
                .status(OrchestrationSuggestion.Status.APPROVED)
                .build();
    }

    private ShipmentSummary.ShipmentSummaryBuilder shipment() {
        return ShipmentSummary.builder()
                .goodsDescription("Pièces détachées")
                .consigneeName("Client X").consigneeCountry("DE")
                .goodsValue(5000.0).currency("EUR")
                .carrierId(UUID.randomUUID()).carrierName("DHL")
                .quotedCost(1200.0);
    }

    private ErpConfig erpConfig() {
        return ErpConfig.builder().id(UUID.randomUUID()).company(company)
                .erpType("ODOO").name("Odoo Prod").isActive(true).build();
    }

    @Test
    @DisplayName("SUGGEST_ERP_ORDER_ADJUSTMENT, expédition + gouvernance OK, ERP configuré → NEEDS_MANUAL_EXPORT avec description")
    void execute_erpOrderAdjustment_validatesAndNeedsManualExport() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(shipment().build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
        assertThat(s.getExecutionResult()).contains("ODOO").contains("Pièces détachées").contains("Client X")
                .contains("DHL").contains("export automatique non disponible");
    }

    @Test
    @DisplayName("Expédition introuvable sur fleet-hub → FAILED, jamais d'exception propagée")
    void execute_shipmentNotFoundOnFleetHub_marksFailed() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("404"));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("introuvable");
    }

    @Test
    @DisplayName("Transporteur non autorisé par la règle → FAILED, jamais consulté l'ERP")
    void execute_carrierNotAllowed_marksFailed() {
        UUID carrierId = UUID.randomUUID();
        rule.setAllowedCarrierIds(UUID.randomUUID().toString()); // un autre transporteur
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().carrierId(carrierId).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("non autorisé");
        org.mockito.Mockito.verifyNoInteractions(erpConfigRepo);
    }

    @Test
    @DisplayName("Transporteur autorisé par la règle (liste explicite) → passe la gouvernance")
    void execute_carrierAllowed_passesGovernance() {
        UUID carrierId = UUID.randomUUID();
        rule.setAllowedCarrierIds(UUID.randomUUID() + "," + carrierId);
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().carrierId(carrierId).build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
    }

    @Test
    @DisplayName("Aucun transporteur sur l'expédition alors que la règle en restreint → FAILED")
    void execute_noCarrierButRuleRestricts_marksFailed() {
        rule.setAllowedCarrierIds(UUID.randomUUID().toString());
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().carrierId(null).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("aucun transporteur");
    }

    @Test
    @DisplayName("Coût au-delà du budget maximum de la règle → FAILED")
    void execute_overBudget_marksFailed() {
        rule.setMaxBudgetAmount(BigDecimal.valueOf(500));
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().quotedCost(1200.0).finalCost(null).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("dépasse le budget maximum");
    }

    @Test
    @DisplayName("finalCost prioritaire sur quotedCost pour le contrôle de budget")
    void execute_usesFinalCostOverQuotedCost() {
        rule.setMaxBudgetAmount(BigDecimal.valueOf(1000));
        // quotedCost dans le budget, mais finalCost (réel, prioritaire) le dépasse.
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().quotedCost(800.0).finalCost(1500.0).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("1500.0");
    }

    @Test
    @DisplayName("Aucune configuration ERP active → FAILED")
    void execute_noActiveErpConfig_marksFailed() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(shipment().build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("Aucune configuration ERP active");
    }

    @Test
    @DisplayName("Aucune expédition associée (shipmentId null) → FAILED")
    void execute_noShipmentId_marksFailed() {
        OrchestrationSuggestion s = suggestion();
        s.setShipmentId(null);

        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("Aucune expédition associée");
    }

    @Test
    @DisplayName("Type d'action quelconque → FAILED, non pris en charge")
    void execute_anyActionType_marksFailedNotSupported() {
        OrchestrationSuggestion s = suggestion();
        s.setActionType("SOMETHING_ELSE");

        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("non pris en charge");
    }

    @Test
    @DisplayName("Type d'action null → FAILED sans exception")
    void execute_nullActionType_marksFailedWithoutThrowing() {
        OrchestrationSuggestion s = suggestion();
        s.setActionType(null);

        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
    }
}
