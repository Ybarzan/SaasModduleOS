package com.incokalk.service.ecommerce;

import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WooCommerceAdapterTest {

    private RestTemplate restTemplate;
    private WooCommerceAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        adapter = new WooCommerceAdapter(restTemplate);
    }

    private ECommerceIntegration integration(String storeUrl, String apiKey, String apiSecret) {
        return ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .platform(ECommerceIntegration.Platform.WOOCOMMERCE)
                .storeUrl(storeUrl)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .lastSyncAt(null)
                .build();
    }

    @Test
    @DisplayName("supports → true for WOOCOMMERCE")
    void supports_woocommerce() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.WOOCOMMERCE)).isTrue();
    }

    @Test
    @DisplayName("supports → false for other platforms")
    void supports_otherPlatforms() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).isFalse();
        assertThat(adapter.supports(ECommerceIntegration.Platform.PRESTASHOP)).isFalse();
    }

    @Test
    @DisplayName("syncOrders → returns orders from WooCommerce API")
    void syncOrders_success() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        Map<String, Object> order1 = Map.of("id", 1001, "number", "1001");
        Map<String, Object> order2 = Map.of("id", 1002, "number", "1002");

        ResponseEntity<Map[]> response = new ResponseEntity<>(new Map[]{order1, order2}, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("id")).isEqualTo(1001);
    }

    @Test
    @DisplayName("syncOrders → empty when no orders")
    void syncOrders_emptyResponse() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        ResponseEntity<Map[]> response = new ResponseEntity<>(new Map[]{}, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → empty when response body is null")
    void syncOrders_nullBody() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        ResponseEntity<Map[]> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → handles API exception gracefully")
    void syncOrders_exception() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenThrow(new RuntimeException("API error"));

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → uses lastSyncAt in URL")
    void syncOrders_withLastSync() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setLastSyncAt(LocalDateTime.now().minusHours(1));

        ResponseEntity<Map[]> response = new ResponseEntity<>(new Map[]{}, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(response);

        adapter.syncOrders(integration);
        verify(restTemplate).exchange(contains("after="), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class));
    }

    @Test
    @DisplayName("getOrder → returns order from WooCommerce API")
    void getOrder_success() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        Map<String, Object> orderData = Map.of("id", 1001, "number", "1001", "total", "99.99");
        ResponseEntity<Map> response = new ResponseEntity<>(orderData, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isNotEmpty();
        assertThat(result.get("id")).isEqualTo(1001);
    }

    @Test
    @DisplayName("getOrder → empty map when response body is null")
    void getOrder_nullBody() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrder → empty map on exception")
    void getOrder_exception() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API error"));

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isEmpty();
    }

    // mapOrderToShipment a été retirée avec le domaine Transport
    // (voir ECommerceAdapter / ECommerceSyncService) — ses tests partaient avec elle.

    @Test
    @DisplayName("markOrderShipped/pushEta → échec explicite, non implémenté pour WooCommerce")
    void markOrderShippedAndPushEta_notImplemented() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        assertThat(adapter.markOrderShipped(integration, "1001", "TRACK", "DHL", true).success()).isFalse();
        assertThat(adapter.pushEta(integration, "1001", java.time.LocalDateTime.now()).success()).isFalse();
    }

    @Test
    @DisplayName("extractExternalOrderId → id converti en String")
    void extractExternalOrderId_returnsIdAsString() {
        assertThat(adapter.extractExternalOrderId(Map.of("id", 42))).isEqualTo("42");
    }

    @Test
    @DisplayName("mapOrderToShipment → non implémenté pour cette plateforme → Optional vide")
    void mapOrderToShipment_notImplemented_returnsEmpty() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        assertThat(adapter.mapOrderToShipment(integration, Map.of("id", 42))).isEmpty();
    }
}
