package com.incokalk.service;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.dto.shipment.CarrierBookingRequestDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.repository.CarrierBookingRequestRepository;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.carrier.CarrierAdapter;
import com.incokalk.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CarrierBookingService {

    private static final Logger log = LoggerFactory.getLogger(CarrierBookingService.class);

    private final CarrierBookingRequestRepository bookingRepo;
    private final CarrierRepository carrierRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final UserRepository userRepo;
    private final List<CarrierAdapter> adapters;
    private final ObjectMapper objectMapper;

    public CarrierBookingService(
            CarrierBookingRequestRepository bookingRepo,
            CarrierRepository carrierRepo,
            ShipmentOrderRepository shipmentRepo,
            UserRepository userRepo,
            List<CarrierAdapter> adapters,
            ObjectMapper objectMapper) {
        this.bookingRepo = bookingRepo;
        this.carrierRepo = carrierRepo;
        this.shipmentRepo = shipmentRepo;
        this.userRepo = userRepo;
        this.adapters = adapters;
        this.objectMapper = objectMapper;
    }

    public CarrierAdapter findAdapter(String carrierCode) {
        return adapters.stream()
            .filter(a -> a.supports(carrierCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No adapter for carrier: " + carrierCode));
    }

    @Transactional
    public CarrierBookingRequest createBooking(CarrierBookingRequestDTO dto, UUID userId) {
        UUID companyId = TenantContext.get();

        Carrier carrier = carrierRepo.findByIdAndCompanyId(dto.getCarrierId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Carrier not found: " + dto.getCarrierId()));

        ShipmentOrder shipment = shipmentRepo.findByIdAndCompanyId(dto.getShipmentOrderId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + dto.getShipmentOrderId()));

        User user = userRepo.findById(userId).orElse(null);

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
        UUID companyId = TenantContext.get();

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

    @Transactional
    public CarrierBookingRequest cancelBooking(UUID bookingId) {
        UUID companyId = TenantContext.get();

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
        UUID companyId = TenantContext.get();
        return bookingRepo.findByIdAndCompanyId(bookingId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listBookings() {
        UUID companyId = TenantContext.get();
        return bookingRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public Page<CarrierBookingRequest> listBookings(Pageable pageable) {
        UUID companyId = TenantContext.get();
        return bookingRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listByShipment(UUID shipmentId) {
        UUID companyId = TenantContext.get();
        return bookingRepo.findByShipmentOrderIdAndCompanyId(shipmentId, companyId);
    }

    @Transactional(readOnly = true)
    public List<CarrierBookingRequest> listByCarrier(UUID carrierId) {
        UUID companyId = TenantContext.get();
        return bookingRepo.findByCarrierIdAndCompanyId(carrierId, companyId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        UUID companyId = TenantContext.get();
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
