package com.finance.dto.internal;

import java.math.BigDecimal;

/**
 * Résumé compact destiné au tableau de bord cross-hub de Praxio et à
 * AnalyticsService côté compliance-engine (via FinanceIntegrationClient).
 */
public record DashboardSummaryDto(
        long overdueInvoicesCount,
        BigDecimal overdueInvoicesAmount
) {}
