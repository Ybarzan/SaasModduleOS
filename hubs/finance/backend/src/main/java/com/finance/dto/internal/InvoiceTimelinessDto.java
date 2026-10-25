package com.finance.dto.internal;

/**
 * Alimente le CTQ "Ponctualité de facturation" de QualityMetricsService côté
 * compliance-engine (via FinanceIntegrationClient) : opportunities = factures
 * émises (hors brouillon), defects = factures non réglées en retard de paiement.
 */
public record InvoiceTimelinessDto(
        long opportunities,
        long defects
) {}
