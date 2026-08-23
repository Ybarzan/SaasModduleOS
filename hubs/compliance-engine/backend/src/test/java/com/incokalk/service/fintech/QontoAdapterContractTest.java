package com.incokalk.service.fintech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.model.FintechConnection;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QontoAdapter - Test contractuel (format des appels)")
class QontoAdapterContractTest {

    private MockWebServer server;
    private QontoAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        adapter = new QontoAdapter(new RestTemplate(), new ObjectMapper());
        ReflectionTestUtils.setField(adapter, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(adapter, "defaultApiKey", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private FintechConnection connection() {
        return FintechConnection.builder()
            .provider(FintechConnection.Provider.QONTO)
            .apiKey("slug:secret")
            .build();
    }

    @Test
    @DisplayName("GET /v2/organizations/bank_accounts avec Authorization slug:secret")
    void fetchAccounts_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":[{\"id\":\"ACC-1\",\"iban\":\"FR7612345678\",\"balance_cents\":125000}]}"));

        List<Map<String, Object>> accounts = adapter.fetchAccounts(connection());

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v2/organizations/bank_accounts");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("slug:secret");

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).get("iban")).isEqualTo("FR7612345678");
    }

    @Test
    @DisplayName("GET /v2/transactions")
    void fetchTransactions_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":[{\"transaction_id\":\"TXN-1\",\"amount\":-5000,\"currency\":\"EUR\"}]}"));

        List<Map<String, Object>> transactions = adapter.fetchTransactions(connection());

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v2/transactions");

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).get("transaction_id")).isEqualTo("TXN-1");
    }

    @Test
    @DisplayName("GET /v2/organizations/attachments pour les factures a traiter")
    void fetchExpenses_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":[{\"id\":\"ATT-1\",\"status\":\"to_process\"}]}"));

        List<Map<String, Object>> expenses = adapter.fetchExpenses(connection());

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v2/organizations/attachments?status=to_process");

        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).get("id")).isEqualTo("ATT-1");
    }
}
