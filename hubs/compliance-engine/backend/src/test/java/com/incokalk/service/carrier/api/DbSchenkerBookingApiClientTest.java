package com.incokalk.service.carrier.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DbSchenkerBookingApiClient — Tests unitaires")
class DbSchenkerBookingApiClientTest {

    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    DbSchenkerBookingApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new DbSchenkerBookingApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.dbschenker.com/booking/v1");
        ReflectionTestUtils.setField(client, "apiKey", "dbs-api-key");
    }

    private Carrier carrier(String transportModes) {
        Carrier c = new Carrier();
        c.setCode("DBS");
        c.setTransportModes(transportModes);
        return c;
    }

    private ShipmentOrder shipment() {
        ShipmentOrder s = new ShipmentOrder();
        s.setOrderNumber("SO-2001");
        s.setGoodsDescription("Electronics");
        s.setWeightKg(45.0);
        s.setVolumeM3(1.2);
        s.setPackagesCount(3);
        s.setDangerous(false);
        s.setShipperName("Acme SAS");
        s.setShipperAddress("12 rue de Lyon");
        s.setShipperCity("Lyon");
        s.setShipperCountry("FR");
        s.setConsigneeName("Buyer GmbH");
        s.setConsigneeAddress("Hauptstr 1");
        s.setConsigneeCity("Berlin");
        s.setConsigneeCountry("DE");
        return s;
    }

    private CarrierBookingRequest bookingRequest(String serviceType, LocalDate pickupDate) {
        CarrierBookingRequest req = new CarrierBookingRequest();
        req.setServiceType(serviceType);
        req.setRequestedPickupDate(pickupDate);
        return req;
    }

    @SuppressWarnings("unchecked")
    private void mockExchange(HttpMethod method, String body, HttpStatus status) {
        ResponseEntity<String> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(method), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }

    // ---------- isConfigured ----------

    @Test
    @DisplayName("isConfigured — clé API présente → true")
    void isConfigured_apiKeyPresent_true() {
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("isConfigured — clé API null → false")
    void isConfigured_apiKeyNull_false() {
        ReflectionTestUtils.setField(client, "apiKey", null);
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured — clé API vide/blanche → false")
    void isConfigured_apiKeyBlank_false() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");
        assertThat(client.isConfigured()).isFalse();
    }

    // ---------- submitBooking — not configured ----------

    @Test
    @DisplayName("submitBooking — non configuré → null, pas d'appel HTTP")
    void submitBooking_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("EXPRESS", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    // ---------- submitBooking — success paths ----------

    @Test
    @DisplayName("submitBooking — 201 CREATED avec corps complet → BookingResponse rempli")
    void submitBooking_created_fullBody_returnsParsedResponse() {
        String body = """
                {"orderNumber":"DBS-ORDER-1","trackingNumber":"DBTRACK123",
                "totalCost":250.75,"estimatedPickup":"2026-08-15",
                "estimatedTransitDays":6,"estimatedDelivery":"2026-08-21"}
                """;
        mockExchange(HttpMethod.POST, body, HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(
                carrier("SEA,AIR"), shipment(), bookingRequest("EXPRESS", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DBS-ORDER-1");
        assertThat(resp.getTrackingNumber()).isEqualTo("DBTRACK123");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(250.75));
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getEstimatedPickupDate()).isEqualTo("2026-08-15");
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(6);
        assertThat(resp.getEstimatedDeliveryDate()).isEqualTo("2026-08-21");

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("https://api.dbschenker.com/booking/v1/orders"),
                eq(HttpMethod.POST), captor.capture(), eq(String.class));
        java.util.Map<String, Object> sentBody = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat(sentBody.get("serviceType")).isEqualTo("EXPRESS");
        assertThat(sentBody.get("transportMode")).isEqualTo("SEA");
        java.util.Map<String, Object> pickup = (java.util.Map<String, Object>) sentBody.get("pickup");
        assertThat(pickup.get("requestedDate")).isEqualTo("2026-08-20");
    }

    @Test
    @DisplayName("submitBooking — 200 OK avec corps → BookingResponse rempli")
    void submitBooking_ok_returnsParsedResponse() {
        String body = "{\"orderNumber\":\"DBS-ORDER-2\"}";
        mockExchange(HttpMethod.POST, body, HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier("ROAD"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNotNull();
        assertThat(resp.getCarrierReference()).isEqualTo("DBS-ORDER-2");
    }

    @Test
    @DisplayName("submitBooking — serviceType null → défaut STANDARD envoyé")
    void submitBooking_nullServiceType_defaultsToStandard() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.CREATED);

        client.submitBooking(carrier("SEA"), shipment(), bookingRequest(null, LocalDate.of(2026, 8, 20)));

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        java.util.Map<String, Object> sentBody = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat(sentBody.get("serviceType")).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("submitBooking — requestedPickupDate null → date par défaut (aujourd'hui + 2 jours)")
    void submitBooking_nullPickupDate_defaultsToTwoDaysFromNow() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.CREATED);

        client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", null));

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        java.util.Map<String, Object> sentBody = (java.util.Map<String, Object>) captor.getValue().getBody();
        java.util.Map<String, Object> pickup = (java.util.Map<String, Object>) sentBody.get("pickup");
        assertThat(pickup.get("requestedDate")).isEqualTo(LocalDate.now().plusDays(2).toString());
    }

    @Test
    @DisplayName("submitBooking — transportModes null sur le transporteur → pas de champ transportMode")
    void submitBooking_nullTransportModes_omitsTransportModeField() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.CREATED);

        client.submitBooking(carrier(null), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        java.util.Map<String, Object> sentBody = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat(sentBody).doesNotContainKey("transportMode");
    }

    @Test
    @DisplayName("submitBooking — corps de réponse vide (defaults Jackson) → references générées")
    void submitBooking_emptyResponseBody_usesGeneratedDefaults() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNotNull();
        assertThat(resp.getCarrierReference()).startsWith("DBS-");
        assertThat(resp.getTrackingNumber()).startsWith("DB");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(5);
    }

    // ---------- submitBooking — non-success / error paths ----------

    @Test
    @DisplayName("submitBooking — statut inattendu (ex: 202 ACCEPTED) → null")
    void submitBooking_unexpectedStatus_returnsNull() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.ACCEPTED);

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — 201 CREATED mais corps null → null")
    void submitBooking_createdWithNullBody_returnsNull() {
        mockExchange(HttpMethod.POST, null, HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — 200 OK mais corps null → null")
    void submitBooking_okWithNullBody_returnsNull() {
        mockExchange(HttpMethod.POST, null, HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — JSON malformé dans la réponse → null (parsing échoue)")
    void submitBooking_malformedJson_returnsNull() {
        mockExchange(HttpMethod.POST, "{not-valid-json!!", HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — exception réseau → null")
    void submitBooking_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse resp = client.submitBooking(carrier("SEA"), shipment(), bookingRequest("STANDARD", LocalDate.of(2026, 8, 20)));

        assertThat(resp).isNull();
    }

    // ---------- getStatus ----------

    @Test
    @DisplayName("getStatus — non configuré → null, pas d'appel HTTP")
    void getStatus_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNull();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("getStatus — 200 OK avec corps → BookingResponse avec currentStatus et lastCheckpoint")
    void getStatus_ok_returnsParsedStatus() {
        mockExchange(HttpMethod.GET, "{\"status\":\"DELIVERED\",\"lastCheckpoint\":\"Paris hub\"}", HttpStatus.OK);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DBS-ORDER-1");
        assertThat(resp.getAdditionalData()).containsEntry("currentStatus", "DELIVERED");
        assertThat(resp.getAdditionalData()).containsEntry("lastCheckpoint", "Paris hub");

        verify(restTemplate).exchange(eq("https://api.dbschenker.com/booking/v1/orders/DBS-ORDER-1/status"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("getStatus — corps vide → defaults status IN_TRANSIT / lastCheckpoint vide")
    void getStatus_emptyBody_usesDefaults() {
        mockExchange(HttpMethod.GET, "{}", HttpStatus.OK);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNotNull();
        assertThat(resp.getAdditionalData()).containsEntry("currentStatus", "IN_TRANSIT");
        assertThat(resp.getAdditionalData()).containsEntry("lastCheckpoint", "");
    }

    @Test
    @DisplayName("getStatus — statut non-OK → null")
    void getStatus_notOk_returnsNull() {
        mockExchange(HttpMethod.GET, "{\"status\":\"DELIVERED\"}", HttpStatus.NOT_FOUND);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — 200 OK mais corps null → null")
    void getStatus_okWithNullBody_returnsNull() {
        mockExchange(HttpMethod.GET, null, HttpStatus.OK);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — JSON malformé → null (parsing échoue)")
    void getStatus_malformedJson_returnsNull() {
        mockExchange(HttpMethod.GET, "{not-valid-json!!", HttpStatus.OK);

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — exception réseau → null")
    void getStatus_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse resp = client.getStatus("DBS-ORDER-1");

        assertThat(resp).isNull();
    }

    // ---------- cancelBooking ----------

    @Test
    @DisplayName("cancelBooking — non configuré → false, pas d'appel HTTP")
    void cancelBooking_notConfigured_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        boolean result = client.cancelBooking("DBS-ORDER-1");

        assertThat(result).isFalse();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("cancelBooking — 200 OK → true")
    void cancelBooking_ok_returnsTrue() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.OK);

        boolean result = client.cancelBooking("DBS-ORDER-1");

        assertThat(result).isTrue();
        verify(restTemplate).exchange(eq("https://api.dbschenker.com/booking/v1/orders/DBS-ORDER-1/cancel"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("cancelBooking — statut non-OK → false")
    void cancelBooking_notOk_returnsFalse() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.BAD_REQUEST);

        boolean result = client.cancelBooking("DBS-ORDER-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelBooking — exception réseau → false")
    void cancelBooking_networkException_returnsFalse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        boolean result = client.cancelBooking("DBS-ORDER-1");

        assertThat(result).isFalse();
    }
}
