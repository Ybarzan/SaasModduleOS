package com.incokalk.service;

import com.incokalk.model.Company;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SUGGEST_ERP_ORDER_ADJUSTMENT appelait ErpProvider.exportOrders(config,
 * List.of(shipment)) avec un ShipmentOrder lu localement, avant le retrait du
 * domaine Transport. Restauré via ErpProvider.exportAdjustmentNote (2026-10-28,
 * voir ErpProvider/OdooProvider/QuickBooksProvider) : recharge l'expédition
 * depuis fleet-hub, revalide la gouvernance, puis cherche une commande
 * fournisseur EXISTANTE par référence pour y poster une note -- EXECUTED si
 * trouvée, NEEDS_MANUAL_EXPORT (jamais EXECUTED) sinon, avec la raison exacte
 * dans executionResult.
 */
@DisplayName("OrchestrationExecutor — Tests unitaires")
class OrchestrationExecutorTest {

    private FleetHubIntegrationClient fleetHubIntegrationClient;
    private ErpConfigRepository erpConfigRepo;
    private ErpProviderRegistry erpProviderRegistry;
    private ErpProvider erpProvider;
    private OrchestrationExecutor executor;

    private UUID companyId, shipmentId, ruleId;
    private Company company;
    private NotificationRule rule;

    @BeforeEach
    void setUp() {
        fleetHubIntegrationClient = mock(FleetHubIntegrationClient.class);
        erpConfigRepo = mock(ErpConfigRepository.class);
        erpProviderRegistry = mock(ErpProviderRegistry.class);
        erpProvider = mock(ErpProvider.class);
        executor = new OrchestrationExecutor(fleetHubIntegrationClient, erpConfigRepo, erpProviderRegistry);

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
                .orderNumber("SHP-001")
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
    @DisplayName("export réussi (commande fournisseur trouvée) → EXECUTED avec la référence ERP")
    void execute_exportSucceeds_marksExecuted() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(shipment().build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));
        when(erpProviderRegistry.getProvider("ODOO")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportAdjustmentNote(any(), eq(new ErpAdjustmentNote("SHP-001",
                "Ajustement (ODOO / Odoo Prod) : Pièces détachées vers Client X (DE), valeur 5000.0 EUR, transporteur DHL"))))
                .thenReturn(ErpExportResult.ok("PO-42", "Note ajoutée à la commande fournisseur Odoo PO-42"));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.EXECUTED);
        assertThat(s.getExecutionResult()).contains("PO-42").contains("Pièces détachées");
    }

    @Test
    @DisplayName("aucune commande fournisseur correspondante trouvée → NEEDS_MANUAL_EXPORT, jamais EXECUTED")
    void execute_exportFindsNoMatch_needsManualExport() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(shipment().build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));
        when(erpProviderRegistry.getProvider("ODOO")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportAdjustmentNote(any(), any()))
                .thenReturn(ErpExportResult.fail("Aucune commande fournisseur Odoo trouvée avec origin=SHP-001"));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
        assertThat(s.getExecutionResult()).contains("Aucune commande fournisseur Odoo trouvée")
                .contains("Pièces détachées");
    }

    @Test
    @DisplayName("aucun connecteur enregistré pour le type ERP → NEEDS_MANUAL_EXPORT, jamais consulté le provider")
    void execute_noProviderForErpType_needsManualExport() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(shipment().build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));
        when(erpProviderRegistry.getProvider("ODOO")).thenReturn(Optional.empty());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
        assertThat(s.getExecutionResult()).contains("Aucun connecteur disponible pour le type ERP ODOO");
    }

    @Test
    @DisplayName("expédition sans référence (orderNumber) → NEEDS_MANUAL_EXPORT, jamais consulté le provider")
    void execute_shipmentWithoutOrderNumber_needsManualExport() {
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenReturn(shipment().orderNumber(null).build());
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig()));
        when(erpProviderRegistry.getProvider("ODOO")).thenReturn(Optional.of(erpProvider));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.NEEDS_MANUAL_EXPORT);
        assertThat(s.getExecutionResult()).contains("sans référence");
        org.mockito.Mockito.verifyNoInteractions(erpProvider);
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

        // Aucun provider ODOO enregistré dans ce test (registry non stubbé -> Optional.empty())
        // -> repli NEEDS_MANUAL_EXPORT, mais la gouvernance a bien été franchie (pas FAILED).
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
