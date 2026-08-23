package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Régression : le changement de statut d'expédition se fait en PATCH (pas PUT),
 * et une mauvaise méthode HTTP doit renvoyer 405 — pas 500 (le catch-all masquait
 * auparavant la vraie cause en 500).
 */
public class ShipmentStatusReproE2eTest extends E2eTestBase {

    private String createDraftShipment() {
        var carrier = post("/v1/carriers", Map.of(
                "name", "Repro Carrier", "code", "RPRC", "transportModes", "SEA", "isActive", true));
        String carrierId = String.valueOf(carrier.getBody().get("id"));
        var ship = post("/v1/shipments", Map.of(
                "carrierId", carrierId,
                "shipperName", "S", "shipperCountry", "CN",
                "consigneeName", "C", "consigneeCountry", "FR",
                "goodsDescription", "G", "goodsValue", 1000, "currency", "EUR",
                "weightKg", 100, "incotermCode", "FOB"));
        return String.valueOf(ship.getBody().get("id"));
    }

    @Test
    @DisplayName("PATCH /v1/shipments/{id}/status → 200 (flux réel du frontend)")
    void patchStatus_ok() {
        registerAndSetToken();
        String shipId = createDraftShipment();

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("status", "BOOKED"), authHeaders());
        var resp = restTemplate.exchange(baseUrl + "/v1/shipments/" + shipId + "/status",
                HttpMethod.PATCH, req, Map.class);

        assertTrue(resp.getStatusCode().is2xxSuccessful(), "PATCH doit réussir, reçu " + resp.getStatusCode());
        assertEquals("BOOKED", resp.getBody().get("status"));
    }

    @Test
    @DisplayName("PUT /v1/shipments/{id}/status → 405 (mauvaise méthode, plus jamais 500)")
    void putStatus_returns405NotServerError() {
        registerAndSetToken();
        String shipId = createDraftShipment();

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("status", "BOOKED"), authHeaders());
        var resp = restTemplate.exchange(baseUrl + "/v1/shipments/" + shipId + "/status",
                HttpMethod.PUT, req, Map.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode(),
                "Une mauvaise méthode HTTP doit donner 405, pas 500");
    }
}
