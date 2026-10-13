package com.fleethub.service;

import com.fleethub.billing.StripeProperties;
import com.fleethub.dto.BillingSummaryDto;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InternalBillingSummaryService — Tests unitaires")
class InternalBillingSummaryServiceTest {

    CompanyRepository companyRepo;
    StripeProperties stripeProperties;
    InternalBillingSummaryService service;
    Long companyId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        stripeProperties = new StripeProperties();
        service = new InternalBillingSummaryService(companyRepo, stripeProperties);
        companyId = 42L;
    }

    @Test
    @DisplayName("getBillingSummary → reflète plan/status/trialEndsAt sans appel Stripe si pas d'abonnement actif")
    void getBillingSummary_withoutSubscriptionId_skipsStripe() {
        LocalDateTime trialEndsAt = LocalDateTime.now().plusDays(3);
        Company company = Company.builder()
                .id(companyId)
                .plan(Company.SubscriptionPlan.TRIAL)
                .status(Company.CompanyStatus.TRIAL)
                .trialEndsAt(trialEndsAt)
                .subscriptionId(null)
                .build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        BillingSummaryDto dto = service.getBillingSummary(companyId);

        assertThat(dto.plan()).isEqualTo("TRIAL");
        assertThat(dto.status()).isEqualTo("TRIAL");
        assertThat(dto.trialEndsAt()).isEqualTo(trialEndsAt);
        assertThat(dto.amount()).isNull();
        assertThat(dto.currency()).isNull();
        assertThat(dto.nextBillingDate()).isNull();
        assertThat(dto.stripeConfigured()).isFalse();
    }

    @Test
    @DisplayName("getBillingSummary → ignore Stripe si désactivé, même avec un abonnement renseigné")
    void getBillingSummary_stripeDisabled_skipsStripeEvenWithSubscriptionId() {
        Company company = Company.builder()
                .id(companyId)
                .plan(Company.SubscriptionPlan.PRO)
                .status(Company.CompanyStatus.ACTIVE)
                .subscriptionId("sub_123")
                .build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        stripeProperties.setEnabled(false);

        BillingSummaryDto dto = service.getBillingSummary(companyId);

        assertThat(dto.plan()).isEqualTo("PRO");
        assertThat(dto.amount()).isNull();
        assertThat(dto.stripeConfigured()).isFalse();
    }
}
