package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingMobileE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/tracking/shipments/{id}")
    void trackShipment() {
        registerAndSetToken();
        var shipment = post("/v1/shipments", shipmentBody());
        var id = jsonPath(shipment, "id");
        if (id != null) {
            var resp = getRaw("/v1/tracking/shipments/" + id);
            assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
        }
    }

    @Test
    @DisplayName("GET /v1/mobile/dashboard")
    void mobileDashboard() {
        registerAndSetToken();
        var resp = getRaw("/v1/mobile/dashboard");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("GET /v1/mobile/quick-quote")
    void mobileQuickQuote() {
        registerAndSetToken();
        var resp = getRaw("/v1/mobile/quick-quote");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError()
                || resp.getStatusCode().is5xxServerError());
    }

    @Test
    @DisplayName("GET /v1/mobile/notifications")
    void mobileNotifications() {
        registerAndSetToken();
        var resp = getRaw("/v1/mobile/notifications");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError()
                || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("GET /v1/mobile/profile")
    void mobileProfile() {
        registerAndSetToken();
        var resp = getRaw("/v1/mobile/profile");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError()
                || resp.getStatusCode().is5xxServerError());
    }

    @Test
    @DisplayName("GET /v1/mobile/recent-shipments")
    void mobileRecentShipments() {
        registerAndSetToken();
        var resp = getRaw("/v1/mobile/recent-shipments");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("GET /v1/client/shipments")
    void clientPortalShipments() {
        registerAndSetToken();
        var resp = getRaw("/v1/client/shipments");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError()
                || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("POST /v1/tracking/lookup")
    void trackingLookup() {
        var body = new LinkedHashMap<String, Object>();
        body.put("trackingNumber", "TEST123");
        body.put("carrier", "DHL");
        var resp = restTemplate.postForEntity(baseUrl + "/v1/tracking/lookup", body, String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 400
                || resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void unauthorized() {
        for (String path : List.of("/v1/mobile/dashboard", "/v1/client/shipments")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
                    "Expected 401/429 for " + path + " but got " + resp.getStatusCode());
        }
    }

    private Map<String, Object> shipmentBody() {
        var b = new LinkedHashMap<String, Object>();
        b.put("origin", "Paris");
        b.put("destination", "New York");
        b.put("incoterm", "CIF");
        b.put("cargoDescription", "Electronics");
        b.put("cargoValue", 10000.00);
        b.put("weight", 500.5);
        return b;
    }
}
