package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.CmaCgmBookingApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CmaCgmAdapter — Tests unitaires")
class CmaCgmAdapterTest {

    CmaCgmBookingApiClient apiClient;
    CmaCgmAdapter adapter;

    @BeforeEach
    void setUp() {
        apiClient = mock(CmaCgmBookingApiClient.class);
        adapter = new CmaCgmAdapter(apiClient);
    }

    private ShipmentOrder shipmentOrder(Double volumeM3, boolean dangerous) {
        return ShipmentOrder.builder()
                .orderNumber("ORD-1")
                .volumeM3(volumeM3)
                .isDangerous(dangerous)
                .build();
    }

    private CarrierBookingRequest bookingRequest(String serviceType, LocalDate pickupDate) {
        return CarrierBookingRequest.builder()
                .serviceType(serviceType)
                .requestedPickupDate(pickupDate)
                .build();
    }

    // ---------- getCarrierCode ----------

    @Test
    @DisplayName("getCarrierCode → CMA_CGM")
    void getCarrierCode_returnsCmaCgm() {
        assertThat(adapter.getCarrierCode()).isEqualTo("CMA_CGM");
    }

    // ---------- supports ----------

    @Test
    @DisplayName("supports — CMA_CGM (exact) → true")
    void supports_cmaCgmExact_true() {
        assertThat(adapter.supports("CMA_CGM")).isTrue();
    }

    @Test
    @DisplayName("supports — cma_cgm (insensible à la casse) → true")
    void supports_cmaCgmLowerCase_true() {
        assertThat(adapter.supports("cma_cgm")).isTrue();
    }

    @Test
    @DisplayName("supports — CMA (deuxième condition) → true")
    void supports_cma_true() {
        assertThat(adapter.supports("CMA")).isTrue();
    }

    @Test
    @DisplayName("supports — cma (insensible à la casse) → true")
    void supports_cmaLowerCase_true() {
        assertThat(adapter.supports("cma")).isTrue();
    }

    @Test
    @DisplayName("supports — CMACGM (troisième condition) → true")
    void supports_cmacgm_true() {
        assertThat(adapter.supports("CMACGM")).isTrue();
    }

    @Test
    @DisplayName("supports — cmacgm (insensible à la casse) → true")
    void supports_cmacgmLowerCase_true() {
        assertThat(adapter.supports("cmacgm")).isTrue();
    }

    @Test
    @DisplayName("supports — code inconnu → false")
    void supports_unknownCode_false() {
        assertThat(adapter.supports("MAERSK")).isFalse();
    }

    @Test
    @DisplayName("supports — code null → false")
    void supports_null_false() {
        assertThat(adapter.supports(null)).isFalse();
    }

    // ---------- submitBooking — délégation à l'API client ----------

    @Test
    @DisplayName("submitBooking — apiClient retourne une réponse → réponse retournée telle quelle")
    void submitBooking_apiClientReturnsResponse_returnsApiResponse() {
        BookingResponse apiResponse = new BookingResponse();
        apiResponse.setAccepted(true);
        apiResponse.setCarrierReference("API-REF-123");
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(apiResponse);

        Carrier carrier = Carrier.builder().name("CMA CGM").code("CMA_CGM").build();
        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("FCL", null);

        BookingResponse result = adapter.submitBooking(carrier, shipment, request);

        assertThat(result).isSameAs(apiResponse);
        assertThat(result.getCarrierReference()).isEqualTo("API-REF-123");
        verify(apiClient, times(1)).submitBooking(carrier, shipment, request);
    }

    @Test
    @DisplayName("submitBooking — apiClient retourne null → repli sur la simulation")
    void submitBooking_apiClientReturnsNull_fallsBackToSimulation() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        Carrier carrier = Carrier.builder().name("CMA CGM").code("CMA_CGM").build();
        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("FCL", null);

        BookingResponse result = adapter.submitBooking(carrier, shipment, request);

        assertThat(result).isNotNull();
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getCarrierReference()).startsWith("CMA-");
        assertThat(result.getAdditionalData()).containsEntry("source", "simulation");
    }

    // ---------- simulateBooking (via submitBooking, apiClient=null) — branches serviceType ----------

    @Test
    @DisplayName("simulateBooking — FCL, volume+dangereux+date fournis → coût FCL+volume+DG, transit 30j, SHANGHAI, DRY_40HC")
    void simulateBooking_fcl_withVolumeDangerousAndPickupDate() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        LocalDate pickupDate = LocalDate.of(2026, 9, 1);
        ShipmentOrder shipment = shipmentOrder(10.0, true);
        CarrierBookingRequest request = bookingRequest("FCL", pickupDate);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        BigDecimal expectedCost = BigDecimal.valueOf(2600.00)
                .add(BigDecimal.valueOf(10.0 * 55))
                .add(BigDecimal.valueOf(400.00));
        assertThat(result.getQuotedCost()).isEqualByComparingTo(expectedCost);
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getEstimatedTransitDays()).isEqualTo(30);

        LocalDate expectedDeparture = pickupDate.plusDays(3);
        LocalDate expectedArrival = expectedDeparture.plusDays(30);
        assertThat(result.getEstimatedPickupDate())
                .isEqualTo(expectedDeparture.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE));
        assertThat(result.getEstimatedDeliveryDate())
                .isEqualTo(expectedArrival.format(DateTimeFormatter.ISO_LOCAL_DATE));

        assertThat(result.getAdditionalData())
                .containsEntry("serviceType", "FCL")
                .containsEntry("portOfDischarge", "SHANGHAI")
                .containsEntry("containerType", "DRY_40HC");
    }

    @Test
    @DisplayName("simulateBooking — LCL, sans volume/dangereux/date → coût LCL seul, transit 22j, MARSEILLE, DRY_20GP")
    void simulateBooking_lcl_noVolumeNoDangerousNoPickupDate() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("LCL", null);

        LocalDate before = LocalDate.now();
        BookingResponse result = adapter.submitBooking(null, shipment, request);
        LocalDate after = LocalDate.now();

        assertThat(result.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(950.00));
        assertThat(result.getEstimatedTransitDays()).isEqualTo(22);
        assertThat(result.getAdditionalData())
                .containsEntry("serviceType", "LCL")
                .containsEntry("portOfDischarge", "MARSEILLE")
                .containsEntry("containerType", "DRY_20GP");

        // departure should be "now" (+/- a day for test execution boundary) plus 5 days
        LocalDate departureLowerBound = before.plusDays(5);
        LocalDate departureUpperBound = after.plusDays(5);
        String pickup = result.getEstimatedPickupDate();
        assertThat(pickup).isNotNull();
        LocalDate actualDeparture = LocalDate.parse(pickup).plusDays(2);
        assertThat(actualDeparture).isBetween(departureLowerBound, departureUpperBound);
    }

    @Test
    @DisplayName("simulateBooking — REEFER, avec volume, sans dangereux → coût REEFER+volume, transit 20j, REEFER_40HC")
    void simulateBooking_reefer_withVolume() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        LocalDate pickupDate = LocalDate.of(2026, 10, 1);
        ShipmentOrder shipment = shipmentOrder(5.0, false);
        CarrierBookingRequest request = bookingRequest("REEFER", pickupDate);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        BigDecimal expectedCost = BigDecimal.valueOf(3800.00).add(BigDecimal.valueOf(5.0 * 90));
        assertThat(result.getQuotedCost()).isEqualByComparingTo(expectedCost);
        assertThat(result.getEstimatedTransitDays()).isEqualTo(20);
        assertThat(result.getAdditionalData())
                .containsEntry("portOfDischarge", "MARSEILLE")
                .containsEntry("containerType", "REEFER_40HC");
    }

    @Test
    @DisplayName("simulateBooking — serviceType null → défaut FCL")
    void simulateBooking_nullServiceType_defaultsToFcl() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest(null, null);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        assertThat(result.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(2600.00));
        assertThat(result.getEstimatedTransitDays()).isEqualTo(30);
        assertThat(result.getAdditionalData()).containsEntry("serviceType", "FCL");
    }

    @Test
    @DisplayName("simulateBooking — serviceType inconnu → coût de base par défaut, transit 28j, MARSEILLE, DRY_20GP")
    void simulateBooking_unknownServiceType_usesDefaults() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("STANDARD", null);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        assertThat(result.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(1900.00));
        assertThat(result.getEstimatedTransitDays()).isEqualTo(28);
        assertThat(result.getAdditionalData())
                .containsEntry("serviceType", "STANDARD")
                .containsEntry("portOfDischarge", "MARSEILLE")
                .containsEntry("containerType", "DRY_20GP");
    }

    @Test
    @DisplayName("simulateBooking — serviceType 'FULL CONTAINER' (contient FULL) → traité comme FCL")
    void simulateBooking_fullServiceType_treatedAsFcl() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("full container", null);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        assertThat(result.getQuotedCost()).isEqualByComparingTo(BigDecimal.valueOf(2600.00));
        assertThat(result.getAdditionalData()).containsEntry("serviceType", "FULL CONTAINER");
    }

    @Test
    @DisplayName("simulateBooking — référence CMA- et numéro BL CMAU générés")
    void simulateBooking_generatesReferenceAndTrackingNumber() {
        when(apiClient.submitBooking(any(), any(), any())).thenReturn(null);

        ShipmentOrder shipment = shipmentOrder(null, false);
        CarrierBookingRequest request = bookingRequest("FCL", null);

        BookingResponse result = adapter.submitBooking(null, shipment, request);

        assertThat(result.getCarrierReference()).startsWith("CMA-");
        assertThat(result.getTrackingNumber()).startsWith("CMAU");
        assertThat(result.getAdditionalData()).containsEntry("vesselName", "CMA CGM JACQUES SAADE");
    }

    // ---------- getBookingStatus ----------

    @Test
    @DisplayName("getBookingStatus — apiClient retourne une réponse → réponse retournée telle quelle")
    void getBookingStatus_apiClientReturnsResponse_returnsApiResponse() {
        BookingResponse apiResponse = new BookingResponse();
        apiResponse.setAccepted(true);
        apiResponse.setCarrierReference("REF-999");
        when(apiClient.getStatus("REF-999")).thenReturn(apiResponse);

        BookingResponse result = adapter.getBookingStatus("REF-999");

        assertThat(result).isSameAs(apiResponse);
        verify(apiClient, times(1)).getStatus("REF-999");
    }

    @Test
    @DisplayName("getBookingStatus — apiClient retourne null → statut par défaut IN_TRANSIT")
    void getBookingStatus_apiClientReturnsNull_returnsDefaultStatus() {
        when(apiClient.getStatus("REF-000")).thenReturn(null);

        BookingResponse result = adapter.getBookingStatus("REF-000");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getCarrierReference()).isEqualTo("REF-000");
        assertThat(result.getAdditionalData())
                .containsEntry("currentStatus", "IN_TRANSIT")
                .containsEntry("vesselPosition", "36.8N 10.2E");
    }

    // ---------- cancelBooking ----------

    @Test
    @DisplayName("cancelBooking — apiClient configuré et annulation réussie → true")
    void cancelBooking_configuredAndSuccessful_true() {
        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.cancelBooking("REF-1")).thenReturn(true);

        boolean result = adapter.cancelBooking("REF-1");

        assertThat(result).isTrue();
        verify(apiClient, times(1)).cancelBooking("REF-1");
    }

    @Test
    @DisplayName("cancelBooking — apiClient configuré mais annulation échoue → false")
    void cancelBooking_configuredButFails_false() {
        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.cancelBooking("REF-2")).thenReturn(false);

        boolean result = adapter.cancelBooking("REF-2");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cancelBooking — apiClient non configuré → true sans appel à l'API")
    void cancelBooking_notConfigured_returnsTrueWithoutApiCall() {
        when(apiClient.isConfigured()).thenReturn(false);

        boolean result = adapter.cancelBooking("REF-3");

        assertThat(result).isTrue();
        verify(apiClient, never()).cancelBooking(anyString());
    }
}
