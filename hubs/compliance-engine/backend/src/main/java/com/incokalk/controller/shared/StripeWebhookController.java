package com.incokalk.controller.shared;

import com.incokalk.service.BillingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final BillingService billingService;

    @Value("${incokalk.stripe.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(HttpServletRequest request) throws IOException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhook secret not configured — ignoring webhook");
            return ResponseEntity.ok(Map.of("received", "true"));
        }

        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String sigHeader = request.getHeader("Stripe-Signature");

        if (sigHeader == null) {
            log.warn("Missing Stripe-Signature header");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing signature"));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid signature"));
        }

        log.info("Stripe webhook received: type={}, id={}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> billingService.handleCheckoutCompleted(event);
                case "customer.subscription.updated" -> billingService.handleSubscriptionUpdated(event);
                case "customer.subscription.deleted" -> billingService.handleSubscriptionDeleted(event);
                case "invoice.paid" -> billingService.handleInvoicePaid(event);
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing Stripe event {}: {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Processing error"));
        }

        return ResponseEntity.ok(Map.of("received", "true"));
    }
}
