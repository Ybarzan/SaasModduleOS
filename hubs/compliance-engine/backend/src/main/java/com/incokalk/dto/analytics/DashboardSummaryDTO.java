package com.incokalk.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Résumé compact destiné au tableau de bord cross-hub de Praxio (consommé via
 * une clé API générale {@code ApiKeyService}, pas une clé dédiée) : un
 * sous-ensemble volontairement restreint de {@link DashboardStatsDTO}, pensé
 * pour être affiché à côté des résumés fleet-hub/Logistix sans exposer le
 * détail métier de compliance-engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    private long simulationsThisMonth;
    private long openDeclarations;
    private long overdueInvoicesCount;
    private BigDecimal overdueInvoicesAmount;
}
