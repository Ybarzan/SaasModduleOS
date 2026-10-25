package com.praxio.dto;

import java.math.BigDecimal;

/** Reflet de DashboardSummaryDto (Finance), lu via GET /v1/internal/dashboard-summary. */
public record FinanceSummaryDto(
        long overdueInvoicesCount,
        BigDecimal overdueInvoicesAmount
) {}
