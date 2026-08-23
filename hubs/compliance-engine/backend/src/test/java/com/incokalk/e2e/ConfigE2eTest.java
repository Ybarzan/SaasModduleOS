package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/branding")
    void getBranding() {
        registerAndSetToken();
        var resp = get("/v1/branding");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("PUT /v1/branding")
    void updateBranding() {
        registerAndSetToken();
        var update = new LinkedHashMap<String, Object>();
        update.put("primaryColor", "#FF0000");
        update.put("portalTitle", "My Portal");
        var resp = put("/v1/branding", update);
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/currencies")
    void currencies() {
        registerAndSetToken();
        var resp = getList("/v1/currencies");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/currencies without authentication succeeds -- public marketing pages (Pricing) show converted prices for anonymous visitors")
    void currencies_publicAccess_noAuth() {
        var resp = getList("/v1/currencies");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/currencies/rates without authentication succeeds")
    void currenciesRates_publicAccess_noAuth() {
        var resp = get("/v1/currencies/rates?base=EUR");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("Other currency endpoints (convert, exposure-report) stay protected -- only the read-only rate lookups needed for public pricing display were opened up")
    void currenciesConvertAndExposureReport_stayProtected() {
        for (String path : List.of("/v1/currencies/convert?amount=100&from=EUR&to=USD", "/v1/currencies/exposure-report")) {
            var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            int status = resp.getStatusCode().value();
            assertTrue(status == 401 || status == 429, "Expected 401/429 for " + path + " but got " + status);
        }
    }

    @Test
    @DisplayName("GET /v1/branches")
    void branches() {
        registerAndSetToken();
        var resp = getList("/v1/branches");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/providers")
    void providers() {
        registerAndSetToken();
        var resp = getList("/v1/providers");
        assertEquals(200, resp.getStatusCode().value());
    }
}
