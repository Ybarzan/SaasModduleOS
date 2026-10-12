package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.OrchestrationSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUGGEST_ERP_ORDER_ADJUSTMENT était le seul type d'action implémenté
 * (ErpProvider.exportOrders sur une expédition) ; il a été retiré avec le
 * domaine Transport, dont il dépendait entièrement (ShipmentOrderRepository,
 * la relation Carrier d'une expédition). Aucun type d'action n'est donc plus
 * implémenté : toute suggestion échoue systématiquement — gap produit
 * pré-existant que ce refactor rend visible, pas comblé ici.
 */
@DisplayName("OrchestrationExecutor — Tests unitaires")
class OrchestrationExecutorTest {

    private OrchestrationExecutor executor;

    private UUID companyId, shipmentId, ruleId;
    private Company company;
    private NotificationRule rule;

    @BeforeEach
    void setUp() {
        executor = new OrchestrationExecutor();

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

    @Test
    @DisplayName("SUGGEST_ERP_ORDER_ADJUSTMENT (ex-seul type implémenté) → FAILED, non pris en charge")
    void execute_erpOrderAdjustment_marksFailedNotSupported() {
        OrchestrationSuggestion s = suggestion();
        executor.execute(s);

        assertThat(s.getStatus()).isEqualTo(OrchestrationSuggestion.Status.FAILED);
        assertThat(s.getExecutionResult()).contains("non pris en charge");
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
