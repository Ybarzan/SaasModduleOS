package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.BookingResponse;
import com.fleethub.dto.CarrierBookingRequestDTO;
import com.fleethub.model.AppUser;
import com.fleethub.model.Carrier;
import com.fleethub.model.CarrierBookingRequest;
import com.fleethub.model.EtaReportedSource;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CarrierBookingRequestRepository;
import com.fleethub.repository.CarrierRepository;
import com.fleethub.repository.EtaReportedSourceRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.carrier.CarrierAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CarrierBookingService {

    private static final Logger log = LoggerFactory.getLogger(CarrierBookingService.class);

    private final CarrierBookingRequestRepository bookingRepo;
    private final CarrierRepository carrierRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final AppUserRepository userRepo;
    private final List<CarrierAdapter> adapters;
    private final ObjectMapper objectMapper;
    private final EtaReportedSourceRepository etaReportedSourceRepo;
    private final ShipmentEventRecorder eventRecorder;

    public CarrierBookingService(
            CarrierBookingRequestRepository bookingRepo,
            CarrierRepository carrierRepo,
            ShipmentOrderRepository shipmentRepo,
            AppUserRepository userRepo,
            List<CarrierAdapter> adapters,
            ObjectMapper objectMapper,
            EtaReportedSourceRepository etaReportedSourceRepo,
            ShipmentEventRecorder eventRecorder) {
        this.bookingRepo = bookingRepo;
        this.carrierRepo = carrierRepo;
        this.shipmentRepo = shipmentRepo;
        this.userRepo = userRepo;
        this.adapters = adapters;
        this.objectMapper = objectMapper;
        this.etaReportedSourceRepo = etaReportedSourceRepo;
        this.eventRecorder = eventRecorder;
    }

    public CarrierAdapter findAdapter(String carrierCode) {
        return adapters.stream()
            .filter(a -> a.supports(carrierCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No adapter for carrier: " + carrierCode));
    }

    @Transactional
    public CarrierBookingRequest createBooking(CarrierBookingRequestDTO dto, Long userId) {
        Long companyId = TenantContext.companyId();

        Carrier carrier = carrierRepo.findByIdAndCompanyId(dto.getCarrierId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Carrier not found: " + dto.getCarrierId()));

        ShipmentOrder shipment = shipmentRepo.findByIdAndCompanyId(dto.getShipmentOrderId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + dto.getShipmentOrderId()));

        AppUser user = userId != null ? userRepo.findById(userId).orElse(null) : null;

        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setCompany(carrier.getCompany());
        booking.setUser(user != null ? user : shipment.getUser());
        booking.setShipmentOrder(shipment);
        booking.setCarrier(carrier);
        booking.setServiceType(dto.getServiceType());
        booking.setSpecialInstructions(dto.getSpecialInstructions());
        booking.setRequestedPickupDate(dto.getRequestedPickupDate());
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);

        return bookingRepo.save(booking);
    }

    @Transactional
    public CarrierBookingRequest submitBooking(UUID bookingId) {
        Long companyId = TenantContext.companyId();

        CarrierBookingRequest booking = bookingRepo.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getCarrierBookingStatus() != CarrierBookingRequest.BookingStatus.PENDING) {
            throw new IllegalStateException("Booking can only be submitted from PENDING status, current: " + booking.getCarrierBookingStatus());
        }

        CarrierAdapter adapter = findAdapter(booking.getCarrier().getCode());

        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.SUBMITTED);
        bookingRepo.save(booking);

        try {
            BookingResponse response = adapter.submitBooking(
                booking.getCarrier(),
                booking.getShipmentOrder(),
                booking
            );

            booking.setSimulated(response.isSimulated());

            if (response.isAccepted()) {
                booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CONFIRMED);
                booking.setCarrierReference(response.getCarrierReference());
                booking.setCarrierTrackingNumber(response.getTrackingNumber());
                booking.setQuotedCost(response.getQuotedCost());
                booking.setQuotedCostCurrency(response.getCurrency());
                booking.setEstimatedTransitDays(response.getEstimatedTransitDays());

                if (response.getEstimatedPickupDate() != null) {
                    booking.setEstimatedPickupDate(LocalDate.parse(response.getEstimatedPickupDate()));
                }
                if (response.getEstimatedDeliveryDate() != null) {
                    booking.setEstimatedDeliveryDate(LocalDate.parse(response.getEstimatedDeliveryDate()));
                }

                booking.setCarrierResponseJson(objectMapper.writeValueAsString(response));
                recordEtaReportedSource(booking);
            } else {
                booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.REJECTED);
                booking.setErrorMessage(response.getErrorMessage());
                booking.setCarrierResponseJson(objectMapper.writeValueAsString(response));
            }
        } catch (Exception e) {
            log.error("Carrier booking failed for booking {}: {}", bookingId, e.getMessage());
            booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.FAILED);
            booking.setErrorMessage(e.getMessage());
        }

        return bookingRepo.save(booking);
    }

    /** Capture l'ETA annoncée par le transporteur au moment de la réservation,
     *  figée telle qu'émise -- voir EtaReportedSource et docs/03-plan-migration.md
     *  Phase 2 J1-J2. Ne bloque jamais la réservation elle-même : un échec ici
     *  (lane/mode absents, par ex. tarif non renseigné) est journalisé et ignoré. */
    private void recordEtaReportedSource(CarrierBookingRequest booking) {
        try {
            ShipmentOrder shipment = booking.getShipmentOrder();
            LocalDateTime reportedArrival = booking.getEstimatedDeliveryDate() != null
                    ? booking.getEstimatedDeliveryDate().atStartOfDay()
                    : null;

            EtaReportedSource source = EtaReportedSource.builder()
                    .company(booking.getCompany())
                    .shipment(shipment)
                    .carrier(booking.getCarrier())
                    .source(EtaReportedSource.Source.CARRIER_BOOKING)
                    .reportedDays(booking.getEstimatedTransitDays())
                    .reportedArrival(reportedArrival)
                    .originCountry(shipment.getShipperCountry())
                    .destinationCountry(shipment.getConsigneeCountry())
                    .transportMode(shipment.getTransportMode())
                    .build();
            etaReportedSourceRepo.save(source);
            eventRecorder.record(shipment, ShipmentEvent.EventType.ETA_CAPTURED,
                    Map.of("source", EtaReportedSource.Source.CARRIER_BOOKING.name(),
                            "reportedArrival", String.valueOf(reportedArrival)));
        } catch (Exception e) {
            log.warn("Échec de capture EtaReportedSource pour la réservation {}: {}", booking.getId(), e.getMessage());
        }
    }

    @Transactional
    public CarrierBookingRequest cancelBooking(UUID bookingId) {
        Long companyId = TenantContext.companyId();

        CarrierBookingRequest booking = bookingRepo.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        CarrierBookingRequest.BookingStatus current = booking.getCarrierBookingStatus();
        if (current == CarrierBookingRequest.BookingStatus.COMPLETED ||
            current == CarrierBookingRequest.BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel booking in status: " + current);
        }

        if (booking.getCarrierReference() != null) {
            try {
                CarrierAdapter adapter = findAdapter(booking.getCarrier().getCode());
                adapter.cancelBooking(booking.getCarrierReference());
            } catch (Exception e) {
                log.warn("Failed to cancel with carrier: {}", e.getMessage());
            }
        }

        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CANCELLED);
        return bookingRepo.save(booking);
    }

    @Transactional(readOnly = true)
    public CarrierBookingRequest getBooking(UUID bookingId) {
        Long companyId = TenantContext.companyId();
        return bookingRepo.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listBookings() {
        Long companyId = TenantContext.companyId();
        return bookingRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public Page<CarrierBookingRequest> listBookings(Pageable pageable) {
        Long companyId = TenantContext.companyId();
        return bookingRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listByShipment(UUID shipmentId) {
        Long companyId = TenantContext.companyId();
        return bookingRepo.findByShipmentOrderIdAndCompanyId(shipmentId, companyId);
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listByCarrier(UUID carrierId) {
        Long companyId = TenantContext.companyId();
        return bookingRepo.findByCarrierIdAndCompanyId(carrierId, companyId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long companyId = TenantContext.companyId();
        long total = bookingRepo.countByCompanyId(companyId);
        long pending = bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.PENDING);
        long confirmed = bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.CONFIRMED);
        long completed = bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.COMPLETED);
        long failed = bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.FAILED);
        long cancelled = bookingRepo.countByCompanyIdAndStatus(companyId, CarrierBookingRequest.BookingStatus.CANCELLED);
        return Map.of(
            "total", total,
            "pending", pending,
            "confirmed", confirmed,
            "completed", completed,
            "failed", failed,
            "cancelled", cancelled
        );
    }
}
