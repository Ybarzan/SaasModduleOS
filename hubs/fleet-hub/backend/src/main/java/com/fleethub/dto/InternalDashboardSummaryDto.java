package com.fleethub.dto;

/**
 * Résumé compact destiné au tableau de bord cross-hub de Praxio -- à ne pas
 * confondre avec {@link DashboardSummaryDto}, le tableau de bord détaillé
 * propre à fleet-hub (KPI flotte/conformité).
 */
public record InternalDashboardSummaryDto(
        long activeShipments,
        long availableTrucks,
        long openCarrierBookings
) {}
