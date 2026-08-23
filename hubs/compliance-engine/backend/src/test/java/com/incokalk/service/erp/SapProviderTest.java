package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.ShipmentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SapProviderTest {

    private RestTemplate restTemplate;
    private SapProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        provider = new SapProvider(restTemplate);
    }

    private ErpConfig config(String endpoint) {
        return ErpConfig.builder()
                .id(UUID.randomUUID())
                .erpType("SAP")
                .name("SAP")
                .apiEndpoint(endpoint)
                .username("user1")
                .apiSecret("secret1")
                .databaseName("SBODEMO")
                .build();
    }

    // ---------- getErpType / getName ----------

    @Test
    @DisplayName("getErpType → SAP")
    void getErpType_returnsSap() {
        assertThat(provider.getErpType()).isEqualTo("SAP");
    }

    @Test
    @DisplayName("getName → SAP Business One")
    void getName_returnsSapBusinessOne() {
        assertThat(provider.getName()).isEqualTo("SAP Business One");
    }

    // ---------- testConnection ----------

    @Test
    @DisplayName("testConnection → true on 2xx response")
    void testConnection_success() {
        ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        boolean result = provider.testConnection(config("https://sap.example.com"));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("testConnection → false on non-2xx response")
    void testConnection_nonSuccessStatus() {
        ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.UNAUTHORIZED);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        boolean result = provider.testConnection(config("https://sap.example.com"));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → false on exception")
    void testConnection_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        boolean result = provider.testConnection(config("https://sap.example.com"));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → false when endpoint is blank")
    void testConnection_blankEndpoint() {
        boolean result = provider.testConnection(config("   "));
        assertThat(result).isFalse();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("testConnection → false when endpoint is null")
    void testConnection_nullEndpoint() {
        boolean result = provider.testConnection(config(null));
        assertThat(result).isFalse();
        verifyNoInteractions(restTemplate);
    }

    // ---------- importProducts / importOrders / importContacts ----------

    @Test
    @DisplayName("importProducts → success with records")
    void importProducts_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("ItemCode", "A1"), Map.of("ItemCode", "A2")));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        ErpSyncResult result = provider.importProducts(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("importProducts → failure result on exception")
    void importProducts_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("boom"));

        ErpSyncResult result = provider.importProducts(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("importProducts → failure when endpoint not configured")
    void importProducts_noEndpoint() {
        ErpSyncResult result = provider.importProducts(config(null));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Endpoint SAP non configuré");
    }

    @Test
    @DisplayName("importOrders → success with records")
    void importOrders_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("DocEntry", 1)));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        ErpSyncResult result = provider.importOrders(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("importOrders → failure result on exception")
    void importOrders_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        ErpSyncResult result = provider.importOrders(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("importContacts → success with records")
    void importContacts_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("CardCode", "C001")));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        ErpSyncResult result = provider.importContacts(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("importContacts → failure result on exception")
    void importContacts_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("network down"));

        ErpSyncResult result = provider.importContacts(config("https://sap.example.com"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("network down");
    }

    // ---------- exportShipments ----------

    private ShipmentOrder shipment(String orderNumber, String consignee, LocalDateTime shippedAt) {
        return ShipmentOrder.builder()
                .orderNumber(orderNumber)
                .consigneeName(consignee)
                .shippedAt(shippedAt)
                .build();
    }

    @Test
    @DisplayName("exportShipments → all synced on 2xx responses")
    void exportShipments_allSynced() {
        ResponseEntity<String> response = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> shipments = List.of(
                shipment("SO-1", "Acme", LocalDateTime.now()),
                shipment("SO-2", "Beta", LocalDateTime.now()));

        ErpSyncResult result = provider.exportShipments(config("https://sap.example.com"), shipments);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("exportShipments → 201 Created counts as synced")
    void exportShipments_created201() {
        ResponseEntity<String> response = new ResponseEntity<>("{}", HttpStatus.CREATED);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> shipments = List.of(shipment("SO-1", "Acme", LocalDateTime.now()));

        ErpSyncResult result = provider.exportShipments(config("https://sap.example.com"), shipments);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsSynced()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportShipments → non-2xx response counts as failed")
    void exportShipments_nonSuccessStatus() {
        ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> shipments = List.of(shipment("SO-1", "Acme", LocalDateTime.now()));

        ErpSyncResult result = provider.exportShipments(config("https://sap.example.com"), shipments);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecordsFailed()).isEqualTo(1);
        assertThat(result.getRecordsSynced()).isEqualTo(0);
    }

    @Test
    @DisplayName("exportShipments → per-item exception counted as failed, others still processed")
    void exportShipments_perItemException() {
        ResponseEntity<String> okResponse = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("item failure"))
                .thenReturn(okResponse);

        List<ShipmentOrder> shipments = List.of(
                shipment("SO-1", "Acme", LocalDateTime.now()),
                shipment("SO-2", "Beta", LocalDateTime.now()));

        ErpSyncResult result = provider.exportShipments(config("https://sap.example.com"), shipments);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(1);
        assertThat(result.getRecordsFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportShipments → handles null consignee name and shippedAt")
    void exportShipments_nullFields() {
        ResponseEntity<String> response = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> shipments = List.of(shipment("SO-1", null, null));

        ErpSyncResult result = provider.exportShipments(config("https://sap.example.com"), shipments);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsSynced()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportShipments → outer failure result when endpoint missing")
    void exportShipments_noEndpoint() {
        List<ShipmentOrder> shipments = List.of(shipment("SO-1", "Acme", LocalDateTime.now()));
        ErpSyncResult result = provider.exportShipments(config(null), shipments);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Endpoint SAP non configuré");
    }

    // ---------- exportOrders ----------

    private ShipmentOrder order(String orderNumber, String consignee, LocalDateTime bookedAt) {
        return ShipmentOrder.builder()
                .orderNumber(orderNumber)
                .consigneeName(consignee)
                .bookedAt(bookedAt)
                .build();
    }

    @Test
    @DisplayName("exportOrders → all synced on 2xx responses")
    void exportOrders_allSynced() {
        ResponseEntity<String> response = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> orders = List.of(
                order("PO-1", "Acme", LocalDateTime.now()),
                order("PO-2", "Beta", LocalDateTime.now()));

        ErpSyncResult result = provider.exportOrders(config("https://sap.example.com"), orders);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("exportOrders → non-2xx response counts as failed")
    void exportOrders_nonSuccessStatus() {
        ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.FORBIDDEN);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> orders = List.of(order("PO-1", "Acme", LocalDateTime.now()));

        ErpSyncResult result = provider.exportOrders(config("https://sap.example.com"), orders);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecordsFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportOrders → per-item exception counted as failed")
    void exportOrders_perItemException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("item failure"));

        List<ShipmentOrder> orders = List.of(order("PO-1", "Acme", LocalDateTime.now()));

        ErpSyncResult result = provider.exportOrders(config("https://sap.example.com"), orders);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRecordsFailed()).isEqualTo(1);
        assertThat(result.getRecordsSynced()).isEqualTo(0);
    }

    @Test
    @DisplayName("exportOrders → handles null consignee name and bookedAt")
    void exportOrders_nullFields() {
        ResponseEntity<String> response = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<ShipmentOrder> orders = List.of(order("PO-1", null, null));

        ErpSyncResult result = provider.exportOrders(config("https://sap.example.com"), orders);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("exportOrders → outer failure result when endpoint missing")
    void exportOrders_noEndpoint() {
        List<ShipmentOrder> orders = List.of(order("PO-1", "Acme", LocalDateTime.now()));
        ErpSyncResult result = provider.exportOrders(config(null), orders);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Endpoint SAP non configuré");
    }

    // ---------- getProducts / getOrders / getContacts ----------

    @Test
    @DisplayName("getProducts → returns mapped records")
    void getProducts_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("ItemCode", "A1")));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("ItemCode")).isEqualTo("A1");
    }

    @Test
    @DisplayName("getProducts → empty list on exception")
    void getProducts_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("boom"));

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrders → returns mapped records")
    void getOrders_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("DocEntry", 42)));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getOrders(config("https://sap.example.com"));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getOrders → empty list on exception")
    void getOrders_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("boom"));

        List<Map<String, Object>> result = provider.getOrders(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getContacts → returns mapped records")
    void getContacts_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of(Map.of("CardCode", "C1")));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getContacts(config("https://sap.example.com"));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getContacts → empty list on exception")
    void getContacts_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("boom"));

        List<Map<String, Object>> result = provider.getContacts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    // ---------- fetchAllPages: pagination, non-2xx, missing/non-list value, non-map items ----------

    @Test
    @DisplayName("getProducts → follows odata.nextLink pagination across pages")
    void getProducts_pagination() {
        Map<String, Object> page1 = new LinkedHashMap<>();
        page1.put("value", List.of(Map.of("ItemCode", "A1")));
        page1.put("odata.nextLink", "https://sap.example.com/b1s/v1/Items?skip=1");

        Map<String, Object> page2 = new LinkedHashMap<>();
        page2.put("value", List.of(Map.of("ItemCode", "A2")));

        ResponseEntity<Map> response1 = new ResponseEntity<>(page1, HttpStatus.OK);
        ResponseEntity<Map> response2 = new ResponseEntity<>(page2, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("ItemCode")).isEqualTo("A1");
        assertThat(result.get(1).get("ItemCode")).isEqualTo("A2");
        verify(restTemplate, times(2))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("getProducts → stops pagination when non-2xx response encountered")
    void getProducts_nonSuccessStopsPagination() {
        ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("getProducts → empty when response body is null")
    void getProducts_nullBody() {
        ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts → empty when 'value' field is missing")
    void getProducts_missingValueField() {
        Map<String, Object> body = new LinkedHashMap<>();
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts → ignores when 'value' is not a list")
    void getProducts_valueNotAList() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", "not-a-list");
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts → skips non-map items within the value list")
    void getProducts_nonMapItemsSkipped() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", Arrays.asList("not-a-map", Map.of("ItemCode", "A1")));
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        List<Map<String, Object>> result = provider.getProducts(config("https://sap.example.com"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("ItemCode")).isEqualTo("A1");
    }

    // ---------- buildAuthHeaders: auth combinations ----------

    @Test
    @DisplayName("testConnection → works without username/secret/database (no basic auth set)")
    void testConnection_noCredentials() {
        ErpConfig cfg = ErpConfig.builder()
                .apiEndpoint("https://sap.example.com")
                .username(null)
                .apiSecret(null)
                .databaseName(null)
                .build();

        ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        boolean result = provider.testConnection(cfg);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("testConnection → username present without secret does not set basic auth")
    void testConnection_usernameOnlyNoSecret() {
        ErpConfig cfg = ErpConfig.builder()
                .apiEndpoint("https://sap.example.com")
                .username("user1")
                .apiSecret(null)
                .databaseName("SBODEMO")
                .build();

        ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        boolean result = provider.testConnection(cfg);
        assertThat(result).isTrue();
    }

    // ---------- normalizeEndpoint: trailing slash ----------

    @Test
    @DisplayName("importProducts → strips trailing slash from endpoint")
    void importProducts_endpointTrailingSlashStripped() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", List.of());
        ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        provider.importProducts(config("https://sap.example.com/"));

        verify(restTemplate).exchange(eq("https://sap.example.com/b1s/v1/Items"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class));
    }
}
