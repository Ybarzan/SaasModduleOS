package com.incokalk.service.ecommerce;

import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ShipmentOrderRepository;
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
    private ShipmentOrderRepository shipmentOrderRepository;
    private WooCommerceAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        shipmentOrderRepository = mock(ShipmentOrderRepository.class);
        adapter = new WooCommerceAdapter(restTemplate, shipmentOrderRepository);
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
    @DisplayName("mapOrderToShipment → creates ShipmentOrder from WooCommerce order")
    void mapOrderToShipment_success() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> shipping = Map.of(
                "first_name", "John",
                "last_name", "Doe",
                "address_1", "123 Main St",
                "city", "New York",
                "country", "US",
                "postcode", "10001");

        Map<String, Object> lineItem = Map.of(
                "name", "Widget A",
                "weight", 2.5,
                "weight_unit", "kg");

        Map<String, Object> order = Map.of(
                "id", 1001,
                "number", 1001,
                "currency", "USD",
                "total", "99.99",
                "shipping", shipping,
                "line_items", List.of(lineItem));

        when(shipmentOrderRepository.findByOrderNumber("WC-1001")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNotNull();
        assertThat(result.getOrderNumber()).isEqualTo("WC-1001");
        assertThat(result.getGoodsDescription()).isEqualTo("Widget A");
        assertThat(result.getWeightKg()).isEqualTo(2.5);
        assertThat(result.getPackagesCount()).isEqualTo(1);
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getGoodsValue()).isEqualTo(99.99);
        assertThat(result.getConsigneeName()).isEqualTo("John Doe");
        assertThat(result.getConsigneeAddress()).isEqualTo("123 Main St");
        assertThat(result.getConsigneeCity()).isEqualTo("New York");
        assertThat(result.getConsigneeCountry()).isEqualTo("US");
        assertThat(result.getConsigneePostalCode()).isEqualTo("10001");
    }

    @Test
    @DisplayName("mapOrderToShipment → returns null when order already exists")
    void mapOrderToShipment_alreadyExists() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        Map<String, Object> order = Map.of("id", 1001, "number", 1001);

        when(shipmentOrderRepository.findByOrderNumber("WC-1001"))
                .thenReturn(Optional.of(ShipmentOrder.builder().build()));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing shipping address")
    void mapOrderToShipment_noAddress() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> lineItem = Map.of("name", "Widget", "weight", 1.0, "weight_unit", "kg");
        Map<String, Object> order = Map.of(
                "id", 500,
                "number", 500,
                "currency", "EUR",
                "total", "50.00",
                "line_items", List.of(lineItem));

        when(shipmentOrderRepository.findByOrderNumber("WC-500")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNotNull();
        assertThat(result.getConsigneeName()).isNull();
        assertThat(result.getConsigneeAddress()).isNull();
        assertThat(result.getWeightKg()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("mapOrderToShipment → uses order id when number is missing")
    void mapOrderToShipment_noNumber() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", 777, "currency", "USD", "total", "10.00");

        when(shipmentOrderRepository.findByOrderNumber("WC-777")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getOrderNumber()).isEqualTo("WC-777");
    }

    @Test
    @DisplayName("mapOrderToShipment → handles grams weight unit")
    void mapOrderToShipment_gramsWeight() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> lineItem = Map.of("name", "Widget", "weight", 500, "weight_unit", "g");
        Map<String, Object> order = Map.of("id", 100, "number", 100, "currency", "USD", "total", "10.00", "line_items", List.of(lineItem));

        when(shipmentOrderRepository.findByOrderNumber("WC-100")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getWeightKg()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles invalid total gracefully")
    void mapOrderToShipment_invalidTotal() {
        var integration = integration("https://shop.example.com/", "key", "secret");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", 100, "number", 100, "currency", "USD", "total", "not-a-number");

        when(shipmentOrderRepository.findByOrderNumber("WC-100")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getGoodsValue()).isEqualTo(0.0);
    }
}
