package com.incokalk.service.carrier.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CmaCgmBookingApiClient - Tests unitaires")
class CmaCgmBookingApiClientTest {

    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    CmaCgmBookingApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new CmaCgmBookingApiClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.cma-cgm.com/booking/v1");
        ReflectionTestUtils.setField(client, "apiKey", "cma-key");
    }

    private Carrier carrier() {
        Carrier c = new Carrier();
        c.setCode("CMACGM");
        return c;
    }

    private ShipmentOrder shipment() {
        ShipmentOrder s = new ShipmentOrder();
        s.setOrderNumber("SO-2001");
        s.setGoodsDescription("Electronics");
        s.setWeightKg(500.0);
        s.setVolumeM3(12.0);
        s.setDangerous(false);
        s.setShipperCountry("FR");
        s.setConsigneeCountry("US");
        s.setPackagesCount(3);
        return s;
    }

    private CarrierBookingRequest bookingRequest() {
        CarrierBookingRequest req = new CarrierBookingRequest();
        req.setServiceType("FCL_EXPRESS");
        req.setRequestedPickupDate(LocalDate.of(2026, 9, 1));
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
    @DisplayName("isConfigured - apiKey null -> false")
    void isConfigured_apiKeyNull_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", null);
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured - apiKey vide/blank -> false")
    void isConfigured_apiKeyBlank_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured - apiKey renseignee -> true")
    void isConfigured_apiKeySet_returnsTrue() {
        assertThat(client.isConfigured()).isTrue();
    }

    // ---------- submitBooking ----------

    @Test
    @DisplayName("submitBooking - non configure -> null, pas d'appel HTTP")
    void submitBooking_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNull();
        org.mockito.Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("submitBooking - succes avec toutes les valeurs de reponse presentes")
    void submitBooking_success_allFieldsPresent_mapsResponse() {
        String body = "{\"bookingRef\":\"CMA-REF-1\",\"containerNumber\":\"CMAU1234567\","
                + "\"totalAmount\":456.78,\"estimatedDeparture\":\"2026-08-20\","
                + "\"transitDays\":30,\"estimatedArrival\":\"2026-09-19\"}";
        mockExchange(HttpMethod.POST, body, HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("CMA-REF-1");
        assertThat(resp.getTrackingNumber()).isEqualTo("CMAU1234567");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(456.78));
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getEstimatedPickupDate()).isEqualTo("2026-08-20");
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(30);
        assertThat(resp.getEstimatedDeliveryDate()).isEqualTo("2026-09-19");
    }

    @Test
    @DisplayName("submitBooking - reponse minimale -> valeurs par defaut generees")
    void submitBooking_success_missingFields_usesDefaults() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("CMA-");
        assertThat(resp.getTrackingNumber()).startsWith("CMAU");
        assertThat(resp.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(resp.getEstimatedPickupDate())
                .isEqualTo(LocalDate.now().plusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(28);
        assertThat(resp.getEstimatedDeliveryDate())
                .isEqualTo(LocalDate.now().plusDays(32).format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Test
    @DisplayName("submitBooking - champs optionnels null -> valeurs par defaut dans le corps de la requete")
    void submitBooking_optionalRequestFieldsNull_usesDefaultsInBody() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.OK);

        ShipmentOrder shipment = shipment();
        shipment.setPackagesCount(null);
        CarrierBookingRequest req = bookingRequest();
        req.setServiceType(null);
        req.setRequestedPickupDate(null);

        client.submitBooking(carrier(), shipment, req);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentBody = (Map<String, Object>) captor.getValue().getBody();
        assertThat(sentBody.get("productCode")).isEqualTo("FCL");
        assertThat(sentBody.get("equipmentQuantity")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> transport = (Map<String, Object>) sentBody.get("transport");
        assertThat(transport.get("readyDate")).isEqualTo(LocalDate.now().plusDays(3).toString());
    }

    @Test
    @DisplayName("submitBooking - champs optionnels renseignes -> valeurs propagees dans le corps")
    void submitBooking_optionalRequestFieldsPresent_propagatedInBody() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.OK);

        ShipmentOrder shipment = shipment();
        shipment.setPackagesCount(7);
        CarrierBookingRequest req = bookingRequest();
        req.setServiceType("FCL_STANDARD");
        req.setRequestedPickupDate(LocalDate.of(2026, 10, 5));

        client.submitBooking(carrier(), shipment, req);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentBody = (Map<String, Object>) captor.getValue().getBody();
        assertThat(sentBody.get("productCode")).isEqualTo("FCL_STANDARD");
        assertThat(sentBody.get("equipmentQuantity")).isEqualTo(7);

        @SuppressWarnings("unchecked")
        Map<String, Object> transport = (Map<String, Object>) sentBody.get("transport");
        assertThat(transport.get("readyDate")).isEqualTo("2026-10-05");
    }

    @Test
    @DisplayName("submitBooking - statut non-OK -> null")
    void submitBooking_nonOkStatus_returnsNull() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.BAD_REQUEST);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking - statut OK mais corps null -> null")
    void submitBooking_okStatusNullBody_returnsNull() {
        mockExchange(HttpMethod.POST, null, HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking - exception reseau -> null")
    void submitBooking_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("submitBooking - JSON malforme -> null (parsing echoue)")
    void submitBooking_malformedJson_returnsNull() {
        mockExchange(HttpMethod.POST, "{not-valid-json!!", HttpStatus.OK);

        BookingResponse resp = client.submitBooking(carrier(), shipment(), bookingRequest());

        assertThat(resp).isNull();
    }

    // ---------- getStatus ----------

    @Test
    @DisplayName("getStatus - non configure -> null, pas d'appel HTTP")
    void getStatus_notConfigured_returnsNull() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNull();
        org.mockito.Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getStatus - succes avec champs presents")
    void getStatus_success_fieldsPresent_mapsResponse() {
        String body = "{\"status\":\"DELIVERED\",\"vesselPosition\":\"North Atlantic\",\"loadPort\":\"FRLEH\"}";
        mockExchange(HttpMethod.GET, body, HttpStatus.OK);

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNotNull();
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("CMA-REF-1");
        assertThat(resp.getAdditionalData())
                .containsEntry("currentStatus", "DELIVERED")
                .containsEntry("vesselPosition", "North Atlantic")
                .containsEntry("portOfLoading", "FRLEH");
    }

    @Test
    @DisplayName("getStatus - reponse minimale -> valeurs par defaut")
    void getStatus_success_missingFields_usesDefaults() {
        mockExchange(HttpMethod.GET, "{}", HttpStatus.OK);

        BookingResponse resp = client.getStatus("CMA-REF-2");

        assertThat(resp).isNotNull();
        assertThat(resp.getCarrierReference()).isEqualTo("CMA-REF-2");
        assertThat(resp.getAdditionalData())
                .containsEntry("currentStatus", "IN_TRANSIT")
                .containsEntry("vesselPosition", "")
                .containsEntry("portOfLoading", "");
    }

    @Test
    @DisplayName("getStatus - statut non-OK -> null")
    void getStatus_nonOkStatus_returnsNull() {
        mockExchange(HttpMethod.GET, "{}", HttpStatus.NOT_FOUND);

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus - statut OK mais corps null -> null")
    void getStatus_okStatusNullBody_returnsNull() {
        mockExchange(HttpMethod.GET, null, HttpStatus.OK);

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus - exception reseau -> null")
    void getStatus_networkException_returnsNull() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNull();
    }

    @Test
    @DisplayName("getStatus - JSON malforme -> null (parsing echoue)")
    void getStatus_malformedJson_returnsNull() {
        mockExchange(HttpMethod.GET, "{not-valid-json!!", HttpStatus.OK);

        BookingResponse resp = client.getStatus("CMA-REF-1");

        assertThat(resp).isNull();
    }

    // ---------- cancelBooking ----------

    @Test
    @DisplayName("cancelBooking - non configure -> false, pas d'appel HTTP")
    void cancelBooking_notConfigured_returnsFalse() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        boolean result = client.cancelBooking("CMA-REF-1");

        assertThat(result).isFalse();
        org.mockito.Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("cancelBooking - succes -> true")
    void cancelBooking_success_returnsTrue() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.OK);

        boolean result = client.cancelBooking("CMA-REF-1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("cancelBooking - statut non-OK -> false")
    void cancelBooking_nonOkStatus_returnsFalse() {
        mockExchange(HttpMethod.POST, "{}", HttpStatus.BAD_REQUEST);

        boolean result = client.cancelBooking("CMA-REF-1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelBooking - exception reseau -> false")
    void cancelBooking_networkException_returnsFalse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        boolean result = client.cancelBooking("CMA-REF-1");

        assertThat(result).isFalse();
    }
}
