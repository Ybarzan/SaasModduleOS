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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GeodisBookingApiClient — Tests unitaires")
class GeodisBookingApiClientTest {

    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    GeodisBookingApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new GeodisBookingApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.geodis.com/booking/v1");
        ReflectionTestUtils.setField(client, "apiKey", "geo-test-key");
    }

    private Carrier carrier() {
        return Carrier.builder().name("Geodis").code("GEODIS").build();
    }

    private ShipmentOrder shipment() {
        return ShipmentOrder.builder()
                .orderNumber("SO-2001")
                .shipperName("Acme SAS")
                .shipperAddress("12 rue de Lyon")
                .shipperCity("Lyon")
                .shipperCountry("FR")
                .consigneeName("Buyer GmbH")
                .consigneeAddress("Hauptstr 1")
                .consigneeCity("Berlin")
                .consigneeCountry("DE")
                .goodsDescription("Machines")
                .weightKg(80.0)
                .volumeM3(2.5)
                .packagesCount(3)
                .isDangerous(false)
                .build();
    }

    private CarrierBookingRequest request() {
        return CarrierBookingRequest.builder().build();
    }

    @SuppressWarnings("unchecked")
    private void mockPostResponse(String body, HttpStatus status) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
    }

    @SuppressWarnings("unchecked")
    private void mockGetResponse(String body, HttpStatus status) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
    }

    @SuppressWarnings("unchecked")
    private void mockDeleteResponse(String body, HttpStatus status) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, status));
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
    @DisplayName("submitBooking — non configuré → null, aucun appel HTTP")
    void submitBooking_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNull();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    // ---------- submitBooking — success ----------

    @Test
    @DisplayName("submitBooking — succès 201 CREATED, JSON complet → BookingResponse mappé")
    void submitBooking_created_fullJson_returnsMappedResponse() {
        String body = """
                {"shipmentNumber":"GEO-REF-1","trackingNumber":"GE12345678",
                "totalPrice":150.75,"estimatedPickupDate":"2026-08-15",
                "estimatedTransitDays":6,"estimatedDeliveryDate":"2026-08-21"}
                """;
        mockPostResponse(body, HttpStatus.CREATED);

        CarrierBookingRequest req = request();
        req.setServiceType("ROAD_FREIGHT");
        req.setRequestedPickupDate(LocalDate.of(2026, 8, 14));

        BookingResponse resp = client.submitBooking(carrier(), shipment(), req);

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("GEO-REF-1");
        assertThat(resp.getTrackingNumber()).isEqualTo("GE12345678");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo("150.75");
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getEstimatedPickupDate()).isEqualTo("2026-08-15");
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(6);
        assertThat(resp.getEstimatedDeliveryDate()).isEqualTo("2026-08-21");
    }

    @Test
    @DisplayName("submitBooking — succès 200 OK également accepté")
    void submitBooking_ok_alsoAccepted() {
        mockPostResponse("{\"shipmentNumber\":\"GEO-REF-2\"}", HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNotNull();
        assertThat(resp.getCarrierReference()).isEqualTo("GEO-REF-2");
    }

    @Test
    @DisplayName("submitBooking — JSON minimal (champs absents) → valeurs par défaut appliquées")
    void submitBooking_minimalJson_usesDefaults() {
        mockPostResponse("{}", HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("GEO-");
        assertThat(resp.getTrackingNumber()).startsWith("GE");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo("0");
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(4);
        assertThat(resp.getEstimatedPickupDate()).isNotBlank();
        assertThat(resp.getEstimatedDeliveryDate()).isNotBlank();
    }

    // ---------- submitBooking — failure branches ----------

    @Test
    @DisplayName("submitBooking — statut non-2xx (ex: BAD_REQUEST) → null")
    void submitBooking_badRequestStatus_returnsNull() {
        mockPostResponse("{\"shipmentNumber\":\"X\"}", HttpStatus.BAD_REQUEST);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — CREATED mais corps null → null")
    void submitBooking_createdWithNullBody_returnsNull() {
        mockPostResponse(null, HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — JSON malformé → parsing échoue, null retourné")
    void submitBooking_malformedJson_returnsNull() {
        mockPostResponse("{not-valid-json", HttpStatus.CREATED);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking — exception réseau → null, pas de propagation")
    void submitBooking_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse resp = client.submitBooking(carrier(), shipment(), request());

        assertThat(resp).isNull();
    }

    // ---------- submitBooking — request body construction branches ----------

    @Test
    @DisplayName("submitBooking — serviceType null → 'ROAD_FREIGHT' par défaut dans le corps")
    void submitBooking_nullServiceType_defaultsInBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setServiceType(null);

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body.get("serviceCode")).isEqualTo("ROAD_FREIGHT");
    }

    @Test
    @DisplayName("submitBooking — serviceType fourni → utilisé tel quel dans le corps")
    void submitBooking_serviceTypeProvided_usedInBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setServiceType("EXPRESS");

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body.get("serviceCode")).isEqualTo("EXPRESS");
    }

    @Test
    @DisplayName("submitBooking — date de collecte fournie → utilisée dans le corps")
    void submitBooking_pickupDateProvided_usedInBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setRequestedPickupDate(LocalDate.of(2026, 9, 1));

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> collection = (Map<String, Object>) body.get("collection");
        assertThat(collection.get("date")).isEqualTo("2026-09-01");
    }

    @Test
    @DisplayName("submitBooking — date de collecte absente → date par défaut (demain) dans le corps")
    void submitBooking_pickupDateNull_defaultsToTomorrowInBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setRequestedPickupDate(null);

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> collection = (Map<String, Object>) body.get("collection");
        assertThat(collection.get("date")).isEqualTo(LocalDate.now().plusDays(1).toString());
    }

    @Test
    @DisplayName("submitBooking — instructions spéciales absentes → clé 'instructions' absente du corps")
    void submitBooking_noSpecialInstructions_omittedFromBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setSpecialInstructions(null);

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body).doesNotContainKey("instructions");
    }

    @Test
    @DisplayName("submitBooking — instructions spéciales fournies → incluses dans le corps")
    void submitBooking_specialInstructionsProvided_includedInBody() {
        mockPostResponse("{}", HttpStatus.CREATED);
        CarrierBookingRequest req = request();
        req.setSpecialInstructions("Handle with care");

        client.submitBooking(carrier(), shipment(), req);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body.get("instructions")).isEqualTo("Handle with care");
    }

    // ---------- getStatus ----------

    @Test
    @DisplayName("getStatus — non configuré → null, aucun appel HTTP")
    void getStatus_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNull();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("getStatus — 200 OK avec corps → BookingResponse mappé")
    void getStatus_ok_returnsMappedResponse() {
        mockGetResponse("{\"status\":\"IN_TRANSIT\",\"lastEvent\":\"Departed hub\"}", HttpStatus.OK);

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("GEO-REF-1");
        assertThat(resp.getAdditionalData()).containsEntry("currentStatus", "IN_TRANSIT");
        assertThat(resp.getAdditionalData()).containsEntry("lastCheckpoint", "Departed hub");
    }

    @Test
    @DisplayName("getStatus — JSON minimal → valeurs par défaut (IN_TRANSIT, checkpoint vide)")
    void getStatus_minimalJson_usesDefaults() {
        mockGetResponse("{}", HttpStatus.OK);

        BookingResponse resp = client.getStatus("GEO-REF-9");

        assertThat(resp).isNotNull();
        assertThat(resp.getAdditionalData()).containsEntry("currentStatus", "IN_TRANSIT");
        assertThat(resp.getAdditionalData()).containsEntry("lastCheckpoint", "");
    }

    @Test
    @DisplayName("getStatus — corps null malgré 200 OK → null")
    void getStatus_okWithNullBody_returnsNull() {
        mockGetResponse(null, HttpStatus.OK);

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — statut non-OK → null")
    void getStatus_notOkStatus_returnsNull() {
        mockGetResponse("{\"status\":\"IN_TRANSIT\"}", HttpStatus.NOT_FOUND);

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — JSON malformé → parsing échoue, null retourné")
    void getStatus_malformedJson_returnsNull() {
        mockGetResponse("{not-valid-json", HttpStatus.OK);

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus — exception réseau → null")
    void getStatus_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        BookingResponse resp = client.getStatus("GEO-REF-1");

        assertThat(resp).isNull();
    }

    // ---------- cancelBooking ----------

    @Test
    @DisplayName("cancelBooking — non configuré → false, aucun appel HTTP")
    void cancelBooking_notConfigured_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        boolean result = client.cancelBooking("GEO-REF-1");

        assertThat(result).isFalse();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("cancelBooking — 204 NO_CONTENT → true")
    void cancelBooking_noContent_returnsTrue() {
        mockDeleteResponse(null, HttpStatus.NO_CONTENT);

        boolean result = client.cancelBooking("GEO-REF-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelBooking — 200 OK → true")
    void cancelBooking_ok_returnsTrue() {
        mockDeleteResponse(null, HttpStatus.OK);

        boolean result = client.cancelBooking("GEO-REF-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelBooking — statut inattendu → false")
    void cancelBooking_unexpectedStatus_returnsFalse() {
        mockDeleteResponse(null, HttpStatus.BAD_REQUEST);

        boolean result = client.cancelBooking("GEO-REF-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelBooking — exception réseau → false")
    void cancelBooking_networkException_returnsFalse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection reset"));

        boolean result = client.cancelBooking("GEO-REF-1");

        assertThat(result).isFalse();
    }
}
