package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FinancialE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/billing/plans")
    void billingPlans() {
        var resp = restTemplate.getForEntity(baseUrl + "/v1/billing/plans", String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().value() == 401);
    }

    @Test
    @DisplayName("GET /v1/billing/subscription")
    void billingSubscription() {
        registerAndSetToken();
        var resp = get("/v1/billing/subscription");
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError());
    }

}
