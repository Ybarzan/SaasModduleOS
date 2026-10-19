package com.fleethub.service;

import com.fleethub.billing.StripeProperties;
import com.fleethub.dto.BillingSummaryDto;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Résumé de facturation pour le tableau de bord "facturation groupée" de
 * Praxio. Le montant/devise/prochaine échéance viennent d'un appel Stripe
 * en lecture seule ({@code Subscription.retrieve}, même principe que
 * {@link com.fleethub.billing.StripeService#changePlan}) -- jamais
 * d'écriture -- avec dégradation gracieuse si Stripe est indisponible ou si
 * la société n'a pas d'abonnement actif.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalBillingSummaryService {

    private final CompanyRepository companyRepository;
    private final StripeProperties stripeProperties;

    @Transactional(readOnly = true)
    public BillingSummaryDto getBillingSummary(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();

        Double amount = null;
        String currency = null;
        LocalDateTime nextBillingDate = null;

        if (stripeProperties.isEnabled() && company.getSubscriptionId() != null) {
            try {
                Subscription subscription = Subscription.retrieve(company.getSubscriptionId());
                com.stripe.model.SubscriptionItem item = subscription.getItems().getData().get(0);
                Price price = item.getPrice();
                if (price.getUnitAmount() != null) {
                    amount = price.getUnitAmount() / 100.0;
                }
                currency = price.getCurrency() != null ? price.getCurrency().toUpperCase() : null;
                if (item.getCurrentPeriodEnd() != null) {
                    nextBillingDate = Instant.ofEpochSecond(item.getCurrentPeriodEnd())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                }
            } catch (StripeException e) {
                log.warn("Facturation groupée : abonnement Stripe illisible pour la société {} : {}",
                        companyId, e.getMessage());
            }
        }

        return new BillingSummaryDto(
                company.getPlan().name(),
                company.getStatus().name(),
                company.getTrialEndsAt(),
                amount,
                currency,
                nextBillingDate,
                stripeProperties.isEnabled());
    }
}
