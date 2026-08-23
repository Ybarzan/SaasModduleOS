package com.incokalk.service.carrier.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DhlBookingApiClient - Test contractuel (format de requete)")
class DhlBookingApiClientContractTest {

    private MockWebServer server;
    private DhlBookingApiClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        client = new DhlBookingApiClient(new RestTemplate(), new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(client, "apiKey", "dhl_key");
        ReflectionTestUtils.setField(client, "apiSecret", "dhl_secret");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("POST /shipments avec Basic auth et body conforme")
    void submitBooking_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"shipmentTrackingNumber\":\"DHL-REF-123\","
                + "\"totalPrice\":[{\"price\":\"123.45\"}],"
                + "\"estimatedTransitDays\":4,"
                + "\"estimatedPickupDate\":\"2026-08-12\"}"));

        Carrier carrier = new Carrier();
        carrier.setCode("DHL");

        ShipmentOrder shipment = new ShipmentOrder();
        shipment.setOrderNumber("SO-1001");
        shipment.setShipperName("Acme SAS");
        shipment.setShipperAddress("12 rue de Lyon");
        shipment.setShipperCity("Lyon");
        shipment.setShipperCountry("FR");
        shipment.setConsigneeName("Buyer GmbH");
        shipment.setConsigneeAddress("Hauptstr 1");
        shipment.setConsigneeCity("Berlin");
        shipment.setConsigneeCountry("DE");
        shipment.setWeightKg(80.0);
        shipment.setGoodsDescription("Machines");

        CarrierBookingRequest req = new CarrierBookingRequest();
        req.setRequestedPickupDate(LocalDate.of(2026, 8, 10));
        req.setServiceType("EXPRESS");

        BookingResponse resp = client.submitBooking(carrier, shipment, req);

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/shipments");
        assertThat(recorded.getHeader("Authorization"))
            .isEqualTo("Basic " + Base64.getEncoder().encodeToString("dhl_key:dhl_secret".getBytes()));
        assertThat(recorded.getHeader("Content-Type")).contains("application/json");

        JsonNode body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertThat(body.path("plannedShippingDateAndTime").path("date").asText()).isEqualTo("2026-08-10");
        assertThat(body.path("shipper").path("companyName").asText()).isEqualTo("Acme SAS");
        assertThat(body.path("receiver").path("companyName").asText()).isEqualTo("Buyer GmbH");
        assertThat(body.path("packages").get(0).path("weight").path("value").asDouble()).isEqualTo(80.0);
        assertThat(body.path("shipmentType").asText()).isEqualTo("EXPRESS");

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DHL-REF-123");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(123.45));
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("GET statut sur /shipments/{reference}")
    void getStatus_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"IN_TRANSIT\"}"));

        BookingResponse resp = client.getStatus("DHL-REF-123");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/shipments/DHL-REF-123");
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DHL-REF-123");
    }
}
