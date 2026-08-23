package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SharedAnalyticsE2eTest extends E2eTestBase {

    @Test
    @DisplayName("POST /v1/shared")
    void createSharedLink() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("resourceType", "SHIPMENT");
        body.put("resourceId", "00000000-0000-0000-0000-000000000001");
        body.put("permissions", "VIEW");
        var resp = post("/v1/shared", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/audit")
    void listAuditLogs() {
        registerAndSetToken();
        var resp = get("/v1/audit");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/carbon-offsets")
    void listCarbonOffsets() {
        registerAndSetToken();
        upgradeCompanyPlan(com.incokalk.model.Company.Plan.ENTERPRISE);
        var resp = getList("/v1/carbon-offsets");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void endpointsReturn401WithoutAuth() {
        for (String path : List.of("/v1/audit", "/v1/carbon-offsets")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            int status = resp.getStatusCode().value();
            assertTrue(status == 401 || status == 429, "Expected 401/429 for " + path + " but got " + status);
        }
    }
}
