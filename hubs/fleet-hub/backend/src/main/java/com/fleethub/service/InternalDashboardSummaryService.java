package com.fleethub.service;

import com.fleethub.dto.InternalDashboardSummaryDto;
import com.fleethub.model.CarrierBookingRequest;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.CarrierBookingRequestRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Résumé compact pour le tableau de bord cross-hub de Praxio. Appelé via la
 * clé d'intégration interne existante (InternalIntegrationService) : le
 * companyId vient de la clé résolue, jamais d'un paramètre client.
 */
@Service
@RequiredArgsConstructor
public class InternalDashboardSummaryService {

    private static final List<ShipmentOrder.Status> ACTIVE_SHIPMENT_STATUSES =
            List.of(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.IN_TRANSIT);

    private static final List<CarrierBookingRequest.BookingStatus> OPEN_BOOKING_STATUSES =
            List.of(CarrierBookingRequest.BookingStatus.PENDING, CarrierBookingRequest.BookingStatus.SUBMITTED);

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final TruckRepository truckRepository;
    private final CarrierBookingRequestRepository carrierBookingRequestRepository;

    @Transactional(readOnly = true)
    public InternalDashboardSummaryDto getDashboardSummary(Long companyId) {
        long activeShipments = shipmentOrderRepository.countByCompanyIdAndStatusIn(companyId, ACTIVE_SHIPMENT_STATUSES);
        long availableTrucks = truckRepository.countByCompanyIdAndActiveTrue(companyId);
        long openCarrierBookings = carrierBookingRequestRepository.countByCompanyIdAndStatusIn(companyId, OPEN_BOOKING_STATUSES);

        return new InternalDashboardSummaryDto(activeShipments, availableTrucks, openCarrierBookings);
    }
}
