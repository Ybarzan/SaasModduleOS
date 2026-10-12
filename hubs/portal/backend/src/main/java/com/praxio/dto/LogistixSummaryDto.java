package com.praxio.dto;

/** Reflet de DashboardSummaryDto (Logistix), lu via GET /v1/internal/dashboard-summary. */
public record LogistixSummaryDto(
        long activeWarehouses,
        long openReceivingOrders,
        long openDiscrepancies
) {}
