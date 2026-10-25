package com.finance.dto.internal;

import java.math.BigDecimal;

/**
 * Agrégats financiers par société, pour MultiBranchService côté
 * compliance-engine (via FinanceIntegrationClient).
 */
public record FinancialTotalsDto(
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin
) {}
