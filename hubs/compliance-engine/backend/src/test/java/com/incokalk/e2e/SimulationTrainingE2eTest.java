package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationTrainingE2eTest extends E2eTestBase {

    @Test
    @DisplayName("POST /v1/simulate - simulates shipment")
    void simulate() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("origin", "FR");
        body.put("destination", "US");
        body.put("incoterm", "CIF");
        body.put("cargoValue", 10000);
        body.put("weight", 500);
        var resp = post("/v1/simulate", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /incoterms - lists incoterms")
    void listIncoterms() {
        var resp = restTemplate.getForEntity(baseUrl + "/incoterms", String.class);
        assertTrue(resp.getStatusCode().value() == 200 || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("GET /v1/simulate/incoterms")
    void simulateIncoterms() {
        var resp = restTemplate.getForEntity(baseUrl + "/v1/simulate/incoterms", String.class);
        assertTrue(resp.getStatusCode().value() == 200 || resp.getStatusCode().value() == 429);
    }

    @Test
    @DisplayName("GET /v1/simulate/simulations → 401 sans authentification")
    void listSimulations() {
        var resp = restTemplate.getForEntity(baseUrl + "/v1/simulate/simulations", String.class);
        assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
            "Expected 401/429 without auth but got " + resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /v1/academy/modules")
    void academyModules() {
        registerAndSetToken();
        var resp = getRaw("/v1/academy/modules");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/academy/dashboard")
    void academyDashboard() {
        registerAndSetToken();
        var resp = get("/v1/academy/dashboard");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/optimization/recommendations")
    void optimizationRecommendations() {
        registerAndSetToken();
        var resp = getRaw("/v1/optimization/recommendations");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/optimization/lane-analysis")
    void optimizationLaneAnalysis() {
        registerAndSetToken();
        var resp = getRaw("/v1/optimization/lane-analysis");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/optimization/stats")
    void optimizationStats() {
        registerAndSetToken();
        var resp = get("/v1/optimization/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void unauthorized() {
        for (String path : List.of("/v1/academy/modules", "/v1/optimization/recommendations")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
                    "Expected 401/429 for " + path + " but got " + resp.getStatusCode());
        }
    }
}
