package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.service.erp.ErpProvider;
import com.incokalk.service.erp.ErpProviderRegistry;
import com.incokalk.service.erp.ErpSyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrchestrationExecutor — Tests unitaires")
class OrchestrationExecutorTest {

    @Mock ShipmentOrderRepository shipmentOrderRepo;
    @Mock ErpConfigRepository erpConfigRepo;
    @Mock ErpProviderRegistry providerRegistry;
    @Mock ErpProvider erpProvider;

    private OrchestrationExecutor executor;

    private UUID companyId, shipmentId, ruleId;
    private Company company;
    private NotificationRule rule;
    private ShipmentOrder shipment;
    private ErpConfig erpConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = new OrchestrationExecutor(shipmentOrderRepo, erpConfigRepo, providerRegistry);

        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
        ruleId = UUID.randomUUID();

        company = Company.builder().id(companyId).build();
        rule = NotificationRule.builder().id(ruleId).company(company)
                .name("ETA dégradé").eventType("SHIPMENT_STATUS_CHANGE")
                .actionType("SUGGEST_ERP_ORDER_ADJUSTMENT").build();
        shipment = ShipmentOrder.builder().id(shipmentId).company(company).build();
        erpConfig = ErpConfig.builder().id(UUID.randomUUID()).company(company)
                .erpType("odoo").name("Odoo prod").isActive(true).build();
    }

    private OrchestrationSuggestion suggestion() {
        return OrchestrationSuggestion.builder()
                .id(UUID.randomUUID()).company(company).rule(rule).shipmentId(shipmentId)
                .actionType("SUGGEST_ERP_ORDER_ADJUSTMENT")
                .status(OrchestrationSuggestion.Status.APPROVED)
                .build();
    }

    @Test
    @DisplayName("ERP export réussi → EXECUTED avec un résumé du résultat")
    void execute_erpExportSucceeds_marksExecuted() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig));
        when(providerRegistry.getProvider("odoo")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportOrders(eq(erpConfig), eq(List.of(shipment))))
                .thenReturn(ErpSyncResult.builder().success(true).recordsTotal(1).recordsSynced(1).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.EXECUTED);
        assertThat(s.getExecutionResult()).contains("odoo").contains("Odoo prod");
    }

    @Test
    @DisplayName("ERP export échoue → FAILED avec le message d'erreur du connecteur")
    void execute_erpExportFails_marksFailed() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig));
        when(providerRegistry.getProvider("odoo")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportOrders(any(), any()))
                .thenReturn(ErpSyncResult.builder().success(false).errorMessage("Timeout Odoo").build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("Timeout Odoo");
    }

    @Test
    @DisplayName("Aucune expédition liée → FAILED, aucun appel ERP")
    void execute_noShipmentId_marksFailedWithoutErpCall() {
        OrchestrationSuggestion s = suggestion();
        s.setShipmentId(null);

        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        verify(providerRegistry, never()).getProvider(any());
    }

    @Test
    @DisplayName("Expédition introuvable (supprimée depuis la proposition) → FAILED")
    void execute_shipmentDeleted_marksFailed() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.empty());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("introuvable");
    }

    @Test
    @DisplayName("Aucune configuration ERP active → FAILED")
    void execute_noActiveErpConfig_marksFailed() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("Aucune configuration ERP active");
    }

    @Test
    @DisplayName("Aucun connecteur pour le type ERP configuré → FAILED")
    void execute_noProviderForErpType_marksFailed() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig));
        when(providerRegistry.getProvider("odoo")).thenReturn(Optional.empty());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
    }

    @Test
    @DisplayName("Type d'action inconnu → FAILED sans toucher aux repos")
    void execute_unknownActionType_marksFailedWithoutSideEffects() {
        OrchestrationSuggestion s = suggestion();
        s.setActionType("SOMETHING_ELSE");

        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        verify(shipmentOrderRepo, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    @DisplayName("Exception inattendue pendant l'exécution → FAILED, ne remonte jamais")
    void execute_unexpectedException_marksFailedInsteadOfThrowing() {
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId))
                .thenThrow(new RuntimeException("DB indisponible"));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("DB indisponible");
    }

    @Test
    @DisplayName("Gouvernance — transporteur non autorisé → FAILED, aucun appel ERP")
    void execute_carrierNotAllowed_marksFailedWithoutErpCall() {
        UUID allowedCarrierId = UUID.randomUUID();
        UUID actualCarrierId = UUID.randomUUID();
        rule.setAllowedCarrierIds(allowedCarrierId.toString());
        shipment.setCarrier(Carrier.builder().id(actualCarrierId).build());
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("non autorisé");
        verify(erpConfigRepo, never()).findByCompanyIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("Gouvernance — transporteur autorisé explicitement → exécution continue")
    void execute_carrierAllowed_proceedsToErpCall() {
        UUID carrierId = UUID.randomUUID();
        rule.setAllowedCarrierIds(UUID.randomUUID() + "," + carrierId);
        shipment.setCarrier(Carrier.builder().id(carrierId).build());
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig));
        when(providerRegistry.getProvider("odoo")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportOrders(any(), any())).thenReturn(ErpSyncResult.builder().success(true).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.EXECUTED);
    }

    @Test
    @DisplayName("Gouvernance — aucun transporteur sur l'expédition alors que la règle en restreint → FAILED")
    void execute_ruleRestrictsCarriersButShipmentHasNone_marksFailed() {
        rule.setAllowedCarrierIds(UUID.randomUUID().toString());
        shipment.setCarrier(null);
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("aucun transporteur");
    }

    @Test
    @DisplayName("Gouvernance — coût dépasse le budget maximum → FAILED, aucun appel ERP")
    void execute_costExceedsBudget_marksFailedWithoutErpCall() {
        rule.setMaxBudgetAmount(new BigDecimal("500.00"));
        shipment.setQuotedCost(750.0);
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("dépasse le budget");
        verify(erpConfigRepo, never()).findByCompanyIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("Gouvernance — finalCost prime sur quotedCost pour le contrôle budgétaire")
    void execute_budgetCheck_prefersFinalCostOverQuotedCost() {
        rule.setMaxBudgetAmount(new BigDecimal("500.00"));
        shipment.setQuotedCost(100.0);
        shipment.setFinalCost(900.0);
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("dépasse le budget");
    }

    @Test
    @DisplayName("Gouvernance — coût dans le budget → exécution continue")
    void execute_costWithinBudget_proceedsToErpCall() {
        rule.setMaxBudgetAmount(new BigDecimal("500.00"));
        shipment.setQuotedCost(250.0);
        when(shipmentOrderRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(erpConfigRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(erpConfig));
        when(providerRegistry.getProvider("odoo")).thenReturn(Optional.of(erpProvider));
        when(erpProvider.exportOrders(any(), any())).thenReturn(ErpSyncResult.builder().success(true).build());

        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.EXECUTED);
        verify(erpProvider, times(1)).exportOrders(any(), any());
    }
}
