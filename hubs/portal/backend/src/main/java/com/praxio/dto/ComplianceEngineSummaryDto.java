package com.praxio.dto;

import java.math.BigDecimal;

/** Reflet de DashboardSummaryDTO (compliance-engine), lu via GET /api/v1/analytics/dashboard-summary. */
public record ComplianceEngineSummaryDto(
        long simulationsThisMonth,
        long openDeclarations,
        long overdueInvoicesCount,
        BigDecimal overdueInvoicesAmount
) {}
