package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.Invoice;
import com.incokalk.model.Subscription;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.InvoiceRepository;
import com.incokalk.repository.SubscriptionRepository;
import com.incokalk.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerBalanceTransaction;
import com.stripe.model.Event;
import com.stripe.param.CustomerBalanceTransactionCollectionCreateParams;
import com.stripe.param.CustomerCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    // Annoncé sur la page tarifs ("Essai gratuit 14 jours") mais jamais appliqué côté
    // Stripe jusqu'ici -- le checkout facturait immédiatement. Appliqué à tous les plans
    // payants (Starter/Croissance/Enterprise), pas seulement Pro : le bouton d'appel à
    // l'action est identique pour les trois sur la page tarifs, restreindre l'essai à un
    // seul plan aurait créé un nouvel écart entre l'UI et le comportement réel.
    private static final long TRIAL_PERIOD_DAYS = 14L;

    // Programme de parrainage : 1 mois offert au parrain ET au filleul, appliqué comme
    // un crédit sur le solde client Stripe (négatif = crédit) plutôt qu'une remise sur
    // l'abonnement, pour ne pas interférer avec l'essai gratuit de 14 jours déjà en place.
    private static final String REFERRAL_CREDIT_DESCRIPTION = "Récompense de parrainage IncoKalk — 1 mois offert";

    private final SubscriptionRepository subscriptionRepo;
    private final InvoiceRepository invoiceRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;

    @Value("${incokalk.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${incokalk.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${incokalk.stripe.success-url:}")
    private String successUrl;

    @Value("${incokalk.stripe.cancel-url:}")
    private String cancelUrl;

    @Value("${incokalk.stripe.price-monthly-starter:}")
    private String priceMonthlyStarter;

    @Value("${incokalk.stripe.price-annual-starter:}")
    private String priceAnnualStarter;

    @Value("${incokalk.stripe.price-monthly-pro:}")
    private String priceMonthlyPro;

    @Value("${incokalk.stripe.price-monthly-enterprise:}")
    private String priceMonthlyEnterprise;

    @Value("${incokalk.stripe.price-annual-pro:}")
    private String priceAnnualPro;

    @Value("${incokalk.stripe.price-annual-enterprise:}")
    private String priceAnnualEnterprise;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe initialized");
        } else {
            log.warn("Stripe secret key not configured — billing features disabled");
        }
    }

    public boolean isStripeConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    // ── Plans ────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();

        plans.add(Map.of(
            "id", "free",
            "name", "Découverte",
            "priceMonthly", 0,
            "priceAnnual", 0,
            "currency", "EUR",
            "recommended", false,
            "valueProp", "Testez avant d'engager un centime",
            "hook", "Le calculateur de coûts logistiques le plus complet du marché, gratuit et sans carte bancaire.",
            "features", List.of(
                "5 simulations/mois",
                "Calcul Incoterms 2020",
                "Suivi expédition basique",
                "1 utilisateur"
            ),
            "limits", Map.of(
                "simulationsPerMonth", 5,
                "users", 1,
                "shipments", 10,
                "apiKeyCalls", 10
            )
        ));

        plans.add(Map.of(
            "id", "starter",
            "name", "Starter — Import-Export",
            "priceMonthly", 29,
            "priceAnnual", 290,
            "currency", "EUR",
            "recommended", false,
            "valueProp", "Tout pour importer et exporter, sans embaucher un service dédié",
            "hook", "Simulation, transport multimodal, documents et suivi client réunis dans un seul abonnement — le socle dont une PME a besoin pour démarrer à l'international.",
            "features", List.of(
                "Simulations et devis illimités",
                "Réservation & suivi transport (mer, air, route)",
                "Génération automatique des documents d'expédition",
                "Portail de suivi à partager avec vos clients",
                "3 utilisateurs",
                "Support par email"
            ),
            "limits", Map.of(
                "simulationsPerMonth", -1,
                "users", 3,
                "shipments", 200,
                "apiKeyCalls", 100
            )
        ));

        plans.add(Map.of(
            "id", "pro",
            "name", "Croissance — Douane & Conformité",
            "priceMonthly", 149,
            "priceAnnual", 1519,
            "currency", "EUR",
            "recommended", true,
            "valueProp", "Le moment où vous dédiez quelqu'un à la douane",
            "hook", "Dès qu'un rôle conformité existe chez vous, débloquez le module douane complet : TARIC, DEB, Intrastat, screening DPS, EUR.1, génération CMR/DGD.",
            "features", List.of(
                "Tout Starter inclus",
                "Module douane complet (TARIC, DEB, DPS, EUR.1)",
                "Gestion des rôles par service",
                "10 utilisateurs",
                "API 500 appels/jour",
                "Support prioritaire"
            ),
            "limits", Map.of(
                "simulationsPerMonth", -1,
                "users", 10,
                "shipments", 500,
                "apiKeyCalls", 500
            )
        ));

        plans.add(Map.of(
            "id", "enterprise",
            "name", "Suite — Grand compte",
            "priceMonthly", 499,
            "priceAnnual", 5089,
            "currency", "EUR",
            "recommended", false,
            "valueProp", "Toute l'infrastructure, tous les hubs, un seul contrat",
            "hook", "Entrepôt, finance, multi-filiales, intégrations ERP et marque blanche pour les entreprises qui pilotent leur supply chain à grande échelle.",
            "features", List.of(
                "Tout Croissance inclus",
                "Hub Entrepôt & stock",
                "Hub Finance & trésorerie",
                "Utilisateurs illimités",
                "Intégrations ERP (SAP, Odoo)",
                "Multi-sociétés / multi-filiales",
                "White-label client",
                "Support dédié + SLA 99.9%"
            ),
            "limits", Map.of(
                "simulationsPerMonth", -1,
                "users", -1,
                "shipments", -1,
                "apiKeyCalls", 10000
            )
        ));

        return plans;
    }

    // ── Checkout ──────────────────────────────────────────────────────────

    @Transactional
    public Map<String, String> createCheckoutSession(UUID companyId, UUID userId, String planId, String billingCycle) throws StripeException {
        if (!isStripeConfigured()) {
            throw new IllegalStateException("Stripe n'est pas configuré");
        }

        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Société introuvable"));
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        String priceId = resolvePriceId(planId, billingCycle);

        // Get or create Stripe customer
        String customerId = ensureStripeCustomer(company, user);

        com.stripe.param.checkout.SessionCreateParams.Builder builder = com.stripe.param.checkout.SessionCreateParams.builder()
            .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build())
            .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                .setTrialPeriodDays(TRIAL_PERIOD_DAYS)
                .build())
            .putMetadata("company_id", companyId.toString())
            .putMetadata("user_id", userId.toString())
            .putMetadata("plan", planId)
            .setSuccessUrl(successUrl + "&session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl);

        com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(builder.build());

        log.info("Checkout session created: {} for company {}", session.getId(), companyId);

        return Map.of("sessionId", session.getId(), "url", session.getUrl());
    }

    // ── Customer Portal ───────────────────────────────────────────────────

    @Transactional
    public Map<String, String> createPortalSession(UUID companyId) throws StripeException {
        if (!isStripeConfigured()) {
            throw new IllegalStateException("Stripe n'est pas configuré");
        }

        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Société introuvable"));

        if (company.getStripeCustomerId() == null) {
            throw new IllegalStateException("Aucun abonnement Stripe trouvé");
        }

        com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(company.getStripeCustomerId())
            .setReturnUrl(successUrl)
            .build();

        com.stripe.model.billingportal.Session portalSession = com.stripe.model.billingportal.Session.create(params);

        return Map.of("url", portalSession.getUrl());
    }

    // ── Current subscription ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getSubscription(UUID companyId) {
        Optional<Subscription> opt = subscriptionRepo.findByCompanyId(companyId);
        if (opt.isEmpty()) {
            return Map.of(
                "plan", "FREE",
                "status", "ACTIVE",
                "billingCycle", "MONTHLY",
                "cancelAtPeriodEnd", false
            );
        }

        Subscription sub = opt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sub.getId());
        result.put("plan", sub.getPlan().name());
        result.put("status", sub.getStatus().name());
        result.put("billingCycle", sub.getBillingCycle().name());
        result.put("cancelAtPeriodEnd", sub.isCancelAtPeriodEnd());
        result.put("currentPeriodStart", sub.getCurrentPeriodStart());
        result.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
        result.put("canceledAt", sub.getCanceledAt());
        result.put("createdAt", sub.getCreatedAt());
        return result;
    }

    // ── Invoices ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInvoices(UUID companyId) {
        List<Invoice> invoices = invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        return invoices.stream().map(this::invoiceToMap).toList();
    }

    // ── Webhook handlers ──────────────────────────────────────────────────

    @Transactional
    public void handleCheckoutCompleted(Event event) {
        com.stripe.model.checkout.Session session = (com.stripe.model.checkout.Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) return;

        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();
        String companyIdStr = session.getMetadata() != null ? session.getMetadata().get("company_id") : null;
        String userIdStr = session.getMetadata() != null ? session.getMetadata().get("user_id") : null;
        String planStr = session.getMetadata() != null ? session.getMetadata().get("plan") : null;

        if (companyIdStr == null || userIdStr == null || planStr == null) {
            log.warn("Checkout completed without metadata — skipping");
            return;
        }

        UUID companyId = UUID.fromString(companyIdStr);
        UUID userId = UUID.fromString(userIdStr);

        Company company = companyRepo.findById(companyId).orElse(null);
        User user = userRepo.findById(userId).orElse(null);
        if (company == null || user == null) return;

        // Update company Stripe customer ID
        company.setStripeCustomerId(customerId);
        companyRepo.save(company);

        // Fetch subscription details from Stripe
        try {
            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(subscriptionId);
            createOrUpdateSubscription(company, user, stripeSub, planStr);
            grantReferralRewardIfEligible(company, stripeSub);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe subscription {}: {}", subscriptionId, e.getMessage());
        }

        log.info("Checkout completed for company {} — plan {}", companyId, planStr);
    }

    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        String planStr = stripeSub.getMetadata() != null ? stripeSub.getMetadata().get("plan") : null;

        subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            if (planStr != null) {
                try { sub.setPlan(Subscription.Plan.valueOf(planStr.toUpperCase())); } catch (Exception e) { log.warn("Invalid plan string '{}' in webhook, keeping current plan", planStr); }
            }
            sub.setStatus(mapStripeStatus(stripeSub.getStatus()));
            if (stripeSub.getCurrentPeriodStart() != null) {
                sub.setCurrentPeriodStart(toLocalDateTime(stripeSub.getCurrentPeriodStart()));
            }
            if (stripeSub.getCurrentPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(toLocalDateTime(stripeSub.getCurrentPeriodEnd()));
            }
            sub.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());
            subscriptionRepo.save(sub);

            // Sync company plan
            companyRepo.findById(sub.getCompany().getId()).ifPresent(c -> {
                c.setPlan(Company.Plan.valueOf(sub.getPlan().name()));
                companyRepo.save(c);
            });

            log.info("Subscription updated: {} → status {}", stripeSub.getId(), sub.getStatus());
        });
    }

    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus(Subscription.Status.CANCELED);
            sub.setCanceledAt(LocalDateTime.now());
            subscriptionRepo.save(sub);

            // Downgrade company to FREE
            companyRepo.findById(sub.getCompany().getId()).ifPresent(c -> {
                c.setPlan(Company.Plan.FREE);
                companyRepo.save(c);
                // Downgrade all users of the company to FREE
                userRepo.findByCompanyIdOrderByCreatedAtAsc(c.getId()).forEach(u -> {
                    u.setPlan(User.Plan.FREE);
                    userRepo.save(u);
                });
            });

            log.info("Subscription canceled: {}", stripeSub.getId());
        });
    }

    @Transactional
    public void handleInvoicePaid(Event event) {
        com.stripe.model.Invoice stripeInvoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeInvoice == null) return;

        String companyIdStr = stripeInvoice.getMetadata() != null ? stripeInvoice.getMetadata().get("company_id") : null;
        if (companyIdStr == null) {
            // Try to find by customer
            String customerId = stripeInvoice.getCustomer();
            if (customerId != null) {
                subscriptionRepo.findByStripeCustomerId(customerId).ifPresent(sub -> {
                    persistInvoice(sub.getCompany().getId(), sub.getId(), stripeInvoice);
                });
            }
            return;
        }

        persistInvoice(UUID.fromString(companyIdStr), null, stripeInvoice);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void persistInvoice(UUID companyId, UUID subscriptionId, com.stripe.model.Invoice stripeInvoice) {
        if (invoiceRepo.findByStripeInvoiceId(stripeInvoice.getId()).isPresent()) return;

        Company company = companyRepo.findById(companyId).orElse(null);
        if (company == null) return;

        Subscription sub = subscriptionId != null
            ? subscriptionRepo.findById(subscriptionId).orElse(null)
            : null;

        Invoice invoice = Invoice.builder()
            .company(company)
            .subscription(sub)
            .stripeInvoiceId(stripeInvoice.getId())
            .amountCents(stripeInvoice.getAmountPaid() != null ? stripeInvoice.getAmountPaid() : 0L)
            .currency(stripeInvoice.getCurrency() != null ? stripeInvoice.getCurrency().toUpperCase() : "EUR")
            .status(Invoice.Status.PAID)
            .description(stripeInvoice.getDescription())
            .invoiceUrl(stripeInvoice.getHostedInvoiceUrl())
            .invoicePdfUrl(stripeInvoice.getInvoicePdf())
            .periodStart(stripeInvoice.getPeriodStart() != null ? toLocalDateTime(stripeInvoice.getPeriodStart()) : null)
            .periodEnd(stripeInvoice.getPeriodEnd() != null ? toLocalDateTime(stripeInvoice.getPeriodEnd()) : null)
            .paidAt(LocalDateTime.now())
            .build();

        invoiceRepo.save(invoice);
        log.info("Invoice persisted: {} for company {}", stripeInvoice.getId(), companyId);
    }

    private void createOrUpdateSubscription(Company company, User user, com.stripe.model.Subscription stripeSub, String planStr) {
        Subscription sub = subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId())
            .orElseGet(() -> Subscription.builder()
                .company(company)
                .user(user)
                .stripeSubscriptionId(stripeSub.getId())
                .stripeCustomerId(stripeSub.getCustomer())
                .build());

        try { sub.setPlan(Subscription.Plan.valueOf(planStr.toUpperCase())); } catch (Exception e) { log.warn("Invalid plan '{}' in Stripe metadata, defaulting to PRO", planStr); sub.setPlan(Subscription.Plan.PRO); }
        sub.setStripeCustomerId(stripeSub.getCustomer());
        sub.setStatus(mapStripeStatus(stripeSub.getStatus()));
        sub.setStripePriceId(stripeSub.getItems().getData().isEmpty() ? null : stripeSub.getItems().getData().get(0).getPrice().getId());

        String interval = stripeSub.getItems().getData().isEmpty() ? null
            : stripeSub.getItems().getData().get(0).getPrice().getRecurring().getInterval();
        sub.setBillingCycle("year".equals(interval) ? Subscription.BillingCycle.ANNUAL : Subscription.BillingCycle.MONTHLY);

        if (stripeSub.getCurrentPeriodStart() != null) {
            sub.setCurrentPeriodStart(toLocalDateTime(stripeSub.getCurrentPeriodStart()));
        }
        if (stripeSub.getCurrentPeriodEnd() != null) {
            sub.setCurrentPeriodEnd(toLocalDateTime(stripeSub.getCurrentPeriodEnd()));
        }
        sub.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());

        subscriptionRepo.save(sub);

        // Sync company plan
        company.setPlan(Company.Plan.valueOf(sub.getPlan().name()));
        companyRepo.save(company);

        // Sync user plan (drives JWT plan claim + rate limiting)
        try {
            user.setPlan(User.Plan.valueOf(sub.getPlan().name()));
            userRepo.save(user);
        } catch (Exception e) {
            log.warn("Impossible de synchroniser le plan utilisateur {}: {}", user.getId(), e.getMessage());
        }
    }

    private String resolvePriceId(String planId, String billingCycle) {
        boolean isAnnual = "annual".equalsIgnoreCase(billingCycle);
        return switch (planId.toLowerCase()) {
            case "starter" -> isAnnual ? priceAnnualStarter : priceMonthlyStarter;
            case "pro" -> isAnnual ? priceAnnualPro : priceMonthlyPro;
            case "enterprise" -> isAnnual ? priceAnnualEnterprise : priceMonthlyEnterprise;
            default -> throw new IllegalArgumentException("Plan inconnu: " + planId);
        };
    }

    // Le 1er checkout payant d'une société parrainée déclenche le crédit des deux côtés.
    // Gardé par referralRewardGranted pour ne jamais créditer deux fois (ex: upgrade/downgrade
    // ultérieur qui redéclenche checkout.session.completed).
    private void grantReferralRewardIfEligible(Company company, com.stripe.model.Subscription stripeSub) {
        if (company.getReferredByCompanyId() == null || company.isReferralRewardGranted()) {
            return;
        }

        Company referrer = companyRepo.findById(company.getReferredByCompanyId()).orElse(null);
        if (referrer == null) {
            log.warn("Parrain {} introuvable pour la société {}, récompense de parrainage ignorée", company.getReferredByCompanyId(), company.getId());
            return;
        }

        Long unitAmount = stripeSub.getItems().getData().isEmpty() ? null
            : stripeSub.getItems().getData().get(0).getPrice().getUnitAmount();
        String currency = stripeSub.getCurrency();
        if (unitAmount == null || unitAmount <= 0 || currency == null) {
            log.warn("Montant d'abonnement introuvable pour créditer le parrainage de la société {}, ignoré", company.getId());
            return;
        }

        try {
            creditCustomerBalance(company.getStripeCustomerId(), unitAmount, currency, REFERRAL_CREDIT_DESCRIPTION + " (filleul)");

            String referrerCustomerId = referrer.getStripeCustomerId();
            if (referrerCustomerId == null) {
                User referrerOwner = userRepo.findByCompanyIdOrderByCreatedAtAsc(referrer.getId())
                    .stream().findFirst().orElse(null);
                referrerCustomerId = referrerOwner != null ? ensureStripeCustomer(referrer, referrerOwner) : null;
            }
            if (referrerCustomerId != null) {
                creditCustomerBalance(referrerCustomerId, unitAmount, currency, REFERRAL_CREDIT_DESCRIPTION + " (parrain)");
            } else {
                log.warn("Aucun utilisateur trouvé pour le parrain {}, crédit du parrain ignoré", referrer.getId());
            }

            company.setReferralRewardGranted(true);
            companyRepo.save(company);
            log.info("Récompense de parrainage accordée : société {} (parrain {})", company.getId(), referrer.getId());
        } catch (StripeException e) {
            log.error("Échec de l'attribution de la récompense de parrainage pour {}: {}", company.getId(), e.getMessage());
        }
    }

    private void creditCustomerBalance(String customerId, long amountCents, String currency, String description) throws StripeException {
        CustomerBalanceTransactionCollectionCreateParams params = CustomerBalanceTransactionCollectionCreateParams.builder()
            .setAmount(-amountCents)
            .setCurrency(currency)
            .setDescription(description)
            .build();
        CustomerBalanceTransaction txn = Customer.retrieve(customerId).balanceTransactions().create(params);
        log.info("Crédit de {} {} appliqué au client Stripe {} ({})", amountCents, currency.toUpperCase(), customerId, txn.getId());
    }

    private String ensureStripeCustomer(Company company, User user) throws StripeException {
        if (company.getStripeCustomerId() != null) {
            return company.getStripeCustomerId();
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(user.getEmail())
            .setName(company.getName())
            .putMetadata("company_id", company.getId().toString())
            .putMetadata("user_id", user.getId().toString())
            .build();

        Customer customer = Customer.create(params);
        company.setStripeCustomerId(customer.getId());
        companyRepo.save(company);

        return customer.getId();
    }

    private Subscription.Status mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> Subscription.Status.ACTIVE;
            case "past_due" -> Subscription.Status.PAST_DUE;
            case "canceled" -> Subscription.Status.CANCELED;
            case "unpaid" -> Subscription.Status.UNPAID;
            case "trialing" -> Subscription.Status.TRIALING;
            case "incomplete" -> Subscription.Status.INCOMPLETE;
            default -> Subscription.Status.ACTIVE;
        };
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Map<String, Object> invoiceToMap(Invoice inv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", inv.getId());
        map.put("stripeInvoiceId", inv.getStripeInvoiceId());
        map.put("amountCents", inv.getAmountCents());
        map.put("currency", inv.getCurrency());
        map.put("status", inv.getStatus().name());
        map.put("description", inv.getDescription());
        map.put("invoiceUrl", inv.getInvoiceUrl());
        map.put("invoicePdfUrl", inv.getInvoicePdfUrl());
        map.put("periodStart", inv.getPeriodStart());
        map.put("periodEnd", inv.getPeriodEnd());
        map.put("paidAt", inv.getPaidAt());
        map.put("createdAt", inv.getCreatedAt());
        return map;
    }
}
