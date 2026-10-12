package com.fleethub.controller;

import com.fleethub.dto.CarrierBookingRequestDTO;
import com.fleethub.model.CarrierBookingRequest;
import com.fleethub.security.AppUserPrincipal;
import com.fleethub.service.CarrierBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Réservations auprès des transporteurs tiers — porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/carrier-bookings")
@RequiredArgsConstructor
public class CarrierBookingController {

    private final CarrierBookingService bookingService;

    @PostMapping
    public ResponseEntity<CarrierBookingRequest> createBooking(@Valid @RequestBody CarrierBookingRequestDTO dto) {
        Long userId = principal().getId();
        CarrierBookingRequest booking = bookingService.createBooking(dto, userId);
        return ResponseEntity.created(URI.create("/api/carrier-bookings/" + booking.getId())).body(booking);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<CarrierBookingRequest> submitBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.submitBooking(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CarrierBookingRequest> cancelBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @GetMapping
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
    public ResponseEntity<CarrierBookingRequest> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @GetMapping("/shipment/{shipmentId}")
    public ResponseEntity<List<CarrierBookingRequest>> listByShipment(@PathVariable UUID shipmentId) {
        return ResponseEntity.ok(bookingService.listByShipment(shipmentId));
    }

    @GetMapping("/carrier/{carrierId}")
    public ResponseEntity<List<CarrierBookingRequest>> listByCarrier(@PathVariable UUID carrierId) {
        return ResponseEntity.ok(bookingService.listByCarrier(carrierId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(bookingService.getStats());
    }

    private AppUserPrincipal principal() {
        return (AppUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
