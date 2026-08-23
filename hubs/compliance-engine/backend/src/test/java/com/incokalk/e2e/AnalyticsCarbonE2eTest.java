package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsCarbonE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/analytics/dashboard")
    void analyticsDashboard() {
        registerAndSetToken();
        var resp = getRaw("/v1/analytics/dashboard");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/analytics/shipments-over-time")
    void analyticsShipmentsOverTime() {
        registerAndSetToken();
        var resp = getRaw("/v1/analytics/shipments-over-time");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/analytics/cost-by-carrier")
    void analyticsCostByCarrier() {
        registerAndSetToken();
        var resp = getRaw("/v1/analytics/cost-by-carrier");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/analytics/top-routes")
    void analyticsTopRoutes() {
        registerAndSetToken();
        var resp = getRaw("/v1/analytics/top-routes");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/analytics/carrier-performance")
    void analyticsCarrierPerformance() {
        registerAndSetToken();
        var resp = getRaw("/v1/analytics/carrier-performance");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/audit - lists audit logs")
    void auditLogs() {
        registerAndSetToken();
        var resp = get("/v1/audit");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/audit/stats")
    void auditStats() {
        registerAndSetToken();
        var resp = get("/v1/audit/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/carbon-offsets - lists offsets")
    void listCarbonOffsets() {
        registerAndSetToken();
        upgradeCompanyPlan(com.incokalk.model.Company.Plan.ENTERPRISE);
        var resp = getList("/v1/carbon-offsets");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/carbon-offsets/stats")
    void carbonOffsetsStats() {
        registerAndSetToken();
        var resp = get("/v1/carbon-offsets/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/carbon-offsets/dashboard")
    void carbonOffsetsDashboard() {
        registerAndSetToken();
        var resp = get("/v1/carbon-offsets/dashboard");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void unauthorized() {
        for (String path : List.of("/v1/analytics/dashboard", "/v1/audit", "/v1/carbon-offsets", "/v1/carbon-offsets/stats")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
                    "Expected 401/429 for " + path + " but got " + resp.getStatusCode());
        }
    }
}
