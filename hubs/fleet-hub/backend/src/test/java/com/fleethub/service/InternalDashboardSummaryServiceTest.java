package com.fleethub.service;

import com.fleethub.dto.InternalDashboardSummaryDto;
import com.fleethub.model.CarrierBookingRequest;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.CarrierBookingRequestRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("InternalDashboardSummaryService — Tests unitaires")
class InternalDashboardSummaryServiceTest {

    ShipmentOrderRepository shipmentOrderRepo;
    TruckRepository truckRepo;
    CarrierBookingRequestRepository carrierBookingRequestRepo;
    InternalDashboardSummaryService service;
    Long companyId;

    @BeforeEach
    void setUp() {
        shipmentOrderRepo = mock(ShipmentOrderRepository.class);
        truckRepo = mock(TruckRepository.class);
        carrierBookingRequestRepo = mock(CarrierBookingRequestRepository.class);
        service = new InternalDashboardSummaryService(shipmentOrderRepo, truckRepo, carrierBookingRequestRepo);
        companyId = 42L;
    }

    @Test
    @DisplayName("getDashboardSummary → agrège expéditions actives/camions disponibles/réservations ouvertes")
    void getDashboardSummary_aggregatesKpis() {
        when(shipmentOrderRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(4L);
        when(truckRepo.countByCompanyIdAndActiveTrue(companyId)).thenReturn(7L);
        when(carrierBookingRequestRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(2L);

        InternalDashboardSummaryDto dto = service.getDashboardSummary(companyId);

        assertThat(dto.activeShipments()).isEqualTo(4L);
        assertThat(dto.availableTrucks()).isEqualTo(7L);
        assertThat(dto.openCarrierBookings()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getDashboardSummary → ne compte que BOOKED/IN_TRANSIT comme expéditions actives")
    void getDashboardSummary_usesActiveShipmentStatuses() {
        when(shipmentOrderRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(0L);

        service.getDashboardSummary(companyId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShipmentOrder.Status>> captor = ArgumentCaptor.forClass(List.class);
        verify(shipmentOrderRepo).countByCompanyIdAndStatusIn(eq(companyId), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.IN_TRANSIT);
    }

    @Test
    @DisplayName("getDashboardSummary → ne compte que PENDING/SUBMITTED comme réservations ouvertes")
    void getDashboardSummary_usesOpenBookingStatuses() {
        when(carrierBookingRequestRepo.countByCompanyIdAndStatusIn(eq(companyId), any())).thenReturn(0L);

        service.getDashboardSummary(companyId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CarrierBookingRequest.BookingStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(carrierBookingRequestRepo).countByCompanyIdAndStatusIn(eq(companyId), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                CarrierBookingRequest.BookingStatus.PENDING, CarrierBookingRequest.BookingStatus.SUBMITTED);
    }
}
