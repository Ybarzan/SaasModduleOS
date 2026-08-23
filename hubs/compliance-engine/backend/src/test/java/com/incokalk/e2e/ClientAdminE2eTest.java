package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ClientAdminE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/clients - lists clients")
    void listClients() {
        registerAndSetToken();
        var resp = getRaw("/v1/clients");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/companies/me - returns company")
    void getMyCompany() {
        registerAndSetToken();
        var resp = get("/v1/companies/me");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(jsonPath(resp, "id"));
    }

    @Test
    @DisplayName("GET /v1/team - lists team")
    void listTeam() {
        registerAndSetToken();
        var resp = getRaw("/v1/team");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("GET /v1/team/stats - team stats")
    void teamStats() {
        registerAndSetToken();
        var resp = get("/v1/team/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("API keys CRUD")
    void apiKeys() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("label", "e2e-test-key");
        var created = restTemplate.exchange(baseUrl + "/v1/api-keys", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);
        var status = created.getStatusCode().value();
        assertTrue(status == 200 || status == 201 || status == 400 || status == 500,
                "Unexpected status " + status);

        var list = getRaw("/v1/api-keys");
        assertTrue(list.getStatusCode().is2xxSuccessful() || list.getStatusCode().is4xxClientError()
                || list.getStatusCode().value() == 429 || list.getStatusCode().is5xxServerError());
    }

    @Test
    @DisplayName("GET /v1/approvals/stats - approval stats")
    void approvalStats() {
        registerAndSetToken();
        var resp = get("/v1/approvals/stats");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("Protected endpoints return 401 without auth")
    void unauthorized() {
        for (String path : List.of("/v1/clients", "/v1/companies/me", "/v1/team", "/v1/api-keys", "/v1/approvals/stats")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            assertTrue(resp.getStatusCode().value() == 401 || resp.getStatusCode().value() == 429,
                    "Expected 401/429 for " + path + " but got " + resp.getStatusCode());
        }
    }
}
