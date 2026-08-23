package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

// Non-regression E2E (serveur embarque reel, RANDOM_PORT, TestRestTemplate) pour
// 2 bugs NullPointerException decouverts pendant la session de couverture backend
// (voir PROGRESS.md du 11/08/2026) :
//  - QuoteService.parseMode() -> NPE quand transportMode est absent/invalide
//  - SharedLinkController.createLink() -> NPE (500) quand le lien n'a pas d'expiration
// Le 3e bug (PushNotificationService.emitToUser) n'est reproductible qu'au niveau
// service : aucun endpoint public ne declenche sendNotification avec referenceId=null.
// Voir PushNotificationServiceIntegrationTest pour ce cas.
public class NpeRegressionE2eTest extends E2eTestBase {

    private Map<String, Object> shipmentBody() {
        var b = new LinkedHashMap<String, Object>();
        b.put("shipperName", "E2E Shipper");
        b.put("shipperCity", "Casablanca");
        b.put("shipperCountry", "MA");
        b.put("consigneeName", "E2E Consignee");
        b.put("consigneeCity", "Paris");
        b.put("consigneeCountry", "FR");
        b.put("goodsDescription", "E2E test goods");
        b.put("weightKg", 100.0);
        return b;
    }

    @Test
    @DisplayName("POST /v1/quotes sans transportMode -> 200 avec un tarif de secours (pas de NPE)")
    void quotesWithoutTransportMode() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("originCountry", "MA");
        body.put("destinationCountry", "FR");
        body.put("weightKg", 500.0);
        body.put("volumeM3", 2.0);

        var resp = restTemplate.exchange(baseUrl + "/v1/quotes", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), List.class);

        assertEquals(200, resp.getStatusCode().value());
        var quotes = resp.getBody();
        assertNotNull(quotes);
        assertFalse(quotes.isEmpty());
        @SuppressWarnings("unchecked")
        var first = (Map<String, Object>) quotes.get(0);
        assertNotNull(first.get("transportMode"));
        assertEquals("IncoKalk Standard", first.get("carrierName"));
    }

    @Test
    @DisplayName("POST /v1/quotes avec un transportMode invalide -> 200 avec un tarif de secours (pas de NPE)")
    void quotesWithInvalidTransportMode() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("originCountry", "CN");
        body.put("destinationCountry", "FR");
        body.put("weightKg", 50.0);
        body.put("volumeM3", 0.5);
        body.put("transportMode", "NOT_A_REAL_MODE");

        var resp = restTemplate.exchange(baseUrl + "/v1/quotes", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), List.class);

        assertEquals(200, resp.getStatusCode().value());
        assertFalse(Objects.requireNonNull(resp.getBody()).isEmpty());
    }

    @Test
    @DisplayName("POST /v1/shared sans expiresHours -> 200 (pas de NPE), expiresAt absent")
    void createSharedLinkWithoutExpiry() {
        registerAndSetToken();
        var shipment = post("/v1/shipments", shipmentBody());
        assertEquals(200, shipment.getStatusCode().value());
        var shipmentId = jsonPath(shipment, "id").toString();

        var linkBody = new LinkedHashMap<String, Object>();
        linkBody.put("shipmentId", shipmentId);
        var resp = post("/v1/shared", linkBody);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(jsonPath(resp, "token"));
        assertFalse(Objects.requireNonNull(resp.getBody()).containsKey("expiresAt"));
    }

    @Test
    @DisplayName("GET /v1/shared/access/{token} (public) -> retrouve le lien créé sans expiration")
    void publicAccessToLinkWithoutExpiry() {
        registerAndSetToken();
        var shipment = post("/v1/shipments", shipmentBody());
        var shipmentId = jsonPath(shipment, "id").toString();

        var linkBody = new LinkedHashMap<String, Object>();
        linkBody.put("shipmentId", shipmentId);
        var link = post("/v1/shared", linkBody);
        String token = jsonPath(link, "token");

        // Endpoint public : pas de header Authorization
        var resp = restTemplate.exchange(baseUrl + "/v1/shared/access/" + token,
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

        assertEquals(200, resp.getStatusCode().value());
        @SuppressWarnings("unchecked")
        var shipmentSummary = (Map<String, Object>) resp.getBody().get("shipment");
        assertEquals(shipmentId, shipmentSummary.get("id"));
    }
}
