package com.praxio.dto;

import java.time.LocalDateTime;

/** Reflet de BillingSummaryDto (fleet-hub), lu via GET /api/internal/billing-summary. */
public record FleetHubBillingSummaryDto(
        String plan,
        String status,
        LocalDateTime trialEndsAt,
        Double amount,
        String currency,
        LocalDateTime nextBillingDate,
        boolean stripeConfigured
) {}
