package com.finance.dto.internal;

import java.math.BigDecimal;

/**
 * Agrégats carbone par société, pour MultiBranchService et
 * MobileDashboardService côté compliance-engine (via FinanceIntegrationClient).
 */
public record CarbonTotalsDto(
        BigDecimal co2EmissionsKg,
        BigDecimal offsetCreditsRetired,
        BigDecimal offsetTotalCost
) {}
