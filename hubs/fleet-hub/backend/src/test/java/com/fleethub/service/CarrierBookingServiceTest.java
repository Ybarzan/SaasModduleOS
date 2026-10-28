package com.fleethub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.dto.BookingResponse;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import com.fleethub.service.carrier.CarrierAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * submitBooking() capture désormais, en plus de son comportement habituel,
 * l'ETA annoncée par le transporteur dans EtaReportedSource (Phase 2 J1-J2,
 * voir docs/03-plan-migration.md) -- seule la réservation CONFIRMED doit
 * produire une observation, jamais REJECTED/FAILED (rien à comparer).
 */
@DisplayName("CarrierBookingService — submitBooking")
class CarrierBookingServiceTest {

    CarrierBookingRequestRepository bookingRepo;
    CarrierRepository carrierRepo;
    ShipmentOrderRepository shipmentRepo;
    AppUserRepository userRepo;
    EtaReportedSourceRepository etaReportedSourceRepo;
    CarrierAdapter adapter;
    CarrierBookingService service;

    UUID bookingId;
    Company company;
    Carrier carrier;
    ShipmentOrder shipment;
    CarrierBookingRequest booking;

    @BeforeEach
    void setUp() {
        bookingRepo = mock(CarrierBookingRequestRepository.class);
        carrierRepo = mock(CarrierRepository.class);
        shipmentRepo = mock(ShipmentOrderRepository.class);
        userRepo = mock(AppUserRepository.class);
        etaReportedSourceRepo = mock(EtaReportedSourceRepository.class);
        adapter = mock(CarrierAdapter.class);
        when(adapter.supports("DHL")).thenReturn(true);

        service = new CarrierBookingService(
                bookingRepo, carrierRepo, shipmentRepo, userRepo,
                List.of(adapter), new ObjectMapper(), etaReportedSourceRepo);

        company = new Company();
        company.setId(7L);

        carrier = Carrier.builder().id(UUID.randomUUID()).company(company).name("DHL").code("DHL").build();

        ShippingRate rate = ShippingRate.builder().id(UUID.randomUUID()).carrier(carrier).transportMode("SEA").build();

        shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(company).carrier(carrier).shippingRate(rate)
                .shipperCountry("CHN").consigneeCountry("DEU")
                .build();

        bookingId = UUID.randomUUID();
        booking = CarrierBookingRequest.builder()
                .id(bookingId).company(company).carrier(carrier).shipmentOrder(shipment)
                .carrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING)
                .build();
        booking.setUser(new AppUser());

        when(bookingRepo.findByIdAndCompanyId(eq(bookingId), any())).thenReturn(Optional.of(booking));
        when(bookingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("réservation acceptée → EtaReportedSource enregistrée avec le lane/mode de l'expédition")
    void submitBooking_accepted_recordsEtaReportedSource() {
        BookingResponse response = new BookingResponse();
        response.setAccepted(true);
        response.setCarrierReference("REF-1");
        response.setEstimatedTransitDays(28);
        response.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(28).toLocalDate().toString());
        when(adapter.submitBooking(carrier, shipment, booking)).thenReturn(response);

        service.submitBooking(bookingId);

        ArgumentCaptor<EtaReportedSource> captor = ArgumentCaptor.forClass(EtaReportedSource.class);
        verify(etaReportedSourceRepo).save(captor.capture());
        EtaReportedSource saved = captor.getValue();

        assertThat(saved.getSource()).isEqualTo(EtaReportedSource.Source.CARRIER_BOOKING);
        assertThat(saved.getReportedDays()).isEqualTo(28);
        assertThat(saved.getReportedArrival()).isNotNull();
        assertThat(saved.getOriginCountry()).isEqualTo("CHN");
        assertThat(saved.getDestinationCountry()).isEqualTo("DEU");
        assertThat(saved.getTransportMode()).isEqualTo("SEA");
        assertThat(saved.getCarrier()).isEqualTo(carrier);
        assertThat(saved.getShipment()).isEqualTo(shipment);
    }

    @Test
    @DisplayName("réservation rejetée par le transporteur → aucune EtaReportedSource")
    void submitBooking_rejected_doesNotRecordEtaReportedSource() {
        BookingResponse response = new BookingResponse();
        response.setAccepted(false);
        response.setErrorMessage("Capacité indisponible");
        when(adapter.submitBooking(carrier, shipment, booking)).thenReturn(response);

        service.submitBooking(bookingId);

        verifyNoInteractions(etaReportedSourceRepo);
    }

    @Test
    @DisplayName("échec de l'appel transporteur (exception) → aucune EtaReportedSource")
    void submitBooking_adapterThrows_doesNotRecordEtaReportedSource() {
        when(adapter.submitBooking(carrier, shipment, booking)).thenThrow(new RuntimeException("timeout"));

        CarrierBookingRequest result = service.submitBooking(bookingId);

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.FAILED);
        verifyNoInteractions(etaReportedSourceRepo);
    }

    @Test
    @DisplayName("réservation acceptée sans date de livraison estimée → observation enregistrée sans reportedArrival")
    void submitBooking_acceptedWithoutDeliveryDate_recordsWithoutReportedArrival() {
        BookingResponse response = new BookingResponse();
        response.setAccepted(true);
        response.setEstimatedTransitDays(10);
        when(adapter.submitBooking(carrier, shipment, booking)).thenReturn(response);

        service.submitBooking(bookingId);

        ArgumentCaptor<EtaReportedSource> captor = ArgumentCaptor.forClass(EtaReportedSource.class);
        verify(etaReportedSourceRepo).save(captor.capture());
        assertThat(captor.getValue().getReportedArrival()).isNull();
        assertThat(captor.getValue().getReportedDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("échec de capture EtaReportedSource (ex. repo en panne) → la réservation reste CONFIRMED malgré tout")
    void submitBooking_etaReportedSourceSaveFails_bookingStillConfirmed() {
        BookingResponse response = new BookingResponse();
        response.setAccepted(true);
        response.setEstimatedTransitDays(15);
        when(adapter.submitBooking(carrier, shipment, booking)).thenReturn(response);
        when(etaReportedSourceRepo.save(any())).thenThrow(new RuntimeException("DB down"));

        CarrierBookingRequest result = service.submitBooking(bookingId);

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.CONFIRMED);
    }
}
