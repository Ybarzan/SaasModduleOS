package com.incokalk.controller.shared;

import com.incokalk.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Réception des callbacks transporteurs (Shippo, DHL, etc.)")
public class WebhookController {

    private final ShipmentService shipmentService;

    @PostMapping("/shippo")
    @Operation(summary = "Webhook Shippo — mise à jour de statut d'expédition")
    public ResponseEntity<Map<String, String>> handleShippoWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook Shippo reçu: {}", payload);
        try {
            String trackingNumber = extractString(payload, "tracking_number");
            String status = extractString(payload, "status");
            String location = extractString(payload, "location");
            String description = extractString(payload, "description");

            if (trackingNumber != null && status != null) {
                shipmentService.processWebhookEvent(trackingNumber, mapShippoStatus(status), location, description, "SHIPPO");
            }
        } catch (Exception e) {
            log.error("Erreur traitement webhook Shippo: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("received", "true"));
    }

    @PostMapping("/dhl")
    @Operation(summary = "Webhook DHL — mise à jour de statut d'expédition")
    public ResponseEntity<Map<String, String>> handleDhlWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook DHL reçu: {}", payload);
        try {
            String trackingNumber = extractString(payload, "trackingNumber");
            String status = extractString(payload, "status");
            String location = extractString(payload, "location");
            String description = extractString(payload, "description");

            if (trackingNumber != null && status != null) {
                shipmentService.processWebhookEvent(trackingNumber, mapDhlStatus(status), location, description, "DHL");
            }
        } catch (Exception e) {
            log.error("Erreur traitement webhook DHL: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("received", "true"));
    }

    @PostMapping("/generic")
    @Operation(summary = "Webhook générique — format universel")
    public ResponseEntity<Map<String, String>> handleGenericWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook générique reçu: {}", payload);
        try {
            String trackingNumber = extractString(payload, "tracking_number");
            String status = extractString(payload, "status");
            String location = extractString(payload, "location");
            String description = extractString(payload, "description");
            String source = extractString(payload, "source");

            if (trackingNumber != null && status != null) {
                shipmentService.processWebhookEvent(trackingNumber, status, location, description,
                    source != null ? source : "WEBHOOK");
            }
        } catch (Exception e) {
            log.error("Erreur traitement webhook générique: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("received", "true"));
    }

    private String mapShippoStatus(String shippoStatus) {
        return switch (shippoStatus.toLowerCase()) {
            case "delivered" -> "DELIVERED";
            case "in_transit" -> "IN_TRANSIT";
            case "pre_transit" -> "BOOKED";
            case "returned" -> "CANCELLED";
            default -> shippoStatus.toUpperCase();
        };
    }

    private String mapDhlStatus(String dhlStatus) {
        if (dhlStatus == null) return "UNKNOWN";
        return switch (dhlStatus.toUpperCase()) {
            case "DELIVERED", "D5" -> "DELIVERED";
            case "TRANSIT", "IT" -> "IN_TRANSIT";
            case "CUSTOMS" -> "IN_TRANSIT";
            case "FAILED" -> "IN_TRANSIT";
            default -> dhlStatus.toUpperCase();
        };
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
