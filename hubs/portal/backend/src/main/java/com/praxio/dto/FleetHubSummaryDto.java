package com.praxio.dto;

/** Reflet de InternalDashboardSummaryDto (fleet-hub), lu via GET /api/internal/dashboard-summary. */
public record FleetHubSummaryDto(
        long activeShipments,
        long availableTrucks,
        long openCarrierBookings
) {}
