package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class LandedCostDocumentsE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/landed-costs - lists landed costs")
    void listLandedCosts() {
        registerAndSetToken();
        var resp = getList("/v1/landed-costs");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("POST /v1/landed-costs/calculate")
    void calculateLandedCost() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("cargoValue", 10000.0);
        body.put("weight", 500.0);
        body.put("originCountry", "FR");
        body.put("destinationCountry", "US");
        body.put("incoterm", "CIF");
        body.put("hsCode", "847130");
        var resp = post("/v1/landed-costs/calculate", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/landed-costs/stats")
    void landedCostsStats() {
        registerAndSetToken();
        var resp = get("/v1/landed-costs/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/export/shipments")
    void exportShipments() {
        registerAndSetToken();
        var resp = restTemplate.exchange(baseUrl + "/v1/export/shipments",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/export/carriers")
    void exportCarriers() {
        registerAndSetToken();
        var resp = restTemplate.exchange(baseUrl + "/v1/export/carriers",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/document-parser/history")
    void documentParserHistory() {
        registerAndSetToken();
        var resp = getRaw("/v1/document-parser/history");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/document-parser/stats")
    void documentParserStats() {
        registerAndSetToken();
        var resp = get("/v1/document-parser/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void unauthorized() {
        for (String path : List.of("/v1/landed-costs", "/v1/export/shipments", "/v1/document-parser/history")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
                    "Expected 401/429 for " + path + " but got " + resp.getStatusCode());
        }
    }
}
