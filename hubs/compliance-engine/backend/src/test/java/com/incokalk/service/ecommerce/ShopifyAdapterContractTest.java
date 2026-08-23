package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ShopifyAdapter - Test contractuel (format des appels)")
class ShopifyAdapterContractTest {

    private MockWebServer server;
    private ShopifyAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        adapter = new ShopifyAdapter(new RestTemplate(), mock(ShipmentOrderRepository.class), mock(CompanyRepository.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ECommerceIntegration integration() {
        ECommerceIntegration integration = new ECommerceIntegration();
        integration.setStoreUrl(server.url("/").toString().replaceAll("/$", ""));
        integration.setApiSecret("shptok_123");
        integration.setPlatform(ECommerceIntegration.Platform.SHOPIFY);
        return integration;
    }

    @Test
    @DisplayName("GET /admin/api/2024-01/orders.json avec X-Shopify-Access-Token")
    void syncOrders_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"orders\":[{\"id\":1001,\"order_number\":\"1001\",\"currency\":\"EUR\","
                + "\"total_price\":\"99.50\",\"line_items\":[{\"title\":\"Chaise\"}],"
                + "\"shipping_address\":{\"name\":\"Client X\",\"address1\":\"1 rue A\","
                + "\"city\":\"Paris\",\"country_code\":\"FR\",\"zip\":\"75001\"}}]}"));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"orders\":[]}"));

        List<Map<String, Object>> orders = adapter.syncOrders(integration());

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).startsWith("/admin/api/2024-01/orders.json");
        assertThat(recorded.getPath()).contains("status=any");
        assertThat(recorded.getPath()).contains("limit=250");
        assertThat(recorded.getHeader("X-Shopify-Access-Token")).isEqualTo("shptok_123");

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).get("id")).isEqualTo(1001);
    }

    @Test
    @DisplayName("GET /orders/{id}.json pour une commande precise")
    void getOrder_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"order\":{\"id\":42,\"order_number\":\"42\"}}"));

        Map<String, Object> order = adapter.getOrder(integration(), "42");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/admin/api/2024-01/orders/42.json");
        assertThat(recorded.getHeader("X-Shopify-Access-Token")).isEqualTo("shptok_123");

        assertThat(order.get("id")).isEqualTo(42);
    }
}
