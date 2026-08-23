package com.incokalk.controller.shipment;

import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.LiveTrackingService;
import com.incokalk.service.tracking.LivePosition;
import com.incokalk.service.tracking.TrackingUpdate;
import com.incokalk.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tracking")
@RequiredArgsConstructor
@Tag(name = "Live Tracking", description = "Suivi en temps réel des expéditions")
public class TrackingController {

    private final LiveTrackingService trackingService;

    @GetMapping("/shipments/{id}")
    @Operation(summary = "Suivi live d'une expédition")
    public ResponseEntity<List<TrackingUpdate>> trackShipment(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(trackingService.trackShipment(id, companyId));
    }

    @GetMapping("/shipments/{id}/position")
    @Operation(summary = "Position actuelle (lat/lng)")
    public ResponseEntity<LivePosition> getPosition(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(trackingService.getLivePosition(id, companyId));
    }

    @PostMapping("/lookup")
    @Operation(summary = "Recherche de tracking par numéro")
    public ResponseEntity<List<TrackingUpdate>> trackByNumber(
            @Valid @RequestBody TrackByNumberReq request) {
        UUID companyId = TenantContext.get();
        String number = request.trackingNumber();
        String mode = request.mode() != null ? request.mode() : "ROAD";
        return ResponseEntity.ok(trackingService.trackByNumber(number, mode, companyId));
    }

    @PostMapping("/shipments/{id}/sync")
    @Operation(summary = "Synchroniser le suivi live vers les événements")
    public ResponseEntity<ShipmentOrder> syncTracking(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(trackingService.syncTrackingToEvents(id, companyId));
    }

    public record TrackByNumberReq(@NotBlank String trackingNumber, String mode) {}
}
