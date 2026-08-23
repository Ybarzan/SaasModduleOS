package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.ShipmentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OdooProviderTest {

    private RestTemplate restTemplate;
    private OdooProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        provider = new OdooProvider(restTemplate);
    }

    private ErpConfig config() {
        return ErpConfig.builder()
                .apiEndpoint("https://odoo.example.com")
                .databaseName("mydb")
                .username("admin")
                .apiKey("secret-key")
                .build();
    }

    private ResponseEntity<String> ok(String body) {
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    private static final String AUTH_OK = """
            <?xml version="1.0"?>
            <methodResponse><params><param><value><int>7</int></value></param></params></methodResponse>
            """;

    private static final String AUTH_INVALID = """
            <?xml version="1.0"?>
            <methodResponse><params><param><value><int>0</int></value></param></params></methodResponse>
            """;

    private static final String RECORDS_XML = """
            <?xml version="1.0"?>
            <methodResponse><params><param><value><array><data>
              <value><struct>
                <member><name>name</name><value><string>Widget</string></value></member>
                <member><name>list_price</name><value><double>19.99</double></value></member>
              </struct></value>
              <value><struct>
                <member><name>name</name><value><string>Gadget</string></value></member>
                <member><name>list_price</name><value><double>29.99</double></value></member>
              </struct></value>
            </data></array></value></param></params></methodResponse>
            """;

    private static final String EMPTY_RECORDS_XML = """
            <?xml version="1.0"?>
            <methodResponse><params><param><value><array><data></data></array></value></param></params></methodResponse>
            """;

    private void stubAuthOk() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_OK));
    }

    // ── simple getters ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getErpType → returns ODOO")
    void getErpType_value() {
        assertThat(provider.getErpType()).isEqualTo("ODOO");
    }

    @Test
    @DisplayName("getName → returns Odoo")
    void getName_value() {
        assertThat(provider.getName()).isEqualTo("Odoo");
    }

    // ── testConnection ──────────────────────────────────────────────────────

    @Test
    @DisplayName("testConnection → true when common endpoint returns 2xx with body")
    void testConnection_success() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_OK));

        assertThat(provider.testConnection(config())).isTrue();
    }

    @Test
    @DisplayName("testConnection → false when response is non-2xx")
    void testConnection_non2xx() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(AUTH_OK, HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(provider.testConnection(config())).isFalse();
    }

    @Test
    @DisplayName("testConnection → false when response body is null")
    void testConnection_nullBody() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThat(provider.testConnection(config())).isFalse();
    }

    @Test
    @DisplayName("testConnection → false when endpoint is missing")
    void testConnection_missingEndpoint() {
        ErpConfig cfg = config();
        cfg.setApiEndpoint(null);

        assertThat(provider.testConnection(cfg)).isFalse();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("testConnection → false when endpoint is blank")
    void testConnection_blankEndpoint() {
        ErpConfig cfg = config();
        cfg.setApiEndpoint("   ");

        assertThat(provider.testConnection(cfg)).isFalse();
    }

    @Test
    @DisplayName("testConnection → false when RestTemplate throws")
    void testConnection_exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThat(provider.testConnection(config())).isFalse();
    }

    // ── importProducts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("importProducts → success with records")
    void importProducts_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        ErpSyncResult result = provider.importProducts(config());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isZero();
    }

    @Test
    @DisplayName("importProducts → success with zero records")
    void importProducts_emptyRecords() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(EMPTY_RECORDS_XML));

        ErpSyncResult result = provider.importProducts(config());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isZero();
    }

    @Test
    @DisplayName("importProducts → failure when authentication returns invalid uid")
    void importProducts_authInvalidUid() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_INVALID));

        ErpSyncResult result = provider.importProducts(config());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("identifiants invalides");
    }

    @Test
    @DisplayName("importProducts → failure when authentication body is null")
    void importProducts_authNullBody() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        ErpSyncResult result = provider.importProducts(config());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Authentification");
    }

    @Test
    @DisplayName("importProducts → failure when authentication response non-2xx")
    void importProducts_authNon2xx() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(AUTH_OK, HttpStatus.INTERNAL_SERVER_ERROR));

        ErpSyncResult result = provider.importProducts(config());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("importProducts → failure when endpoint missing")
    void importProducts_missingEndpoint() {
        ErpConfig cfg = config();
        cfg.setApiEndpoint(null);

        ErpSyncResult result = provider.importProducts(cfg);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Endpoint");
    }

    // ── importOrders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("importOrders → success with records")
    void importOrders_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        ErpSyncResult result = provider.importOrders(config());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("importOrders → failure when auth fails")
    void importOrders_authFailure() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_INVALID));

        ErpSyncResult result = provider.importOrders(config());
        assertThat(result.isSuccess()).isFalse();
    }

    // ── importContacts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("importContacts → success with records")
    void importContacts_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        ErpSyncResult result = provider.importContacts(config());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsSynced()).isEqualTo(2);
    }

    @Test
    @DisplayName("importContacts → failure when auth request throws")
    void importContacts_authFailure() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("network down"));

        ErpSyncResult result = provider.importContacts(config());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("network down");
    }

    // ── exportShipments ─────────────────────────────────────────────────────

    @Test
    @DisplayName("exportShipments → mixed success, failure and exception outcomes")
    void exportShipments_mixedOutcomes() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("<ok/>"))
                .thenReturn(ok("<ok/>"))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(new ResponseEntity<>("<err/>", HttpStatus.INTERNAL_SERVER_ERROR));

        List<ShipmentOrder> shipments = List.of(
                ShipmentOrder.builder().orderNumber("SO-1").consigneeName("Acme").build(),
                ShipmentOrder.builder().orderNumber(null).consigneeName(null).build(),
                ShipmentOrder.builder().orderNumber("SO-3").consigneeName("Beta").build(),
                ShipmentOrder.builder().orderNumber("SO-4").consigneeName("Gamma").build()
        );

        ErpSyncResult result = provider.exportShipments(config(), shipments);

        assertThat(result.getRecordsTotal()).isEqualTo(4);
        assertThat(result.getRecordsSynced()).isEqualTo(2);
        assertThat(result.getRecordsFailed()).isEqualTo(2);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("exportShipments → success when list is empty")
    void exportShipments_emptyList() {
        stubAuthOk();

        ErpSyncResult result = provider.exportShipments(config(), List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsTotal()).isZero();
        assertThat(result.getRecordsSynced()).isZero();
        assertThat(result.getRecordsFailed()).isZero();
    }

    @Test
    @DisplayName("exportShipments → failure when authentication fails")
    void exportShipments_authFailure() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_INVALID));

        ErpSyncResult result = provider.exportShipments(config(),
                List.of(ShipmentOrder.builder().orderNumber("SO-1").build()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("identifiants invalides");
    }

    // ── exportOrders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("exportOrders → mixed success and failure outcomes")
    void exportOrders_mixedOutcomes() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("<ok/>"))
                .thenReturn(new ResponseEntity<>("<err/>", HttpStatus.BAD_REQUEST));

        List<ShipmentOrder> orders = List.of(
                ShipmentOrder.builder().orderNumber("SO-1").consigneeName("Acme").build(),
                ShipmentOrder.builder().orderNumber(null).consigneeName(null).build()
        );

        ErpSyncResult result = provider.exportOrders(config(), orders);

        assertThat(result.getRecordsTotal()).isEqualTo(2);
        assertThat(result.getRecordsSynced()).isEqualTo(1);
        assertThat(result.getRecordsFailed()).isEqualTo(1);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("exportOrders → success when all exports succeed")
    void exportOrders_allSuccess() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok("<ok/>"));

        List<ShipmentOrder> orders = List.of(ShipmentOrder.builder().orderNumber("SO-1").consigneeName("Acme").build());

        ErpSyncResult result = provider.exportOrders(config(), orders);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecordsFailed()).isZero();
    }

    @Test
    @DisplayName("exportOrders → failure when authentication throws")
    void exportOrders_authException() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("dns error"));

        ErpSyncResult result = provider.exportOrders(config(),
                List.of(ShipmentOrder.builder().orderNumber("SO-1").build()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("dns error");
    }

    // ── getProducts / getOrders / getContacts ───────────────────────────────

    @Test
    @DisplayName("getProducts → returns parsed records")
    void getProducts_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        List<Map<String, Object>> result = provider.getProducts(config());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("name")).isEqualTo("Widget");
        assertThat(result.get(0).get("list_price")).isEqualTo(19.99);
    }

    @Test
    @DisplayName("getProducts → returns empty list on exception")
    void getProducts_exception() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("boom"));

        List<Map<String, Object>> result = provider.getProducts(config());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrders → returns parsed records")
    void getOrders_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        List<Map<String, Object>> result = provider.getOrders(config());
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getOrders → returns empty list when authentication is invalid")
    void getOrders_exception() {
        when(restTemplate.exchange(contains("2/common"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(AUTH_INVALID));

        List<Map<String, Object>> result = provider.getOrders(config());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getContacts → returns parsed records")
    void getContacts_success() {
        stubAuthOk();
        when(restTemplate.exchange(contains("2/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ok(RECORDS_XML));

        List<Map<String, Object>> result = provider.getContacts(config());
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getContacts → returns empty list when endpoint is blank")
    void getContacts_missingEndpoint() {
        ErpConfig cfg = config();
        cfg.setApiEndpoint("");

        List<Map<String, Object>> result = provider.getContacts(cfg);
        assertThat(result).isEmpty();
    }

    // ── buildXmlRpcUrl (reflection) ─────────────────────────────────────────

    @Test
    @DisplayName("buildXmlRpcUrl → strips trailing slash from endpoint")
    void buildXmlRpcUrl_stripsTrailingSlash() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("buildXmlRpcUrl", ErpConfig.class, String.class);
        m.setAccessible(true);

        ErpConfig cfg = config();
        cfg.setApiEndpoint("https://odoo.example.com/");

        String url = (String) m.invoke(provider, cfg, "common");
        assertThat(url).isEqualTo("https://odoo.example.com/xmlrpc/2/common");
    }

    @Test
    @DisplayName("buildXmlRpcUrl → throws when endpoint is null")
    void buildXmlRpcUrl_nullEndpoint() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("buildXmlRpcUrl", ErpConfig.class, String.class);
        m.setAccessible(true);

        ErpConfig cfg = config();
        cfg.setApiEndpoint(null);

        assertThatThrownBy(() -> m.invoke(provider, cfg, "common"))
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("buildXmlRpcUrl → throws when endpoint is blank")
    void buildXmlRpcUrl_blankEndpoint() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("buildXmlRpcUrl", ErpConfig.class, String.class);
        m.setAccessible(true);

        ErpConfig cfg = config();
        cfg.setApiEndpoint("   ");

        assertThatThrownBy(() -> m.invoke(provider, cfg, "common"))
                .hasCauseInstanceOf(RuntimeException.class);
    }

    // ── serializeValue (reflection) ─────────────────────────────────────────

    @Test
    @DisplayName("serializeValue → handles all supported value types and default fallback")
    void serializeValue_allTypes() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("serializeValue", Object.class);
        m.setAccessible(true);

        assertThat((String) m.invoke(provider, (Object) null)).contains("<nil/>");
        assertThat((String) m.invoke(provider, "hi & <bye>")).contains("&amp;").contains("&lt;").contains("&gt;");
        assertThat((String) m.invoke(provider, 42)).contains("<int>42</int>");
        assertThat((String) m.invoke(provider, 42L)).contains("<long>42</long>");
        assertThat((String) m.invoke(provider, 3.14)).contains("<double>3.14</double>");
        assertThat((String) m.invoke(provider, true)).contains("<boolean>1</boolean>");
        assertThat((String) m.invoke(provider, false)).contains("<boolean>0</boolean>");
        assertThat((String) m.invoke(provider, List.of("a", "b"))).contains("<array>");
        assertThat((String) m.invoke(provider, Map.of("k", "v"))).contains("<struct>").contains("<name>k</name>");
        assertThat((String) m.invoke(provider, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .contains("<string>00000000-0000-0000-0000-000000000001</string>");
    }

    // ── parseXmlRpcValue (reflection) ───────────────────────────────────────

    @Test
    @DisplayName("parseXmlRpcValue → parses int, i4, string, double, boolean, nil and default text")
    void parseXmlRpcValue_allTypes() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("parseXmlRpcValue", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(provider, "<value><int>5</int></value>")).isEqualTo(5);
        assertThat(m.invoke(provider, "<value><i4>6</i4></value>")).isEqualTo(6);
        assertThat(m.invoke(provider, "<value><string>hello</string></value>")).isEqualTo("hello");
        assertThat(m.invoke(provider, "<value><double>1.5</double></value>")).isEqualTo(1.5);
        assertThat(m.invoke(provider, "<value><boolean>1</boolean></value>")).isEqualTo(true);
        assertThat(m.invoke(provider, "<value><boolean>0</boolean></value>")).isEqualTo(false);
        assertThat(m.invoke(provider, "<value><nil/></value>")).isNull();
        assertThat(m.invoke(provider, "<value>plain text</value>")).isEqualTo("<value>plain text</value>");
    }

    // ── parseXmlRpcInt (reflection) ─────────────────────────────────────────

    @Test
    @DisplayName("parseXmlRpcInt → parses <int> and <i4> and returns -1 when absent")
    void parseXmlRpcInt_variants() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("parseXmlRpcInt", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(provider, "<value><int>9</int></value>")).isEqualTo(9);
        assertThat(m.invoke(provider, "<value><i4>3</i4></value>")).isEqualTo(3);
        assertThat(m.invoke(provider, "<value><string>no int here</string></value>")).isEqualTo(-1);
    }

    // ── parseXmlRpcArray / parseXmlRpcStruct (reflection) ───────────────────

    @Test
    @DisplayName("parseXmlRpcArray → returns empty list when xml is null")
    void parseXmlRpcArray_nullXml() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("parseXmlRpcArray", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) m.invoke(provider, (Object) null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseXmlRpcArray → skips empty structs")
    void parseXmlRpcArray_emptyStruct() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("parseXmlRpcArray", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) m.invoke(provider, "<struct></struct>");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseXmlRpcStruct → skips members with missing name or value tags")
    void parseXmlRpcStruct_malformedMembers() throws Exception {
        Method m = OdooProvider.class.getDeclaredMethod("parseXmlRpcStruct", String.class);
        m.setAccessible(true);

        String xml = "<struct>"
                + "<member><value><string>orphan</string></value></member>"
                + "<member><name>onlyName</name></member>"
                + "<member><name>ok</name><value><string>fine</string></value></member>"
                + "</struct>";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) m.invoke(provider, xml);
        assertThat(result).containsEntry("ok", "fine");
        assertThat(result).hasSize(1);
    }
}
