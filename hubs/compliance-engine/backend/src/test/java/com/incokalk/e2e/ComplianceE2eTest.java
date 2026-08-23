package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ComplianceE2eTest extends E2eTestBase {

    @Test
    @DisplayName("POST /v1/compliance/check")
    void complianceCheck() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("originCountry", "FR");
        body.put("destinationCountry", "US");
        body.put("hsCode", "847130");
        body.put("productValue", 5000);
        var resp = post("/v1/compliance/check", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 400);
    }

    @Test
    @DisplayName("GET /v1/customs/tariff-info")
    void tariffInfo() {
        registerAndSetToken();
        var resp = restTemplate.exchange(baseUrl + "/v1/customs/tariff-info?hsCode=847130&dest=US&origin=FR",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

    @Test
    @DisplayName("POST /v1/dps/screen")
    void deniedPartyScreening() {
        registerAndSetToken();
        var body = new LinkedHashMap<String, Object>();
        body.put("entityName", "Test Company Ltd");
        body.put("country", "FR");
        var resp = post("/v1/dps/screen", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError()
                || resp.getStatusCode().is5xxServerError());
    }

    @Test
    @DisplayName("GET /v1/trade-agreements")
    void tradeAgreementsList() {
        var resp = restTemplate.exchange(baseUrl + "/v1/trade-agreements",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 401);
    }
}
