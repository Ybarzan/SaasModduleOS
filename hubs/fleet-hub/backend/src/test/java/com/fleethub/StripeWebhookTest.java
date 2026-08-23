package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le traitement des webhooks Stripe (signature validée via HMAC-SHA256),
 * le basculement du statut des sociétés et la sécurité du back-office.
 */
@SpringBootTest(properties = {"stripe.webhook-secret=whsec_test_secret"})
@AutoConfigureMockMvc
class StripeWebhookTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String register(String company, String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("companyId").asText();
    }

    private void postWebhook(String payload) throws Exception {
        String signature = sign(payload, WEBHOOK_SECRET);
        mvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void assertCompanyStatus(String token, String status, String plan) throws Exception {
        mvc.perform(get("/api/billing/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.plan").value(plan));
    }

    @Test
    void checkoutCompleted_activatesCompanyOnPlan() throws Exception {
        String companyId = register("Stripe Co", "stripeco@test.fr");
        String payload = event("checkout.session.completed", """
                {"object":"checkout.session","id":"cs_test_123","client_reference_id":"%s",
                 "customer":"cus_test_123","subscription":"sub_test_123",
                 "metadata":{"companyId":"%s","plan":"PRO"}}""".formatted(companyId, companyId));
        postWebhook(payload);

        // Le plan et le statut de la société ont été mis à jour.
        mvc.perform(get("/api/admin/companies/" + companyId)
                        .header("Authorization", "Bearer " + login("saasadmin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan").value("PRO"));
    }

    @Test
    void subscriptionDeleted_cancelsCompany() throws Exception {
        String companyId = register("Stripe Del", "stripe-del@test.fr");
        postWebhook(event("checkout.session.completed", """
                {"object":"checkout.session","id":"cs_test_2","client_reference_id":"%s",
                 "customer":"cus_test_2","subscription":"sub_test_2",
                 "metadata":{"companyId":"%s","plan":"STARTER"}}""".formatted(companyId, companyId)));
        postWebhook(event("customer.subscription.deleted", """
                {"object":"subscription","id":"sub_test_2","status":"canceled","plan":{"id":"plan_x"}}"""));

        mvc.perform(get("/api/admin/companies/" + companyId)
                        .header("Authorization", "Bearer " + login("saasadmin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void paymentFailed_suspendsCompany() throws Exception {
        String companyId = register("Stripe Pay", "stripe-pay@test.fr");
        postWebhook(event("checkout.session.completed", """
                {"object":"checkout.session","id":"cs_test_3","client_reference_id":"%s",
                 "customer":"cus_test_3","subscription":"sub_test_3",
                 "metadata":{"companyId":"%s","plan":"ENTERPRISE"}}""".formatted(companyId, companyId)));
        postWebhook(event("invoice.payment_failed", """
                {"object":"invoice","id":"in_test_3","subscription":"sub_test_3",
                 "status":"open"}"""));

        mvc.perform(get("/api/admin/companies/" + companyId)
                        .header("Authorization", "Bearer " + login("saasadmin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void invalidSignature_isRejected() throws Exception {
        String companyId = register("Stripe Sig", "stripe-sig@test.fr");
        String payload = event("checkout.session.completed", """
                {"object":"checkout.session","id":"cs_test_4","client_reference_id":"%s",
                 "customer":"cus_test_4","subscription":"sub_test_4",
                 "metadata":{"companyId":"%s","plan":"PRO"}}""".formatted(companyId, companyId));
        mvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=tampered")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billing_checkout_unavailableWhenStripeDisabled() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Stripe Off\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"stripe-off@test.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();

        // Stripe non activé (stripe.enabled=false par défaut) -> 503.
        mvc.perform(post("/api/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(),
                com.fasterxml.jackson.databind.JsonNode.class).get("token").asText();
    }

    private String event(String type, String objectJson) {
        return "{\"id\":\"evt_test\",\"object\":\"event\",\"type\":\"" + type + "\","
                + "\"api_version\":\"2026-07-29\","
                + "\"data\":{\"object\":" + objectJson + "}}";
    }

    private String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
