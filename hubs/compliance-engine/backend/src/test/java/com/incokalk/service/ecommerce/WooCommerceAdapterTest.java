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
    @DisplayName("mapOrderToShipment → objet shipping complet → requête construite")
    void mapOrderToShipment_fullShipping_buildsRequest() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.getCompany().setName("Boutique SARL");
        Map<String, Object> rawOrder = new LinkedHashMap<>();
        rawOrder.put("id", 42);
        rawOrder.put("currency", "EUR");
        rawOrder.put("total", "29.90");
        rawOrder.put("shipping", Map.of(
                "first_name", "Jean", "last_name", "Client",
                "address_1", "5 avenue de la Gare", "city", "Lyon", "country", "FR", "postcode", "69000"));
        rawOrder.put("line_items", List.of(Map.of("name", "Gadget", "quantity", 3)));

        var result = adapter.mapOrderToShipment(integration, rawOrder);

        assertThat(result).isPresent();
        var request = result.get();
        assertThat(request.getShipperName()).isEqualTo("Boutique SARL");
        assertThat(request.getConsigneeName()).isEqualTo("Jean Client");
        assertThat(request.getConsigneeAddress()).isEqualTo("5 avenue de la Gare");
        assertThat(request.getConsigneeCity()).isEqualTo("Lyon");
        assertThat(request.getConsigneeCountry()).isEqualTo("FR");
        assertThat(request.getConsigneePostalCode()).isEqualTo("69000");
        assertThat(request.getGoodsDescription()).isEqualTo("3x Gadget");
        assertThat(request.getGoodsValue()).isEqualTo(29.90);
        assertThat(request.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("mapOrderToShipment → sans objet shipping → Optional vide")
    void mapOrderToShipment_noShipping_returnsEmpty() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        assertThat(adapter.mapOrderToShipment(integration, Map.of("id", 42))).isEmpty();
    }

    @Test
    @DisplayName("mapOrderToShipment → shipping sans nom ni société → Optional vide")
    void mapOrderToShipment_shippingWithoutNameOrCompany_returnsEmpty() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        Map<String, Object> rawOrder = Map.of("id", 42, "shipping", Map.of("city", "Lyon"));

        assertThat(adapter.mapOrderToShipment(integration, rawOrder)).isEmpty();
    }

    @Test
    @DisplayName("mapOrderToShipment → prénom/nom absents → repli sur le nom de société shipping")
    void mapOrderToShipment_fallsBackToShippingCompanyName() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.getCompany().setName("Boutique SARL");
        Map<String, Object> rawOrder = Map.of("id", 42, "shipping", Map.of("company", "Acme Corp"));

        var result = adapter.mapOrderToShipment(integration, rawOrder);

        assertThat(result).isPresent();
        assertThat(result.get().getConsigneeName()).isEqualTo("Acme Corp");
    }
}
