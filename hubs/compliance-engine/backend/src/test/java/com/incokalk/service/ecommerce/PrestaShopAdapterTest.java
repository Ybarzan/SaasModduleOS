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

class PrestaShopAdapterTest {

    private RestTemplate restTemplate;
    private ShipmentOrderRepository shipmentOrderRepository;
    private PrestaShopAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        shipmentOrderRepository = mock(ShipmentOrderRepository.class);
        adapter = new PrestaShopAdapter(restTemplate, shipmentOrderRepository);
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

    @Test
    @DisplayName("mapOrderToShipment → creates ShipmentOrder from PrestaShop order")
    void mapOrderToShipment_success() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", "1");
        order.put("total_weight", 1.5);
        order.put("total_paid", 99.99);
        order.put("currency", "USD");
        order.put("delivery_firstname", "John");
        order.put("delivery_lastname", "Doe");
        order.put("delivery_address1", "123 Main St");
        order.put("delivery_city", "New York");
        order.put("delivery_country", "US");
        order.put("delivery_postcode", "10001");
        order.put("products", "Widget A, Widget B");

        when(shipmentOrderRepository.findByOrderNumber("PS-1")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNotNull();
        assertThat(result.getOrderNumber()).isEqualTo("PS-1");
        assertThat(result.getGoodsDescription()).isEqualTo("Widget A, Widget B");
        assertThat(result.getWeightKg()).isEqualTo(1.5);
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
        var integration = integration("https://shop.example.com/", "api-key");
        Map<String, Object> order = Map.of("id", "1");

        when(shipmentOrderRepository.findByOrderNumber("PS-1"))
                .thenReturn(Optional.of(ShipmentOrder.builder().build()));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing total_weight")
    void mapOrderToShipment_noWeight() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", "5", "total_paid", 50.0, "currency", "EUR");

        when(shipmentOrderRepository.findByOrderNumber("PS-5")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getWeightKg()).isNull();
        assertThat(result.getGoodsValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing total_paid")
    void mapOrderToShipment_noTotalPaid() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", "10", "total_weight", 2.0, "currency", "USD");

        when(shipmentOrderRepository.findByOrderNumber("PS-10")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getGoodsValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing currency")
    void mapOrderToShipment_noCurrency() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", "15", "total_weight", 1.0, "total_paid", 25.0);

        when(shipmentOrderRepository.findByOrderNumber("PS-15")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("mapOrderToShipment → handles missing delivery name")
    void mapOrderToShipment_noDeliveryName() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = Map.of("id", "20", "total_weight", 1.0, "total_paid", 25.0, "currency", "EUR");

        when(shipmentOrderRepository.findByOrderNumber("PS-20")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getConsigneeName()).isNull();
    }

    @Test
    @DisplayName("mapOrderToShipment → handles only firstname without lastname")
    void mapOrderToShipment_onlyFirstname() {
        var integration = integration("https://shop.example.com/", "api-key");
        integration.setCompany(Company.builder().id(UUID.randomUUID()).build());

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", "30");
        order.put("total_weight", 1.0);
        order.put("total_paid", 25.0);
        order.put("currency", "EUR");
        order.put("delivery_firstname", "John");
        order.put("delivery_lastname", null);

        when(shipmentOrderRepository.findByOrderNumber("PS-30")).thenReturn(Optional.empty());
        when(shipmentOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShipmentOrder result = adapter.mapOrderToShipment(order, integration);
        assertThat(result.getConsigneeName()).isEqualTo("John");
    }

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
}
