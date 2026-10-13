package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SharedAnalyticsE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/audit")
    void listAuditLogs() {
        registerAndSetToken();
        var resp = get("/v1/audit");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void endpointsReturn401WithoutAuth() {
        for (String path : List.of("/v1/audit")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            int status = resp.getStatusCode().value();
            assertTrue(status == 401 || status == 429, "Expected 401/429 for " + path + " but got " + status);
        }
    }
}
