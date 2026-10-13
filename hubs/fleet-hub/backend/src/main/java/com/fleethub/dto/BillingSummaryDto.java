package com.fleethub.dto;

import java.time.LocalDateTime;

/**
 * Résumé compact pour la facturation groupée cross-hub de Praxio. amount/
 * currency/nextBillingDate restent null si la société n'a pas d'abonnement
 * Stripe actif ou si l'appel Stripe échoue -- dégradation gracieuse, jamais
 * une erreur pour l'appelant.
 */
public record BillingSummaryDto(
        String plan,
        String status,
        LocalDateTime trialEndsAt,
        Double amount,
        String currency,
        LocalDateTime nextBillingDate,
        boolean stripeConfigured
) {}
