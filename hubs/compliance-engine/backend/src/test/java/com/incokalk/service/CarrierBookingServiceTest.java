package com.incokalk.service;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.dto.shipment.CarrierBookingRequestDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.repository.CarrierBookingRequestRepository;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.carrier.CarrierAdapter;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.stubbing.Answer;

class CarrierBookingServiceTest {

    @Mock private CarrierBookingRequestRepository bookingRepo;
    @Mock private CarrierRepository carrierRepo;
    @Mock private ShipmentOrderRepository shipmentRepo;
    @Mock private UserRepository userRepo;
    @Mock private CarrierAdapter dhlAdapter;
    @Mock private CarrierAdapter mscAdapter;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private CarrierBookingService service;

    private UUID companyId = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();
    private Company company;
    private User user;
    private Carrier carrier;
    private ShipmentOrder shipment;
    private CarrierBookingRequestDTO dto;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new CarrierBookingService(bookingRepo, carrierRepo, shipmentRepo, userRepo, List.of(dhlAdapter, mscAdapter), objectMapper);
        TenantContext.set(companyId);

        company = new Company();
        company.setId(companyId);

        user = new User();
        user.setId(userId);
        user.setCompany(company);

        carrier = new Carrier();
        carrier.setId(UUID.randomUUID());
        carrier.setCompany(company);
        carrier.setCode("DHL");
        carrier.setTransportModes("ROAD,AIR");

        shipment = new ShipmentOrder();
        shipment.setId(UUID.randomUUID());
        shipment.setCompany(company);
        shipment.setUser(user);
        shipment.setWeightKg(50.0);
        shipment.setVolumeM3(2.5);

        dto = new CarrierBookingRequestDTO();
        dto.setShipmentOrderId(shipment.getId());
        dto.setCarrierId(carrier.getId());
        dto.setServiceType("EXPRESS");
        dto.setRequestedPickupDate(LocalDate.now().plusDays(2));

        lenient().when(carrierRepo.findByIdAndCompanyId(eq(carrier.getId()), eq(companyId)))
            .thenReturn(Optional.of(carrier));
        lenient().when(shipmentRepo.findByIdAndCompanyId(eq(shipment.getId()), eq(companyId)))
            .thenReturn(Optional.of(shipment));
        lenient().when(userRepo.findById(eq(userId))).thenReturn(Optional.of(user));
        lenient().when(objectMapper.writeValueAsString(any()))
            .thenAnswer(invocation -> {
                try {
                    return "{}";
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void createBooking_success() {
        CarrierBookingRequest saved = new CarrierBookingRequest();
        saved.setId(UUID.randomUUID());
        saved.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);
        when(bookingRepo.save(any())).thenReturn(saved);

        CarrierBookingRequest result = service.createBooking(dto, userId);

        assertThat(result).isNotNull();
        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.PENDING);
        verify(bookingRepo).save(any());
    }

    @Test
    void createBooking_carrierNotFound() {
        dto.setCarrierId(UUID.randomUUID());
        when(carrierRepo.findByIdAndCompanyId(any(), eq(companyId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(dto, userId))
            .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
    }

    @Test
    void createBooking_shipmentNotFound() {
        dto.setShipmentOrderId(UUID.randomUUID());
        when(shipmentRepo.findByIdAndCompanyId(any(), eq(companyId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(dto, userId))
            .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
    }

    @Test
    void submitBooking_success_confirmed() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setCarrierReference("DHL-ABC123");
        resp.setTrackingNumber("DHL999");
        resp.setQuotedCost(BigDecimal.valueOf(750));
        resp.setCurrency("EUR");
        resp.setEstimatedPickupDate(LocalDate.now().plusDays(2).toString());
        resp.setEstimatedDeliveryDate(LocalDate.now().plusDays(7).toString());
        resp.setEstimatedTransitDays(5);
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.submitBooking(any(), any(), any())).thenReturn(resp);
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.submitBooking(booking.getId());

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.CONFIRMED);
        assertThat(result.getCarrierReference()).isEqualTo("DHL-ABC123");
        assertThat(result.getCarrierTrackingNumber()).isEqualTo("DHL999");
        verify(dhlAdapter).submitBooking(any(), any(), any());
    }

    @Test
    void submitBooking_simulated_propagatesFlagToBooking() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference("DHL-SIM123");
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.submitBooking(any(), any(), any())).thenReturn(resp);
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.submitBooking(booking.getId());

        assertThat(result.isSimulated()).isTrue();
    }

    @Test
    void submitBooking_realResponse_notSimulated() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setCarrierReference("DHL-REAL456");
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.submitBooking(any(), any(), any())).thenReturn(resp);
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.submitBooking(booking.getId());

        assertThat(result.isSimulated()).isFalse();
    }

    @Test
    void submitBooking_rejected() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(false);
        resp.setErrorMessage("Capacity full");
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.submitBooking(any(), any(), any())).thenReturn(resp);
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.submitBooking(booking.getId());

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.REJECTED);
        assertThat(result.getErrorMessage()).isEqualTo("Capacity full");
    }

    @Test
    void submitBooking_adapterException_setsFailed() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.submitBooking(any(), any(), any())).thenThrow(new RuntimeException("Network error"));
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.submitBooking(booking.getId());

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Network error");
    }

    @Test
    void submitBooking_notPending_throws() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CONFIRMED);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.submitBooking(booking.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }

    @Test
    void submitBooking_bookingNotFound() {
        when(bookingRepo.findByIdAndCompanyId(any(), eq(companyId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitBooking(UUID.randomUUID()))
            .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
    }

    @Test
    void cancelBooking_success() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCarrier(carrier);
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CONFIRMED);
        booking.setCarrierReference("DHL-ABC123");

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));
        when(dhlAdapter.supports("DHL")).thenReturn(true);
        when(dhlAdapter.cancelBooking("DHL-ABC123")).thenReturn(true);
        when(bookingRepo.save(any())).thenReturn(booking);

        CarrierBookingRequest result = service.cancelBooking(booking.getId());

        assertThat(result.getCarrierBookingStatus()).isEqualTo(CarrierBookingRequest.BookingStatus.CANCELLED);
        verify(dhlAdapter).cancelBooking("DHL-ABC123");
    }

    @Test
    void cancelBooking_completed_throws() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.COMPLETED);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(booking.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel");
    }

    @Test
    void cancelBooking_alreadyCancelled_throws() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCompany(company);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CANCELLED);

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(booking.getId()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getBooking_success() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));

        CarrierBookingRequest result = service.getBooking(booking.getId());
        assertThat(result).isNotNull();
    }

    @Test
    void getBooking_notFound() {
        when(bookingRepo.findByIdAndCompanyId(any(), eq(companyId))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getBooking(UUID.randomUUID()))
            .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
    }

    @Test
    void listBookings_delegatesToRepo() {
        when(bookingRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());
        assertThat(service.listBookings()).isEmpty();
        verify(bookingRepo).findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Test
    void listByShipment_delegatesToRepo() {
        UUID shipmentId = UUID.randomUUID();
        when(bookingRepo.findByShipmentOrderIdAndCompanyId(shipmentId, companyId)).thenReturn(List.of());
        assertThat(service.listByShipment(shipmentId)).isEmpty();
    }

    @Test
    void listByCarrier_delegatesToRepo() {
        UUID carrierId = UUID.randomUUID();
        when(bookingRepo.findByCarrierIdAndCompanyId(carrierId, companyId)).thenReturn(List.of());
        assertThat(service.listByCarrier(carrierId)).isEmpty();
    }

    @Test
    void getStats_returnsAllStatuses() {
        when(bookingRepo.countByCompanyId(companyId)).thenReturn(10L);
        when(bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.PENDING)).thenReturn(2L);
        when(bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.CONFIRMED)).thenReturn(3L);
        when(bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.COMPLETED)).thenReturn(4L);
        when(bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.FAILED)).thenReturn(1L);
        when(bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.CANCELLED)).thenReturn(0L);

        Map<String, Long> stats = service.getStats();

        assertThat(stats.get("total")).isEqualTo(10L);
        assertThat(stats.get("pending")).isEqualTo(2L);
        assertThat(stats.get("confirmed")).isEqualTo(3L);
        assertThat(stats.get("completed")).isEqualTo(4L);
        assertThat(stats.get("failed")).isEqualTo(1L);
        assertThat(stats.get("cancelled")).isEqualTo(0L);
    }

    @Test
    void findAdapter_noAdapter_throws() {
        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setCarrier(carrier);
        booking.setCompany(company);
        booking.setShipmentOrder(shipment);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);
        booking.setId(UUID.randomUUID());

        when(bookingRepo.findByIdAndCompanyId(eq(booking.getId()), eq(companyId)))
            .thenReturn(Optional.of(booking));
        when(dhlAdapter.supports("DHL")).thenReturn(false);
        when(mscAdapter.supports("DHL")).thenReturn(false);

        assertThatThrownBy(() -> service.submitBooking(booking.getId()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
