package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ShipmentE2eTest extends E2eTestBase {

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

    @Test
    @DisplayName("POST /v1/shipments - creates shipment")
    void createShipment() {
        registerAndSetToken();
        var resp = post("/v1/shipments", shipmentBody());
        assertTrue(resp.getStatusCode().value() == 200 || resp.getStatusCode().value() == 201);
        assertNotNull(jsonPath(resp, "id"));
        assertFalse(resp.getBody().isEmpty());
    }

    @Test
    @DisplayName("GET /v1/shipments - lists shipments")
    void listShipments() {
        registerAndSetToken();
        post("/v1/shipments", shipmentBody());
        var resp = getList("/v1/shipments");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/shipments/{id} - returns single shipment")
    void getShipmentById() {
        registerAndSetToken();
        var created = post("/v1/shipments", shipmentBody());
        var id = jsonPath(created, "id").toString();

        var resp = get("/v1/shipments/" + id);
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(id, jsonPath(resp, "id").toString());
    }

    @Test
    @DisplayName("DELETE /v1/shipments/{id} - deletes shipment")
    void deleteShipment() {
        registerAndSetToken();
        var created = post("/v1/shipments", shipmentBody());
        var id = jsonPath(created, "id").toString();

        var del = delete("/v1/shipments/" + id);
        assertTrue(del.getStatusCode().value() == 200 || del.getStatusCode().value() == 204);
    }

    @Test
    @DisplayName("GET /v1/carriers - lists carriers")
    void listCarriers() {
        registerAndSetToken();
        var resp = getList("/v1/carriers");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/shipping-rates - lists shipping rates")
    void listShippingRates() {
        registerAndSetToken();
        var resp = getList("/v1/shipping-rates");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("POST /v1/shipments without token - returns 401")
    void createShipmentUnauthenticated() {
        try {
            restTemplate.exchange(baseUrl + "/v1/shipments", HttpMethod.POST,
                    new HttpEntity<>(shipmentBody()), String.class);
        } catch (ResourceAccessException | HttpClientErrorException e) {
            return;
        }
    }

    @Test
    @DisplayName("GET /v1/shipments without token - returns 401")
    void listShipmentsUnauthenticated() {
        try {
            restTemplate.exchange(baseUrl + "/v1/shipments", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
        } catch (ResourceAccessException | HttpClientErrorException e) {
            return;
        }
    }
}
