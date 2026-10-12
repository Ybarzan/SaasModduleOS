package com.incokalk.service.ecommerce;

import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopifyAdapterTest {

    private RestTemplate restTemplate;
    private CompanyRepository companyRepository;
    private ShopifyAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        companyRepository = mock(CompanyRepository.class);
        adapter = new ShopifyAdapter(restTemplate, companyRepository);
    }

    private ECommerceIntegration integration(String storeUrl, String apiSecret) {
        return ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .storeUrl(storeUrl)
                .apiSecret(apiSecret)
                .lastSyncAt(null)
                .build();
    }

    @Test
    @DisplayName("supports → true for SHOPIFY")
    void supports_shopify() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).isTrue();
    }

    @Test
    @DisplayName("supports → false for other platforms")
    void supports_otherPlatforms() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.WOOCOMMERCE)).isFalse();
        assertThat(adapter.supports(ECommerceIntegration.Platform.PRESTASHOP)).isFalse();
    }

    @Test
    @DisplayName("syncOrders → returns orders from Shopify API")
    void syncOrders_success() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        Map<String, Object> order1 = Map.of("id", 1001L, "order_number", "1001");
        Map<String, Object> order2 = Map.of("id", 1002L, "order_number", "1002");
        Map<String, Object> responseBody = Map.of("orders", List.of(order1, order2));

        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        ResponseEntity<Map> emptyResponse = new ResponseEntity<>(Map.of("orders", Collections.emptyList()), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response)
                .thenReturn(emptyResponse);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("id")).isEqualTo(1001L);
    }

    @Test
    @DisplayName("syncOrders → empty when no orders")
    void syncOrders_emptyResponse() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        Map<String, Object> responseBody = Map.of("orders", Collections.emptyList());
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → empty when response body is null")
    void syncOrders_nullBody() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → handles API exception gracefully")
    void syncOrders_exception() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API error"));

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → uses lastSyncAt in URL")
    void syncOrders_withLastSync() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setLastSyncAt(LocalDateTime.now().minusHours(1));

        Map<String, Object> responseBody = Map.of("orders", Collections.emptyList());
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        adapter.syncOrders(integration);
        verify(restTemplate).exchange(contains("updated_at_min="), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("getOrder → returns order from Shopify API")
    void getOrder_success() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        Map<String, Object> orderData = Map.of("id", 1001L, "order_number", "1001", "name", "Order #1001");
        Map<String, Object> responseBody = Map.of("order", orderData);
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isNotEmpty();
        assertThat(result.get("id")).isEqualTo(1001L);
    }

    @Test
    @DisplayName("getOrder → empty map when response body is null")
    void getOrder_nullBody() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrder → empty map on exception")
    void getOrder_exception() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API error"));

        Map<String, Object> result = adapter.getOrder(integration, "1001");
        assertThat(result).isEmpty();
    }

    // mapOrderToShipment a été retirée avec le domaine Transport
    // (voir ECommerceAdapter / ECommerceSyncService) — ses tests partaient avec elle.

    @Test
    @DisplayName("syncOrders → strips trailing slash from store URL")
    void syncOrders_urlNormalization() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        Map<String, Object> responseBody = Map.of("orders", Collections.emptyList());
        ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        adapter.syncOrders(integration);
        verify(restTemplate).exchange(contains("my-store.myshopify.com/admin/api/2024-01"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }
}
