package com.fleethub.billing;

import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.service.email.EmailNotifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Facturation Stripe : session de checkout (abonnement), portail de facturation,
 * changement de plan et traitement des webhooks. Désactivé si {@code stripe.enabled=false}.
 */
@Service
@RequiredArgsConstructor
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final StripeProperties props;
    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;
    private final EmailNotifier emailNotifier;

    @PostConstruct
    void init() {
        if (props.isEnabled() && props.getSecretKey() != null && !props.getSecretKey().isBlank()) {
            Stripe.apiKey = props.getSecretKey();
        }
    }

    // ---- Checkout ----

    @Transactional(readOnly = true)
    public String createCheckoutSession(Company company, Company.SubscriptionPlan plan) {
        requireEnabled();
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(props.getSuccessUrl())
                .setCancelUrl(props.getCancelUrl())
                .setClientReferenceId(String.valueOf(company.getId()))
                .setCustomerCreation(SessionCreateParams.CustomerCreation.ALWAYS)
                .putMetadata("companyId", String.valueOf(company.getId()))
                .putMetadata("plan", plan.name())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceIdFor(plan))
                        .setQuantity(1L)
                        .build());
        if (company.getStatus() == Company.CompanyStatus.TRIAL
                && company.getTrialEndsAt() != null
                && company.getTrialEndsAt().isAfter(LocalDateTime.now())) {
            builder.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                    .setTrialEnd(company.getTrialEndsAt().atZone(ZoneId.systemDefault()).toEpochSecond())
                    .build());
        }
        try {
            Session session = Session.create(builder.build());
            log.info("Session de checkout créée pour la société {} (plan {})", company.getId(), plan);
            return session.getUrl();
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur Stripe : " + e.getMessage());
        }
    }

    // ---- Portail de facturation ----

    @Transactional(readOnly = true)
    public String createPortalSession(Company company) {
        requireEnabled();
        if (company.getBillingCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun client de facturation n'est encore lié à cette société.");
        }
        try {
            com.stripe.model.billingportal.Session portal =
                    com.stripe.model.billingportal.Session.create(
                            com.stripe.param.billingportal.SessionCreateParams.builder()
                                    .setCustomer(company.getBillingCustomerId())
                                    .setReturnUrl(props.getPortalReturnUrl())
                                    .build());
            return portal.getUrl();
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur Stripe : " + e.getMessage());
        }
    }

    // ---- Changement de plan (prorata) ----

    @Transactional(readOnly = true)
    public void changePlan(Company company, Company.SubscriptionPlan plan) {
        requireEnabled();
        if (company.getSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun abonnement Stripe actif pour cette société.");
        }
        try {
            Subscription subscription = Subscription.retrieve(company.getSubscriptionId());
            String itemId = subscription.getItems().getData().get(0).getId();
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(itemId)
                            .setPrice(priceIdFor(plan))
                            .setQuantity(1L)
                            .build())
                    .build();
            subscription.update(params);
            log.info("Changement de plan demandé : société {} -> {}", company.getId(), plan);
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur Stripe : " + e.getMessage());
        }
    }

    // ---- Résiliation (RGPD : suppression du compte) ----

    /** Résilie immédiatement l'abonnement Stripe, sans frais de clôture. */
    @Transactional(readOnly = true)
    public void cancelSubscription(Company company) {
        requireEnabled();
        if (company.getSubscriptionId() == null) {
            log.info("Aucun abonnement Stripe à résilier pour la société {}", company.getId());
            return;
        }
        try {
            Subscription subscription = Subscription.retrieve(company.getSubscriptionId());
            com.stripe.param.SubscriptionCancelParams params =
                    com.stripe.param.SubscriptionCancelParams.builder().build();
            subscription.cancel(params);
            log.info("Abonnement Stripe résilié lors de la suppression du compte de la société {}",
                    company.getId());
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur Stripe : " + e.getMessage());
        }
    }

    // ---- Webhooks ----    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook Stripe non configuré (STRIPE_WEBHOOK_SECRET).");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, props.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Signature Stripe invalide : {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature Stripe invalide");
        }
        log.info("Webhook Stripe reçu : {} ({})", event.getType(), event.getId());
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.info("Webhook Stripe ignoré (type non géré) : {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        JsonObject session = rawObject(event);
        if (session == null) {
            log.warn("checkout.session.completed sans objet");
            return;
        }
        Long companyId = Long.valueOf(str(session, "client_reference_id"));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Société inconnue pour ce checkout"));
        company.setBillingCustomerId(str(session, "customer"));
        String subscriptionId = str(session, "subscription");
        if (subscriptionId != null) {
            company.setSubscriptionId(subscriptionId);
            company.setSubscriptionProvider("stripe");
        }
        JsonObject metadata = session.getAsJsonObject("metadata");
        if (metadata != null) {
            String plan = str(metadata, "plan");
            if (plan != null) {
                company.setPlan(Company.SubscriptionPlan.valueOf(plan));
            }
        }
        company.setStatus(Company.CompanyStatus.ACTIVE);
        companyRepository.save(company);
        log.info("Checkout terminé pour la société {} -> plan {}", company.getId(), company.getPlan());
    }

    private void handleSubscriptionUpdated(Event event) {
        JsonObject sub = rawObject(event);
        if (sub == null) return;
        companyRepository.findBySubscriptionId(str(sub, "id")).ifPresent(company -> {
            Company.CompanyStatus status = switch (str(sub, "status")) {
                case "trialing" -> Company.CompanyStatus.TRIAL;
                case "active" -> Company.CompanyStatus.ACTIVE;
                case "past_due", "unpaid" -> Company.CompanyStatus.SUSPENDED;
                default -> Company.CompanyStatus.CANCELLED;
            };
            if ("trialing".equals(str(sub, "status"))) {
                Long trialEnd = epoch(sub, "trial_end");
                if (trialEnd != null) {
                    company.setTrialEndsAt(Instant.ofEpochSecond(trialEnd)
                            .atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
            }
            String priceId = priceIdFromSubscription(sub);
            if (priceId != null && planFromPrice(priceId) != null) {
                company.setPlan(planFromPrice(priceId));
            }
            company.setStatus(status);
            companyRepository.save(company);
            log.info("Abonnement Stripe {} -> statut {}", str(sub, "id"), status);
        });
    }

    private void handleSubscriptionDeleted(Event event) {
        JsonObject sub = rawObject(event);
        if (sub == null) return;
        companyRepository.findBySubscriptionId(str(sub, "id")).ifPresent(company -> {
            company.setStatus(Company.CompanyStatus.CANCELLED);
            companyRepository.save(company);
            log.info("Abonnement Stripe résilié pour la société {}", company.getId());
        });
    }

    private void handlePaymentFailed(Event event) {
        JsonObject invoice = rawObject(event);
        if (invoice == null) return;
        String subscriptionId = str(invoice, "subscription");
        if (subscriptionId == null) return;
        companyRepository.findBySubscriptionId(subscriptionId).ifPresent(company -> {
            company.setStatus(Company.CompanyStatus.SUSPENDED);
            companyRepository.save(company);
            notifyAdmins(company, (email, name) -> emailNotifier.paymentFailed(email, name));
            log.warn("Paiement en échec pour la société {} -> suspension", company.getId());
        });
    }

    // ---- Helpers ----

    private void requireEnabled() {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La facturation en ligne n'est pas encore activée sur cette plateforme.");
        }
    }

    private String priceIdFor(Company.SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> props.getPriceStarter();
            case PRO -> props.getPricePro();
            case ENTERPRISE -> props.getPriceEnterprise();
            case TRIAL -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le plan TRIAL ne peut pas être souscrit via Stripe.");
        };
    }

    private void notifyAdmins(Company c, java.util.function.BiConsumer<String, String> send) {
        userRepository.findByCompanyId(c.getId()).stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .filter(AppUser::isEnabled)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .forEach(u -> send.accept(u.getEmail(), c.getName()));
    }

    private static String str(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static JsonObject rawObject(Event event) {
        try {
            return JsonParser.parseString(event.getDataObjectDeserializer().getRawJson()).getAsJsonObject();
        } catch (RuntimeException e) {
            log.warn("Objet d'événement Stripe illisible : {}", e.getMessage());
            return null;
        }
    }

    private static Long epoch(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? null : e.getAsLong();
    }

    private static String priceIdFromSubscription(JsonObject sub) {
        JsonElement items = sub.get("items");
        if (items == null || !items.isJsonObject()) return null;
        JsonObject itemsObj = items.getAsJsonObject();
        JsonElement data = itemsObj.get("data");
        if (data == null || !data.isJsonArray() || data.getAsJsonArray().isEmpty()) return null;
        JsonObject item = data.getAsJsonArray().get(0).getAsJsonObject();
        JsonObject price = item.getAsJsonObject("price");
        return price == null ? null : str(price, "id");
    }

    private Company.SubscriptionPlan planFromPrice(String priceId) {
        if (priceId == null) return null;
        if (priceId.equals(props.getPriceStarter())) return Company.SubscriptionPlan.STARTER;
        if (priceId.equals(props.getPricePro())) return Company.SubscriptionPlan.PRO;
        if (priceId.equals(props.getPriceEnterprise())) return Company.SubscriptionPlan.ENTERPRISE;
        return null;
    }
}
