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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("MscBookingApiClient — Tests unitaires")
class MscBookingApiClientTest {

    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    MscBookingApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new MscBookingApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.msc.com/booking/v1");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    @SuppressWarnings("unchecked")
    private void mockHttpResponse(HttpMethod method, String body, HttpStatus status) {
        ResponseEntity<String> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(method), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }

    private ShipmentOrder shipment() {
        return ShipmentOrder.builder()
                .orderNumber("ORD-123")
                .packagesCount(3)
                .goodsDescription("Machine parts")
                .weightKg(1200.0)
                .volumeM3(4.5)
                .isDangerous(false)
                .shipperCountry("FR")
                .consigneeCountry("US")
                .build();
    }

    private CarrierBookingRequest bookingRequest() {
        return CarrierBookingRequest.builder()
                .serviceType("FCL")
                .requestedPickupDate(LocalDate.now().plusDays(2))
                .build();
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

    // ---------- submitBooking — non configuré ----------

    @Test
    @DisplayName("submitBooking — non configuré (clé null) → null, pas d'appel HTTP")
    void submitBooking_notConfigured_apiKeyNull_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
        verify_neverExchange();
    }

    @Test
    @DisplayName("submitBooking — non configuré (clé vide) → null, pas d'appel HTTP")
    void submitBooking_notConfigured_apiKeyBlank_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
        verify_neverExchange();
    }

    // ---------- submitBooking — succès ----------

    @Test
    @DisplayName("submitBooking — succès, tous les champs présents dans la réponse")
    void submitBooking_success_allFieldsPresent() {
        String body = """
                {"bookingReference":"MSCU12345678","containerNumber":"CONT999",
                 "totalCost":1500.75,"estimatedDeparture":"2026-08-20",
                 "estimatedTransitDays":21,"estimatedArrival":"2026-09-10"}
                """;
        mockHttpResponse(HttpMethod.POST, body, HttpStatus.OK);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNotNull();
        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getCarrierReference()).isEqualTo("MSCU12345678");
        assertThat(response.getTrackingNumber()).isEqualTo("CONT999");
        assertThat(response.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(1500.75));
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getEstimatedPickupDate()).isEqualTo("2026-08-20");
        assertThat(response.getEstimatedTransitDays()).isEqualTo(21);
        assertThat(response.getEstimatedDeliveryDate()).isEqualTo("2026-09-10");
    }

    @Test
    @DisplayName("submitBooking — succès, réponse vide → valeurs par défaut")
    void submitBooking_success_emptyResponse_usesDefaults() {
        mockHttpResponse(HttpMethod.POST, "{}", HttpStatus.OK);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNotNull();
        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getCarrierReference()).startsWith("MSC-");
        assertThat(response.getTrackingNumber()).startsWith("MSCU");
        assertThat(response.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getEstimatedTransitDays()).isEqualTo(25);
        assertThat(response.getEstimatedPickupDate())
                .isEqualTo(LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertThat(response.getEstimatedDeliveryDate())
                .isEqualTo(LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Test
    @DisplayName("submitBooking — serviceType/packagesCount/pickupDate absents → valeurs par défaut dans le corps")
    void submitBooking_missingRequestFields_usesDefaultsInBody() {
        mockHttpResponse(HttpMethod.POST, "{}", HttpStatus.OK);

        ShipmentOrder shipment = ShipmentOrder.builder()
                .orderNumber("ORD-999")
                .packagesCount(null)
                .goodsDescription("Misc")
                .weightKg(500.0)
                .volumeM3(1.0)
                .isDangerous(true)
                .shipperCountry("DE")
                .consigneeCountry("CN")
                .build();

        CarrierBookingRequest request = CarrierBookingRequest.builder()
                .serviceType(null)
                .requestedPickupDate(null)
                .build();

        BookingResponse response = client.submitBooking(carrier(), shipment, request);

        assertThat(response).isNotNull();

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> reqBody = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat(reqBody.get("serviceType")).isEqualTo("FCL");
        assertThat(reqBody.get("containerCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> route = (java.util.Map<String, Object>) reqBody.get("route");
        assertThat(route.get("pickupDate")).isEqualTo(LocalDate.now().plusDays(3).toString());
    }

    // ---------- submitBooking — erreurs ----------

    @Test
    @DisplayName("submitBooking — statut non-OK → null")
    void submitBooking_responseNotOk_returnsNull() {
        mockHttpResponse(HttpMethod.POST, "{}", HttpStatus.ACCEPTED);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("submitBooking — corps de réponse null malgré statut OK → null")
    void submitBooking_responseBodyNull_returnsNull() {
        mockHttpResponse(HttpMethod.POST, null, HttpStatus.OK);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("submitBooking — JSON malformé → parsing échoue silencieusement, null")
    void submitBooking_malformedJson_returnsNull() {
        mockHttpResponse(HttpMethod.POST, "{not-valid-json!!", HttpStatus.OK);

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("submitBooking — exception réseau → null")
    void submitBooking_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse response = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(response).isNull();
    }

    // ---------- getStatus — non configuré ----------

    @Test
    @DisplayName("getStatus — non configuré → null, pas d'appel HTTP")
    void getStatus_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        BookingResponse response = client.getStatus("MSC-REF-1");

        assertThat(response).isNull();
        verify_neverExchange();
    }

    // ---------- getStatus — succès ----------

    @Test
    @DisplayName("getStatus — succès, tous les champs présents")
    void getStatus_success_allFieldsPresent() {
        String body = """
                {"status":"IN_TRANSIT","vesselPosition":"12.34,56.78","portOfLoading":"Shanghai"}
                """;
        mockHttpResponse(HttpMethod.GET, body, HttpStatus.OK);

        BookingResponse response = client.getStatus("MSC-REF-1");

        assertThat(response).isNotNull();
        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getCarrierReference()).isEqualTo("MSC-REF-1");
        assertThat(response.getAdditionalData().get("currentStatus")).isEqualTo("IN_TRANSIT");
        assertThat(response.getAdditionalData().get("vesselPosition")).isEqualTo("12.34,56.78");
        assertThat(response.getAdditionalData().get("portOfLoading")).isEqualTo("Shanghai");
    }

    @Test
    @DisplayName("getStatus — succès, réponse vide → valeurs par défaut")
    void getStatus_success_emptyResponse_usesDefaults() {
        mockHttpResponse(HttpMethod.GET, "{}", HttpStatus.OK);

        BookingResponse response = client.getStatus("MSC-REF-2");

        assertThat(response).isNotNull();
        assertThat(response.getAdditionalData().get("currentStatus")).isEqualTo("IN_TRANSIT");
        assertThat(response.getAdditionalData().get("vesselPosition")).isEqualTo("");
        assertThat(response.getAdditionalData().get("portOfLoading")).isEqualTo("");
    }

    // ---------- getStatus — erreurs ----------

    @Test
    @DisplayName("getStatus — statut non-OK → null")
    void getStatus_responseNotOk_returnsNull() {
        mockHttpResponse(HttpMethod.GET, "{}", HttpStatus.NOT_FOUND);

        BookingResponse response = client.getStatus("MSC-REF-3");

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("getStatus — corps de réponse null malgré statut OK → null")
    void getStatus_responseBodyNull_returnsNull() {
        mockHttpResponse(HttpMethod.GET, null, HttpStatus.OK);

        BookingResponse response = client.getStatus("MSC-REF-4");

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("getStatus — JSON malformé → parsing échoue silencieusement, null")
    void getStatus_malformedJson_returnsNull() {
        mockHttpResponse(HttpMethod.GET, "{not-valid-json!!", HttpStatus.OK);

        BookingResponse response = client.getStatus("MSC-REF-5");

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("getStatus — exception réseau → null")
    void getStatus_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("boom"));

        BookingResponse response = client.getStatus("MSC-REF-6");

        assertThat(response).isNull();
    }

    // ---------- cancelBooking ----------

    @Test
    @DisplayName("cancelBooking — non configuré → false, pas d'appel HTTP")
    void cancelBooking_notConfigured_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        boolean result = client.cancelBooking("MSC-REF-1");

        assertThat(result).isFalse();
        verify_neverExchange();
    }

    @Test
    @DisplayName("cancelBooking — statut OK → true")
    void cancelBooking_statusOk_returnsTrue() {
        mockHttpResponse(HttpMethod.POST, "", HttpStatus.OK);

        boolean result = client.cancelBooking("MSC-REF-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelBooking — statut non-OK → false")
    void cancelBooking_statusNotOk_returnsFalse() {
        mockHttpResponse(HttpMethod.POST, "", HttpStatus.BAD_REQUEST);

        boolean result = client.cancelBooking("MSC-REF-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelBooking — exception réseau → false")
    void cancelBooking_networkException_returnsFalse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection reset"));

        boolean result = client.cancelBooking("MSC-REF-1");

        assertThat(result).isFalse();
    }

    // ---------- helpers ----------

    private Carrier carrier() {
        return Carrier.builder()
                .name("MSC")
                .code("MSC")
                .transportModes("SEA")
                .build();
    }

    private void verify_neverExchange() {
        org.mockito.Mockito.verify(restTemplate, never())
                .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }
}
