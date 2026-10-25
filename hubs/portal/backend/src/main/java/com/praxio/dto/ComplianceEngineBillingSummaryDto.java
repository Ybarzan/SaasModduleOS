package com.praxio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Reflet de BillingSummaryDTO (compliance-engine), lu via GET /v1/billing/summary. */
public record ComplianceEngineBillingSummaryDto(
        String plan,
        String status,
        String billingCycle,
        BigDecimal amount,
        String currency,
        LocalDateTime nextBillingDate,
        boolean cancelAtPeriodEnd
) {}
