package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.CarrierBookingRequestDTO;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CarrierBookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/carrier-bookings")
@RequiresPlan(Company.Plan.STARTER)
public class CarrierBookingController {

    private final CarrierBookingService bookingService;

    public CarrierBookingController(CarrierBookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<CarrierBookingRequest> createBooking(@Valid @RequestBody CarrierBookingRequestDTO dto,
                                                              HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        CarrierBookingRequest booking = bookingService.createBooking(dto, userId);
        return ResponseEntity.created(URI.create("/v1/carrier-bookings/" + booking.getId())).body(booking);
    }

    @PostMapping("/{id}/submit")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    public ResponseEntity<CarrierBookingRequest> submitBooking(@PathVariable UUID id) {
        CarrierBookingRequest booking = bookingService.submitBooking(id);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/{id}/cancel")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    public ResponseEntity<CarrierBookingRequest> cancelBooking(@PathVariable UUID id) {
        CarrierBookingRequest booking = bookingService.cancelBooking(id);
        return ResponseEntity.ok(booking);
    }

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<?> listBookings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null && size > 0) {
            Page<CarrierBookingRequest> result = bookingService.listBookings(PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(bookingService.listBookings());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<CarrierBookingRequest> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @GetMapping("/shipment/{shipmentId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<List<CarrierBookingRequest>> listByShipment(@PathVariable UUID shipmentId) {
        return ResponseEntity.ok(bookingService.listByShipment(shipmentId));
    }

    @GetMapping("/carrier/{carrierId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    public ResponseEntity<List<CarrierBookingRequest>> listByCarrier(@PathVariable UUID carrierId) {
        return ResponseEntity.ok(bookingService.listByCarrier(carrierId));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(bookingService.getStats());
    }
}
