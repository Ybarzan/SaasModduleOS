package com.incokalk.e2e;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PortalNotificationE2eTest extends E2eTestBase {

    @Test
    @DisplayName("GET /v1/mobile/dashboard")
    void mobileDashboard() {
        registerAndSetToken();
        var resp = get("/v1/mobile/dashboard");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/mobile/recent-shipments")
    void recentShipments() {
        registerAndSetToken();
        var resp = getList("/v1/mobile/recent-shipments");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/client/shipments")
    void clientShipments() {
        registerAndSetToken();
        var resp = getList("/v1/client/shipments");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/notifications")
    void notifications() {
        registerAndSetToken();
        var resp = getList("/v1/notifications");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/notification-rules")
    void notificationRules() {
        registerAndSetToken();
        var resp = getList("/v1/notification-rules");
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/mobile/dashboard without auth - returns 401")
    void mobileDashboardUnauthenticated() {
        var resp = restTemplate.exchange(baseUrl + "/v1/mobile/dashboard",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /v1/client/shipments without auth - returns 401")
    void clientShipmentsUnauthenticated() {
        var resp = restTemplate.exchange(baseUrl + "/v1/client/shipments",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertEquals(401, resp.getStatusCode().value());
    }
}
