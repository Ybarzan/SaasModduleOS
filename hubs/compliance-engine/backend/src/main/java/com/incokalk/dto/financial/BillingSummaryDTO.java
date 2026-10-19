package com.incokalk.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Résumé compact pour la facturation groupée cross-hub de Praxio (clé API
 * générale, même principe que DashboardSummaryDTO) : le montant est dérivé
 * du catalogue statique des plans (BillingService#getPlans), jamais d'appel
 * Stripe -- compliance-engine ne connaît que l'abonnement mis en cache par
 * les webhooks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSummaryDTO {

    private String plan;
    private String status;
    private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime nextBillingDate;
    private boolean cancelAtPeriodEnd;
}
