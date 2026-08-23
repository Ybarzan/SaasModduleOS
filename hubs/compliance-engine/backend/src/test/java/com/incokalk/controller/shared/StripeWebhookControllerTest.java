package com.incokalk.controller.shared;

import com.incokalk.service.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Plain unit tests for {@link StripeWebhookController}.
 *
 * <p>No Spring context is used — the controller is instantiated directly and the
 * {@code webhookSecret} field (normally populated via {@code @Value}) is injected with
 * {@link ReflectionTestUtils}. Requests are built with {@link MockHttpServletRequest} so the
 * controller can read the raw body via {@code getInputStream()} exactly as it would in
 * production, and Stripe signatures are computed manually following Stripe's documented
 * webhook signing scheme: {@code v1 = HMAC-SHA256(secret, "{timestamp}.{payload}")}, header
 * format {@code t=<timestamp>,v1=<hexSignature>}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookController — Tests unitaires")
class StripeWebhookControllerTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    @Mock
    private BillingService billingService;

    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(billingService);
        ReflectionTestUtils.setField(controller, "webhookSecret", WEBHOOK_SECRET);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String signedHeader(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        String signature = hmacSha256Hex(secret, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String eventPayload(String type) {
        return """
            {
              "id": "evt_test_%s",
              "object": "event",
              "api_version": "2023-10-16",
              "created": 1700000000,
              "type": "%s",
              "data": {
                "object": {
                  "id": "obj_test",
                  "object": "checkout.session"
                }
              }
            }
            """.formatted(type.replace('.', '_'), type);
    }

    private static MockHttpServletRequest requestWithBody(String payload, String sigHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        if (sigHeader != null) {
            request.addHeader("Stripe-Signature", sigHeader);
        }
        return request;
    }

    // ------------------------------------------------------------------
    // Secret not configured
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Secret webhook non configuré -> 200 no-op sans vérification")
    void handle_webhookSecretNotConfigured_returnsOkNoop() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", null);
        MockHttpServletRequest request = requestWithBody("{}", null);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("received", "true");
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Secret webhook vide/blank -> 200 no-op sans vérification")
    void handle_webhookSecretBlank_returnsOkNoop() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "   ");
        MockHttpServletRequest request = requestWithBody("{}", null);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(billingService);
    }

    // ------------------------------------------------------------------
    // Missing signature header
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Header Stripe-Signature absent -> 400")
    void handle_missingSignatureHeader_returnsBadRequest() throws Exception {
        MockHttpServletRequest request = requestWithBody(eventPayload("checkout.session.completed"), null);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Missing signature");
        verifyNoInteractions(billingService);
    }

    // ------------------------------------------------------------------
    // Invalid signature
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Signature invalide -> 400")
    void handle_invalidSignature_returnsBadRequest() throws Exception {
        String payload = eventPayload("checkout.session.completed");
        // Signed with the wrong secret so verification fails.
        String badHeader = signedHeader(payload, "whsec_wrong_secret");
        MockHttpServletRequest request = requestWithBody(payload, badHeader);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid signature");
        verifyNoInteractions(billingService);
    }

    @Test
    @DisplayName("Signature malformée (pas de t=/v1=) -> 400")
    void handle_malformedSignatureHeader_returnsBadRequest() throws Exception {
        String payload = eventPayload("checkout.session.completed");
        MockHttpServletRequest request = requestWithBody(payload, "not-a-valid-header");

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid signature");
        verifyNoInteractions(billingService);
    }

    // ------------------------------------------------------------------
    // Valid signature + recognized event types dispatch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("checkout.session.completed -> handleCheckoutCompleted")
    void handle_checkoutSessionCompleted_dispatchesToBillingService() throws Exception {
        String payload = eventPayload("checkout.session.completed");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("received", "true");
        verify(billingService, times(1)).handleCheckoutCompleted(org.mockito.ArgumentMatchers.any());
        verify(billingService, never()).handleSubscriptionUpdated(org.mockito.ArgumentMatchers.any());
        verify(billingService, never()).handleSubscriptionDeleted(org.mockito.ArgumentMatchers.any());
        verify(billingService, never()).handleInvoicePaid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("customer.subscription.updated -> handleSubscriptionUpdated")
    void handle_subscriptionUpdated_dispatchesToBillingService() throws Exception {
        String payload = eventPayload("customer.subscription.updated");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(billingService, times(1)).handleSubscriptionUpdated(org.mockito.ArgumentMatchers.any());
        verify(billingService, never()).handleCheckoutCompleted(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("customer.subscription.deleted -> handleSubscriptionDeleted")
    void handle_subscriptionDeleted_dispatchesToBillingService() throws Exception {
        String payload = eventPayload("customer.subscription.deleted");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(billingService, times(1)).handleSubscriptionDeleted(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("invoice.paid -> handleInvoicePaid")
    void handle_invoicePaid_dispatchesToBillingService() throws Exception {
        String payload = eventPayload("invoice.paid");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(billingService, times(1)).handleInvoicePaid(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------
    // Unrecognized event type -> no-op
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Type d'evenement non reconnu -> 200 sans dispatch")
    void handle_unrecognizedEventType_isNoopButStillOk() throws Exception {
        String payload = eventPayload("payment_intent.created");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("received", "true");
        verifyNoInteractions(billingService);
    }

    // ------------------------------------------------------------------
    // Billing service throws -> 500
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Exception dans BillingService -> 500")
    void handle_billingServiceThrows_returnsInternalServerError() throws Exception {
        String payload = eventPayload("checkout.session.completed");
        String header = signedHeader(payload, WEBHOOK_SECRET);
        MockHttpServletRequest request = requestWithBody(payload, header);

        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
            .when(billingService).handleCheckoutCompleted(org.mockito.ArgumentMatchers.any());

        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Processing error");
    }
}
