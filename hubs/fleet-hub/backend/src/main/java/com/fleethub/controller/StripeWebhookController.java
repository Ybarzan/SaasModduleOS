package com.fleethub.controller;

import com.fleethub.billing.StripeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Réception des événements Stripe (payload brut + vérification de signature).
 * Endpoint public : la signature {@code Stripe-Signature} fait foi.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Webhook Stripe", description = "Réception des événements de facturation Stripe")
public class StripeWebhookController {

    private final StripeService stripeService;

    @PostMapping("/api/webhooks/stripe")
    @Operation(summary = "Événement Stripe", description = "Reçoit et traite les événements de facturation Stripe (webhook)")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader("Stripe-Signature") String sigHeader) {
        stripeService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok("ok");
    }
}
