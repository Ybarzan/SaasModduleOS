package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.Invoice;
import com.incokalk.model.Subscription;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.InvoiceRepository;
import com.incokalk.repository.SubscriptionRepository;
import com.incokalk.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BillingService — Tests unitaires")
class BillingServiceTest {

    @Mock SubscriptionRepository subscriptionRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock CompanyRepository companyRepo;
    @Mock UserRepository userRepo;

    @InjectMocks BillingService service;

    private UUID companyId, userId, subId, invoiceId;
    private Company company;
    private User user;
    private Subscription subscription;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // @Value fields
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_xxx");
        ReflectionTestUtils.setField(service, "stripeWebhookSecret", "whsec_xxx");
        ReflectionTestUtils.setField(service, "successUrl", "https://app.test/success");
        ReflectionTestUtils.setField(service, "cancelUrl", "https://app.test/cancel");
        ReflectionTestUtils.setField(service, "priceMonthlyStarter", "price_starter_monthly");
        ReflectionTestUtils.setField(service, "priceAnnualStarter", "price_starter_annual");
        ReflectionTestUtils.setField(service, "priceMonthlyPro", "price_pro_monthly");
        ReflectionTestUtils.setField(service, "priceMonthlyEnterprise", "price_ent_monthly");
        ReflectionTestUtils.setField(service, "priceAnnualPro", "price_pro_annual");
        ReflectionTestUtils.setField(service, "priceAnnualEnterprise", "price_ent_annual");

        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        subId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();

        company = Company.builder()
                .id(companyId)
                .name("TestCo")
                .slug("testco")
                .plan(Company.Plan.FREE)
                .isActive(true)
                .build();
        user = User.builder()
                .id(userId)
                .email("user@test.com")
                .fullName("User")
                .password("enc")
                .active(true)
                .emailVerified(true)
                .company(company)
                .build();
        subscription = Subscription.builder()
                .id(subId)
                .company(company)
                .user(user)
                .plan(Subscription.Plan.PRO)
                .status(Subscription.Status.ACTIVE)
                .billingCycle(Subscription.BillingCycle.MONTHLY)
                .stripeSubscriptionId("sub_stripe_123")
                .stripeCustomerId("cus_stripe_123")
                .stripePriceId("price_pro_monthly")
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                .build();
        invoice = Invoice.builder()
                .id(invoiceId)
                .company(company)
                .subscription(subscription)
                .stripeInvoiceId("in_stripe_001")
                .amountCents(4900L)
                .currency("EUR")
                .status(Invoice.Status.PAID)
                .description("Pro plan monthly")
                .invoiceUrl("https://stripe.com/invoice/1")
                .invoicePdfUrl("https://stripe.com/invoice/1.pdf")
                .paidAt(LocalDateTime.now())
                .build();
    }

    // ── isStripeConfigured ───────────────────────────────────────────────

    @Test
    @DisplayName("isStripeConfigured → true quand clé définie")
    void isStripeConfigured_true() {
        assertThat(service.isStripeConfigured()).isTrue();
    }

    @Test
    @DisplayName("isStripeConfigured → false quand clé absente")
    void isStripeConfigured_false() {
        ReflectionTestUtils.setField(service, "stripeSecretKey", "");
        assertThat(service.isStripeConfigured()).isFalse();
    }

    // ── getPlans ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPlans → 4 plans (free, starter, pro, enterprise) aux prix marketing")
    void getPlans_returnsFourPlans() {
        List<Map<String, Object>> plans = service.getPlans();
        assertThat(plans).hasSize(4);
        assertThat(plans.get(0)).containsEntry("id", "free")
                .containsEntry("priceMonthly", 0);
        assertThat(plans.get(1)).containsEntry("id", "starter")
                .containsEntry("priceMonthly", 29)
                .containsEntry("priceAnnual", 290)
                .containsKey("valueProp")
                .containsKey("hook");
        assertThat(plans.get(2)).containsEntry("id", "pro")
                .containsEntry("priceMonthly", 149)
                .containsEntry("priceAnnual", 1519)
                .containsEntry("recommended", true);
        assertThat(plans.get(3)).containsEntry("id", "enterprise")
                .containsEntry("priceMonthly", 499)
                .containsEntry("priceAnnual", 5089);
    }

    // ── createCheckoutSession ────────────────────────────────────────────

    @Test
    @DisplayName("createCheckoutSession → échec si Stripe non configuré")
    void createCheckoutSession_stripeNotConfigured_throws() throws StripeException {
        ReflectionTestUtils.setField(service, "stripeSecretKey", "");
        assertThatThrownBy(() -> service.createCheckoutSession(companyId, userId, "pro", "monthly"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stripe");
        verifyNoInteractions(companyRepo, userRepo);
    }

    @Test
    @DisplayName("createCheckoutSession → company introuvable")
    void createCheckoutSession_companyNotFound_throws() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createCheckoutSession(companyId, userId, "pro", "monthly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Société");
    }

    @Test
    @DisplayName("createCheckoutSession → user introuvable")
    void createCheckoutSession_userNotFound_throws() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createCheckoutSession(companyId, userId, "pro", "monthly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Utilisateur");
    }

    @Test
    @DisplayName("createCheckoutSession → plan inconnu")
    void createCheckoutSession_unknownPlan_throws() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.createCheckoutSession(companyId, userId, "unknown", "monthly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan inconnu");
    }

    @Test
    @DisplayName("createCheckoutSession → succès pro monthly (réutilise customer existant)")
    void createCheckoutSession_proMonthly_success() throws StripeException {
        company.setStripeCustomerId("cus_existing");
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_001");
        when(mockSession.getUrl()).thenReturn("https://stripe.com/checkout/cs_test_001");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenReturn(mockSession);

            Map<String, String> result = service.createCheckoutSession(companyId, userId, "pro", "monthly");

            assertThat(result).containsEntry("sessionId", "cs_test_001")
                    .containsEntry("url", "https://stripe.com/checkout/cs_test_001");
        }
    }

    @Test
    @DisplayName("createCheckoutSession → applique un essai de 14 jours (annoncé sur la page tarifs, jamais honoré avant ce fix)")
    void createCheckoutSession_appliesFourteenDayTrial() throws StripeException {
        company.setStripeCustomerId("cus_existing");
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_001");
        when(mockSession.getUrl()).thenReturn("https://stripe.com/checkout/cs_test_001");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            ArgumentCaptor<com.stripe.param.checkout.SessionCreateParams> captor =
                    ArgumentCaptor.forClass(com.stripe.param.checkout.SessionCreateParams.class);
            sessionMock.when(() -> Session.create(captor.capture())).thenReturn(mockSession);

            service.createCheckoutSession(companyId, userId, "starter", "monthly");

            assertThat(captor.getValue().getSubscriptionData()).isNotNull();
            assertThat(captor.getValue().getSubscriptionData().getTrialPeriodDays()).isEqualTo(14L);
        }
    }

    @Test
    @DisplayName("createCheckoutSession → succès enterprise annual (crée customer)")
    void createCheckoutSession_enterpriseAnnual_createsCustomer() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_new_001");

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_002");
        when(mockSession.getUrl()).thenReturn("https://stripe.com/checkout/cs_test_002");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            customerMock.when(() -> Customer.create(any(CustomerCreateParams.class))).thenReturn(mockCustomer);
            sessionMock.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenReturn(mockSession);

            Map<String, String> result = service.createCheckoutSession(companyId, userId, "enterprise", "annual");

            assertThat(result.get("sessionId")).isEqualTo("cs_test_002");
            customerMock.verify(() -> Customer.create(any(CustomerCreateParams.class)));
            sessionMock.verify(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)));
        }
    }

    // ── createPortalSession ──────────────────────────────────────────────

    @Test
    @DisplayName("createPortalSession → échec si Stripe non configuré")
    void createPortalSession_stripeNotConfigured_throws() throws StripeException {
        ReflectionTestUtils.setField(service, "stripeSecretKey", "");
        assertThatThrownBy(() -> service.createPortalSession(companyId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("createPortalSession → company introuvable")
    void createPortalSession_companyNotFound_throws() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createPortalSession(companyId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createPortalSession → pas de customer Stripe sur la société")
    void createPortalSession_noStripeCustomer_throws() throws StripeException {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        assertThatThrownBy(() -> service.createPortalSession(companyId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aucun abonnement");
    }

    @Test
    @DisplayName("createPortalSession → succès avec URL portail")
    void createPortalSession_success() throws StripeException {
        company.setStripeCustomerId("cus_existing");
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        var mockPortal = mock(com.stripe.model.billingportal.Session.class);
        when(mockPortal.getUrl()).thenReturn("https://stripe.com/portal/cs_001");

        try (MockedStatic<com.stripe.model.billingportal.Session> portalMock = mockStatic(com.stripe.model.billingportal.Session.class)) {
            portalMock.when(() -> com.stripe.model.billingportal.Session.create(any(com.stripe.param.billingportal.SessionCreateParams.class)))
                    .thenReturn(mockPortal);

            Map<String, String> result = service.createPortalSession(companyId);
            assertThat(result).containsEntry("url", "https://stripe.com/portal/cs_001");
        }
    }

    // ── getSubscription ──────────────────────────────────────────────────

    @Test
    @DisplayName("getSubscription → aucune subscription existante → FREE par défaut")
    void getSubscription_empty_returnsFreeDefault() {
        when(subscriptionRepo.findByCompanyId(companyId)).thenReturn(Optional.empty());
        Map<String, Object> result = service.getSubscription(companyId);
        assertThat(result).containsEntry("plan", "FREE")
                .containsEntry("status", "ACTIVE")
                .containsEntry("billingCycle", "MONTHLY")
                .containsEntry("cancelAtPeriodEnd", false);
    }

    @Test
    @DisplayName("getSubscription → subscription PRO existante → map complète")
    void getSubscription_existing_returnsFullMap() {
        when(subscriptionRepo.findByCompanyId(companyId)).thenReturn(Optional.of(subscription));
        Map<String, Object> result = service.getSubscription(companyId);
        assertThat(result).containsEntry("plan", "PRO")
                .containsEntry("status", "ACTIVE")
                .containsEntry("billingCycle", "MONTHLY")
                .containsEntry("cancelAtPeriodEnd", false)
                .containsKey("currentPeriodStart")
                .containsKey("currentPeriodEnd");
    }

    // ── getInvoices ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvoices → liste vide")
    void getInvoices_empty() {
        when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());
        assertThat(service.getInvoices(companyId)).isEmpty();
    }

    @Test
    @DisplayName("getInvoices → invoices mappées (champs clés présents)")
    void getInvoices_mapped() {
        when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(invoice));
        List<Map<String, Object>> result = service.getInvoices(companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("stripeInvoiceId", "in_stripe_001")
                .containsEntry("amountCents", 4900L)
                .containsEntry("currency", "EUR")
                .containsEntry("status", "PAID");
    }

    // ── handleCheckoutCompleted ──────────────────────────────────────────

    @Test
    @DisplayName("handleCheckoutCompleted → session null → no-op")
    void handleCheckoutCompleted_nullSession_noOp() {
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        service.handleCheckoutCompleted(event);
        verifyNoInteractions(companyRepo, userRepo);
    }

    @Test
    @DisplayName("handleCheckoutCompleted → metadata manquante → skip")
    void handleCheckoutCompleted_missingMetadata_skip() {
        Session session = mock(Session.class);
        when(session.getMetadata()).thenReturn(null);
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) session));

        service.handleCheckoutCompleted(event);
        verifyNoInteractions(companyRepo, userRepo);
    }

    @Test
    @DisplayName("handleCheckoutCompleted → success met à jour company + subscription")
    void handleCheckoutCompleted_success() throws StripeException {
        Session session = mock(Session.class);
        when(session.getCustomer()).thenReturn("cus_stripe_456");
        when(session.getSubscription()).thenReturn("sub_stripe_456");
        when(session.getMetadata()).thenReturn(Map.of(
                "company_id", companyId.toString(),
                "user_id", userId.toString(),
                "plan", "pro"
        ));
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) session));

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(subscriptionRepo.findByStripeSubscriptionId("sub_stripe_456")).thenReturn(Optional.empty());
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_stripe_456");
        when(stripeSub.getCustomer()).thenReturn("cus_stripe_456");
        when(stripeSub.getStatus()).thenReturn("active");
        when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
        when(stripeSub.getItems()).thenReturn(mock(com.stripe.model.SubscriptionItemCollection.class));
        when(stripeSub.getItems().getData()).thenReturn(List.of());

        try (MockedStatic<com.stripe.model.Subscription> subMock = mockStatic(com.stripe.model.Subscription.class)) {
            subMock.when(() -> com.stripe.model.Subscription.retrieve("sub_stripe_456")).thenReturn(stripeSub);
            service.handleCheckoutCompleted(event);
        }

        verify(companyRepo, atLeastOnce()).save(any(Company.class));
        verify(subscriptionRepo).save(any(Subscription.class));
    }

    // ── handleCheckoutCompleted → programme de parrainage ──────────────────

    private Session referralSession() {
        Session session = mock(Session.class);
        when(session.getCustomer()).thenReturn("cus_stripe_456");
        when(session.getSubscription()).thenReturn("sub_stripe_456");
        when(session.getMetadata()).thenReturn(Map.of(
                "company_id", companyId.toString(),
                "user_id", userId.toString(),
                "plan", "pro"
        ));
        return session;
    }

    private Event referralEvent(Session session) {
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) session));
        return event;
    }

    private com.stripe.model.Subscription referralStripeSub() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_stripe_456");
        when(stripeSub.getCustomer()).thenReturn("cus_stripe_456");
        when(stripeSub.getStatus()).thenReturn("active");
        when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
        when(stripeSub.getCurrency()).thenReturn("eur");

        var recurring = mock(com.stripe.model.Price.Recurring.class);
        when(recurring.getInterval()).thenReturn("month");
        var price = mock(com.stripe.model.Price.class);
        when(price.getUnitAmount()).thenReturn(14900L);
        when(price.getId()).thenReturn("price_pro_monthly");
        when(price.getRecurring()).thenReturn(recurring);
        var item = mock(com.stripe.model.SubscriptionItem.class);
        when(item.getPrice()).thenReturn(price);
        var itemCollection = mock(com.stripe.model.SubscriptionItemCollection.class);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(stripeSub.getItems()).thenReturn(itemCollection);

        return stripeSub;
    }

    private Customer customerWithBalanceCredit(String stripeCustomerId, String txnId) throws StripeException {
        var txn = mock(com.stripe.model.CustomerBalanceTransaction.class);
        when(txn.getId()).thenReturn(txnId);
        var txnCollection = mock(com.stripe.model.CustomerBalanceTransactionCollection.class);
        when(txnCollection.create(any(com.stripe.param.CustomerBalanceTransactionCollectionCreateParams.class))).thenReturn(txn);
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(stripeCustomerId);
        when(customer.balanceTransactions()).thenReturn(txnCollection);
        return customer;
    }

    @Test
    @DisplayName("handleCheckoutCompleted → société parrainée, 1er paiement → crédite 1 mois au filleul et au parrain")
    void handleCheckoutCompleted_referredCompany_grantsRewardToBothSides() throws StripeException {
        UUID referrerId = UUID.randomUUID();
        Company referrer = Company.builder().id(referrerId).name("Parrain SAS").stripeCustomerId("cus_referrer").build();
        company.setReferredByCompanyId(referrerId);
        company.setReferralRewardGranted(false);

        Session session = referralSession();
        Event event = referralEvent(session);

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(companyRepo.findById(referrerId)).thenReturn(Optional.of(referrer));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(subscriptionRepo.findByStripeSubscriptionId("sub_stripe_456")).thenReturn(Optional.empty());
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        com.stripe.model.Subscription stripeSub = referralStripeSub();
        Customer refereeCustomer = customerWithBalanceCredit("cus_stripe_456", "cbtxn_referee");
        Customer referrerCustomer = customerWithBalanceCredit("cus_referrer", "cbtxn_referrer");

        try (MockedStatic<com.stripe.model.Subscription> subMock = mockStatic(com.stripe.model.Subscription.class);
             MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            subMock.when(() -> com.stripe.model.Subscription.retrieve("sub_stripe_456")).thenReturn(stripeSub);
            customerMock.when(() -> Customer.retrieve("cus_stripe_456")).thenReturn(refereeCustomer);
            customerMock.when(() -> Customer.retrieve("cus_referrer")).thenReturn(referrerCustomer);

            service.handleCheckoutCompleted(event);

            ArgumentCaptor<com.stripe.param.CustomerBalanceTransactionCollectionCreateParams> captor =
                    ArgumentCaptor.forClass(com.stripe.param.CustomerBalanceTransactionCollectionCreateParams.class);
            verify(refereeCustomer.balanceTransactions()).create(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualTo(-14900L);
            verify(referrerCustomer.balanceTransactions()).create(any(com.stripe.param.CustomerBalanceTransactionCollectionCreateParams.class));
        }

        assertThat(company.isReferralRewardGranted()).isTrue();
    }

    @Test
    @DisplayName("handleCheckoutCompleted → récompense déjà accordée → aucun crédit supplémentaire")
    void handleCheckoutCompleted_alreadyRewarded_noDoubleCredit() throws StripeException {
        UUID referrerId = UUID.randomUUID();
        company.setReferredByCompanyId(referrerId);
        company.setReferralRewardGranted(true);

        Session session = referralSession();
        Event event = referralEvent(session);

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(subscriptionRepo.findByStripeSubscriptionId("sub_stripe_456")).thenReturn(Optional.empty());
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        com.stripe.model.Subscription stripeSub = referralStripeSub();

        try (MockedStatic<com.stripe.model.Subscription> subMock = mockStatic(com.stripe.model.Subscription.class)) {
            subMock.when(() -> com.stripe.model.Subscription.retrieve("sub_stripe_456")).thenReturn(stripeSub);
            service.handleCheckoutCompleted(event);
        }

        verify(companyRepo, never()).findById(referrerId);
    }

    @Test
    @DisplayName("handleCheckoutCompleted → parrain sans customer Stripe → en crée un avant de créditer")
    void handleCheckoutCompleted_referrerWithoutStripeCustomer_createsOneLazily() throws StripeException {
        UUID referrerId = UUID.randomUUID();
        Company referrer = Company.builder().id(referrerId).name("Parrain SAS").stripeCustomerId(null).build();
        User referrerOwner = User.builder().id(UUID.randomUUID()).email("owner@parrain.com").fullName("Owner").build();
        company.setReferredByCompanyId(referrerId);
        company.setReferralRewardGranted(false);

        Session session = referralSession();
        Event event = referralEvent(session);

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(companyRepo.findById(referrerId)).thenReturn(Optional.of(referrer));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.findByCompanyIdOrderByCreatedAtAsc(referrerId)).thenReturn(List.of(referrerOwner));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(subscriptionRepo.findByStripeSubscriptionId("sub_stripe_456")).thenReturn(Optional.empty());
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        com.stripe.model.Subscription stripeSub = referralStripeSub();
        Customer refereeCustomer = customerWithBalanceCredit("cus_stripe_456", "cbtxn_referee");
        Customer newReferrerCustomer = customerWithBalanceCredit("cus_new_referrer", "cbtxn_referrer");

        try (MockedStatic<com.stripe.model.Subscription> subMock = mockStatic(com.stripe.model.Subscription.class);
             MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            subMock.when(() -> com.stripe.model.Subscription.retrieve("sub_stripe_456")).thenReturn(stripeSub);
            customerMock.when(() -> Customer.retrieve("cus_stripe_456")).thenReturn(refereeCustomer);
            customerMock.when(() -> Customer.create(any(CustomerCreateParams.class))).thenReturn(newReferrerCustomer);
            customerMock.when(() -> Customer.retrieve("cus_new_referrer")).thenReturn(newReferrerCustomer);

            service.handleCheckoutCompleted(event);

            customerMock.verify(() -> Customer.create(any(CustomerCreateParams.class)));
            verify(newReferrerCustomer.balanceTransactions()).create(any(com.stripe.param.CustomerBalanceTransactionCollectionCreateParams.class));
        }

        assertThat(referrer.getStripeCustomerId()).isEqualTo("cus_new_referrer");
        assertThat(company.isReferralRewardGranted()).isTrue();
    }

    // ── handleSubscriptionDeleted ────────────────────────────────────────

    @Test
    @DisplayName("handleSubscriptionDeleted → sub introuvable → no-op")
    void handleSubscriptionDeleted_notFound_noOp() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_unknown");
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) stripeSub));

        when(subscriptionRepo.findByStripeSubscriptionId("sub_unknown")).thenReturn(Optional.empty());
        service.handleSubscriptionDeleted(event);
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    @DisplayName("handleSubscriptionDeleted → sub trouvée → downgrade FREE")
    void handleSubscriptionDeleted_found_downgradesToFree() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_stripe_123");
        Event event = mock(Event.class);
        var deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) stripeSub));

        when(subscriptionRepo.findByStripeSubscriptionId("sub_stripe_123")).thenReturn(Optional.of(subscription));
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(companyRepo.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));

        service.handleSubscriptionDeleted(event);

        assertThat(subscription.getStatus()).isEqualTo(Subscription.Status.CANCELED);
        assertThat(company.getPlan()).isEqualTo(Company.Plan.FREE);
    }
}
