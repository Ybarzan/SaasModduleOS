package com.logistix.dto;

/**
 * Résumé compact destiné au tableau de bord cross-hub de Praxio, consommé via
 * la clé d'intégration Praxio (voir {@code PraxioIntegrationService}).
 */
public record DashboardSummaryDto(
        long activeWarehouses,
        long openReceivingOrders,
        long openDiscrepancies
) {}
