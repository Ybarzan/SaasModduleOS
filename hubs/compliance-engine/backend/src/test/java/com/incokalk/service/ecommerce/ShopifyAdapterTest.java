package com.incokalk.service.ecommerce;

import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
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
import static org.mockito.Mockito.*;

class ShopifyAdapterTest {

    private RestTemplate restTemplate;
    private ShipmentOrderRepository shipmentOrderRepository;
    private CompanyRepository companyRepository;
    private ShopifyAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        shipmentOrderRepository = mock(ShipmentOrderRepository.class);
        companyRepository = mock(CompanyRepository.class);
        adapter = new ShopifyAdapter(restTemplate, shipmentOrderRepository, companyRepository);
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

    @Test
    @DisplayName("mapOrderToShipment → creates ShipmentOrder from Shopify order")
    void mapOrderToShipment_success() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> address = Map.of(
                "name", "John Doe",
                "address1", "123 Main St",
                "city", "New York",
                "country_code", "US",
                "zip", "10001");

        Map<String, Object> lineItem = Map.of(
                "title", "Widget A",
                "grams", 500);

        Map<String, Object> order = Map.of(
                "order_number", "1001",
                "currency", "USD",
                "total_price", 99.99,
                "shipping_address", address,
                "line_items", List.of(lineItem));

        when(shipmentOrderRepository.findByOrderNumber("SHOP-1001")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNotNull();
        assertThat(result.getOrderNumber()).isEqualTo("SHOP-1001");
        assertThat(result.getGoodsDescription()).isEqualTo("Widget A");
        assertThat(result.getWeightKg()).isEqualTo(0.5);
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
        var integration = integration("https://my-store.myshopify.com/", "token123");
        Map<String, Object> order = Map.of("order_number", "1001");

        when(shipmentOrderRepository.findByOrderNumber("SHOP-1001"))
                .thenReturn(Optional.of(ShipmentOrder.builder().build()));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing shipping address")
    void mapOrderToShipment_noAddress() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> lineItem = Map.of("title", "Widget", "grams", 200);
        Map<String, Object> order = Map.of(
                "order_number", "500",
                "currency", "EUR",
                "total_price", 50.00,
                "line_items", List.of(lineItem));

        when(shipmentOrderRepository.findByOrderNumber("SHOP-500")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNotNull();
        assertThat(result.getConsigneeName()).isNull();
        assertThat(result.getConsigneeAddress()).isNull();
        assertThat(result.getWeightKg()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles multiple line items with weights")
    void mapOrderToShipment_multipleLineItems() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> item1 = Map.of("title", "Widget A", "grams", 500);
        Map<String, Object> item2 = Map.of("title", "Widget B", "grams", 300);
        Map<String, Object> item3 = new HashMap<>();
        item3.put("title", "Widget C");
        item3.put("grams", null);

        Map<String, Object> order = Map.of(
                "order_number", "200",
                "currency", "USD",
                "total_price", 200.00,
                "line_items", List.of(item1, item2, item3));

        when(shipmentOrderRepository.findByOrderNumber("SHOP-200")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getGoodsDescription()).isEqualTo("Widget A, Widget B, Widget C");
        assertThat(result.getWeightKg()).isEqualTo(0.8);
        assertThat(result.getPackagesCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing total_price")
    void mapOrderToShipment_noTotalPrice() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("order_number", "300", "currency", "USD");

        when(shipmentOrderRepository.findByOrderNumber("SHOP-300")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getGoodsValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing currency")
    void mapOrderToShipment_noCurrency() {
        var integration = integration("https://my-store.myshopify.com/", "token123");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("order_number", "400");

        when(shipmentOrderRepository.findByOrderNumber("SHOP-400")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

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
