package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("QuickBooksProvider — Tests unitaires")
class QuickBooksProviderTest {

    private RestTemplate restTemplate;
    private QuickBooksProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        provider = new QuickBooksProvider(restTemplate);
    }

    private ErpConfig configWithCompanyId() {
        return ErpConfig.builder()
                .apiEndpoint("123145678")
                .apiKey("token-abc")
                .build();
    }

    private ErpConfig configWithoutCompanyId() {
        return ErpConfig.builder()
                .apiEndpoint(null)
                .apiKey("token-abc")
                .build();
    }

    private ErpConfig configWithBlankCompanyId() {
        return ErpConfig.builder()
                .apiEndpoint("   ")
                .apiKey("token-abc")
                .build();
    }

    private ErpConfig configWithoutApiKey() {
        return ErpConfig.builder()
                .apiEndpoint("123145678")
                .apiKey(null)
                .build();
    }

    // ---------------------------------------------------------------
    // getErpType / getName
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getErpType → QUICKBOOKS")
    void getErpType_returnsQuickbooks() {
        assertThat(provider.getErpType()).isEqualTo("QUICKBOOKS");
    }

    @Test
    @DisplayName("getName → QuickBooks Online")
    void getName_returnsFriendlyName() {
        assertThat(provider.getName()).isEqualTo("QuickBooks Online");
    }

    // ---------------------------------------------------------------
    // testConnection
    // ---------------------------------------------------------------

    @Test
    @DisplayName("testConnection → true quand réponse 2xx avec body")
    void testConnection_success() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"QueryResponse\":{}}", HttpStatus.OK));

        boolean result = provider.testConnection(configWithCompanyId());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("testConnection → false quand statut non-2xx")
    void testConnection_nonSuccessStatus() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("error", HttpStatus.UNAUTHORIZED));

        boolean result = provider.testConnection(configWithCompanyId());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → false quand body null malgré 2xx")
    void testConnection_nullBody() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        boolean result = provider.testConnection(configWithCompanyId());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → false quand exception levée (companyId manquant)")
    void testConnection_missingCompanyId() {
        boolean result = provider.testConnection(configWithoutCompanyId());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → false quand exception réseau")
    void testConnection_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        boolean result = provider.testConnection(configWithCompanyId());

        assertThat(result).isFalse();
    }

    // ---------------------------------------------------------------
    // importProducts
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importProducts → succès avec des enregistrements")
    void importProducts_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of(
                        "Item", List.of(
                                Map.of("Id", "1", "Name", "Widget"),
                                Map.of("Id", "2", "Name", "Gadget"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("importProducts → échec quand companyId manquant")
    void importProducts_missingCompanyId() {
        ErpSyncResult result = provider.importProducts(configWithoutCompanyId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("importProducts → échec sur exception réseau")
    void importProducts_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("boom"));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("importProducts → liste vide quand statut non-2xx")
    void importProducts_nonSuccessStatus() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.BAD_REQUEST));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(0);
    }

    // ---------------------------------------------------------------
    // importOrders
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importOrders → succès avec des enregistrements")
    void importOrders_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of(
                        "SalesOrder", List.of(Map.of("Id", "10"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ErpSyncResult result = provider.importOrders(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("importOrders → échec quand companyId manquant")
    void importOrders_missingCompanyId() {
        ErpSyncResult result = provider.importOrders(configWithBlankCompanyId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("importOrders → échec sur exception réseau")
    void importOrders_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("network down"));

        ErpSyncResult result = provider.importOrders(configWithCompanyId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("network down");
    }

    // ---------------------------------------------------------------
    // importContacts
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importContacts → succès avec des enregistrements")
    void importContacts_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of(
                        "Customer", List.of(Map.of("Id", "5", "DisplayName", "Acme"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ErpSyncResult result = provider.importContacts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("importContacts → échec quand companyId manquant")
    void importContacts_missingCompanyId() {
        ErpSyncResult result = provider.importContacts(configWithoutCompanyId());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("importContacts → échec sur exception réseau")
    void importContacts_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("boom"));

        ErpSyncResult result = provider.importContacts(configWithCompanyId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("boom");
    }

    // ---------------------------------------------------------------
    // exportShipments/exportOrders ont été retirées avec le domaine Transport
    // (voir ErpProvider) — leurs tests partaient avec elles.
    // ---------------------------------------------------------------

    // ---------------------------------------------------------------
    // getProducts / getOrders / getContacts
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getProducts → retourne les enregistrements")
    void getProducts_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of("Item", List.of(Map.of("Id", "1"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        List<Map<String, Object>> result = provider.getProducts(configWithCompanyId());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getProducts → liste vide sur exception")
    void getProducts_exceptionReturnsEmptyList() {
        List<Map<String, Object>> result = provider.getProducts(configWithoutCompanyId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrders → retourne les enregistrements")
    void getOrders_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of("SalesOrder", List.of(Map.of("Id", "1"), Map.of("Id", "2"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        List<Map<String, Object>> result = provider.getOrders(configWithCompanyId());

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getOrders → liste vide sur exception")
    void getOrders_exceptionReturnsEmptyList() {
        List<Map<String, Object>> result = provider.getOrders(configWithoutCompanyId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getContacts → retourne les enregistrements")
    void getContacts_success() {
        Map<String, Object> body = Map.of(
                "QueryResponse", Map.of("Customer", List.of(Map.of("Id", "1"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        List<Map<String, Object>> result = provider.getContacts(configWithCompanyId());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getContacts → liste vide sur exception")
    void getContacts_exceptionReturnsEmptyList() {
        List<Map<String, Object>> result = provider.getContacts(configWithoutCompanyId());

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------
    // fetchQueryResults branches: pagination, missing QueryResponse,
    // non-map item entries, no api key
    // ---------------------------------------------------------------

    @Test
    @DisplayName("importProducts → pagination via nextQuery jusqu'à épuisement")
    void importProducts_pagination() {
        Map<String, Object> firstPage = Map.of(
                "QueryResponse", Map.of(
                        "Item", List.of(Map.of("Id", "1")),
                        "nextQuery", "https://quickbooks.api.intuit.com/v3/company/123145678/query?next=1"));
        Map<String, Object> secondPage = Map.of(
                "QueryResponse", Map.of(
                        "Item", List.of(Map.of("Id", "2"))));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(firstPage, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(secondPage, HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("importProducts → QueryResponse absent du corps")
    void importProducts_missingQueryResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("other", "value"), HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("importProducts → entrée non-Map dans la liste Item ignorée")
    void importProducts_nonMapListEntryIgnored() {
        List<Object> itemsWithGarbage = new java.util.ArrayList<>();
        itemsWithGarbage.add("not-a-map");
        itemsWithGarbage.add(Map.of("Id", "1"));
        Map<String, Object> body = Map.of("QueryResponse", Map.of("Item", itemsWithGarbage));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(configWithCompanyId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("importProducts → sans clé API (pas d'en-tête Authorization)")
    void importProducts_withoutApiKey() {
        Map<String, Object> body = Map.of("QueryResponse", Map.of("Item", List.of(Map.of("Id", "1"))));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(configWithoutApiKey());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(1);
    }
}
