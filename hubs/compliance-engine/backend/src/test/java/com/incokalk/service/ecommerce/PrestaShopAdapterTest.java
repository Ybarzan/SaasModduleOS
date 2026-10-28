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

class PrestaShopAdapterTest {

    private RestTemplate restTemplate;
    private PrestaShopAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        adapter = new PrestaShopAdapter(restTemplate);
    }

    private ECommerceIntegration integration(String storeUrl, String apiKey) {
        return ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(UUID.randomUUID()).build())
                .platform(ECommerceIntegration.Platform.PRESTASHOP)
                .storeUrl(storeUrl)
                .apiKey(apiKey)
                .lastSyncAt(null)
                .build();
    }

    private static final String XML_SINGLE_ORDER = """
            <prestashop>
              <order>
                <id>1</id>
                <reference>REF001</reference>
                <total_paid>99.99</total_paid>
                <total_weight>1.5</total_weight>
                <currency>USD</currency>
                <delivery_address>
                  <firstname>John</firstname>
                  <lastname>Doe</lastname>
                  <address1>123 Main St</address1>
                  <city>New York</city>
                  <country>US</country>
                  <postcode>10001</postcode>
                </delivery_address>
                <order_row>
                  <product_name>Widget A</product_name>
                </order_row>
                <order_row>
                  <product_name>Widget B</product_name>
                </order_row>
              </order>
            </prestashop>""";

    private static final String XML_EMPTY = "<prestashop></prestashop>";

    @Test
    @DisplayName("supports → true for PRESTASHOP")
    void supports_prestashop() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.PRESTASHOP)).isTrue();
    }

    @Test
    @DisplayName("supports → false for other platforms")
    void supports_otherPlatforms() {
        assertThat(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).isFalse();
        assertThat(adapter.supports(ECommerceIntegration.Platform.WOOCOMMERCE)).isFalse();
    }

    @Test
    @DisplayName("syncOrders → returns orders from PrestaShop XML API")
    void syncOrders_success() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(XML_SINGLE_ORDER, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo("1");
        assertThat(result.get(0).get("reference")).isEqualTo("REF001");
        assertThat(result.get(0).get("currency")).isEqualTo("USD");
    }

    @Test
    @DisplayName("syncOrders → empty when no orders in XML")
    void syncOrders_emptyXml() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(XML_EMPTY, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → empty when response body is null")
    void syncOrders_nullBody() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → empty when response body is blank")
    void syncOrders_blankBody() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>("   ", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → handles API exception gracefully")
    void syncOrders_exception() {
        var integration = integration("https://shop.example.com/", "api-key");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → uses lastSyncAt in URL")
    void syncOrders_withLastSync() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setLastSyncAt(LocalDateTime.now().minusHours(1));

        ResponseEntity<String> response = new ResponseEntity<>(XML_EMPTY, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        adapter.syncOrders(integration);
        verify(restTemplate).exchange(contains("filter[date_upd]=>"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("getOrder → returns order from PrestaShop XML API")
    void getOrder_success() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(XML_SINGLE_ORDER, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1");
        assertThat(result).isNotEmpty();
        assertThat(result.get("id")).isEqualTo("1");
        assertThat(result.get("reference")).isEqualTo("REF001");
    }

    @Test
    @DisplayName("getOrder → empty map when response body is null")
    void getOrder_nullBody() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrder → empty map on exception")
    void getOrder_exception() {
        var integration = integration("https://shop.example.com/", "api-key");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        Map<String, Object> result = adapter.getOrder(integration, "1");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrder → empty map when XML has no orders")
    void getOrder_noOrders() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(XML_EMPTY, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrder → empty map on invalid XML")
    void getOrder_invalidXml() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>("not xml at all", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        Map<String, Object> result = adapter.getOrder(integration, "1");
        assertThat(result).isEmpty();
    }

    // mapOrderToShipment a été retirée avec le domaine Transport
    // (voir ECommerceAdapter / ECommerceSyncService) — ses tests partaient avec elle.

    @Test
    @DisplayName("syncOrders → handles invalid XML gracefully")
    void syncOrders_invalidXml() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>("not valid xml <<<", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = adapter.syncOrders(integration);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncOrders → strips trailing slash from store URL")
    void syncOrders_urlNormalization() {
        var integration = integration("https://shop.example.com/", "api-key");
        ResponseEntity<String> response = new ResponseEntity<>(XML_EMPTY, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        adapter.syncOrders(integration);
        verify(restTemplate).exchange(contains("shop.example.com/api/orders"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("markOrderShipped/pushEta → échec explicite, non implémenté pour PrestaShop")
    void markOrderShippedAndPushEta_notImplemented() {
        var integration = integration("https://shop.example.com/", "key");
        assertThat(adapter.markOrderShipped(integration, "1001", "TRACK", "DHL", true).success()).isFalse();
        assertThat(adapter.pushEta(integration, "1001", java.time.LocalDateTime.now()).success()).isFalse();
    }
}
