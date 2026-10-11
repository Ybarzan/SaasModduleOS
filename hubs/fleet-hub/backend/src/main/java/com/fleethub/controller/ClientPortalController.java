package com.fleethub.controller;

import com.fleethub.model.ShipmentOrder;
import com.fleethub.security.ClientAuthFilter;
import com.fleethub.service.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Portail client self-service — porté depuis compliance-engine
 * (ClientPortalController). Le téléchargement de documents PDF liés à une
 * expédition (label, CMR, DGD, certificat d'origine — DocumentExportService)
 * n'est pas porté : ce service n'existe pas dans FleetHub et n'était pas
 * dans le périmètre de cette fusion.
 */
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Portail client self-service")
public class ClientPortalController {

    private final ClientAuthService clientAuthService;

    @GetMapping("/shipments")
    @Operation(summary = "Liste des expéditions du client")
    public ResponseEntity<List<Map<String, Object>>> myShipments(HttpServletRequest req) {
        Long companyId = requireCompanyId(req);
        UUID clientId = requireClientId(req);
        List<ShipmentOrder> shipments = clientAuthService.getMyShipments(companyId, clientId);
        return ResponseEntity.ok(shipments.stream().map(this::toShipmentSummary).toList());
    }

    @GetMapping("/shipments/{id}")
    @Operation(summary = "Détail d'une expédition avec suivi")
    public ResponseEntity<Map<String, Object>> shipmentDetail(@PathVariable UUID id, HttpServletRequest req) {
        Long companyId = requireCompanyId(req);
        UUID clientId = requireClientId(req);
        ShipmentOrder shipment = clientAuthService.getShipmentDetail(companyId, clientId, id)
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Expédition introuvable"));
        Map<String, Object> detail = toShipmentSummary(shipment);
        detail.put("trackingEvents", shipment.getTrackingEvents().stream()
            .map(this::toTrackingEvent)
            .sorted((a, b) -> {
                LocalDateTime ta = (LocalDateTime) a.get("eventTime");
                LocalDateTime tb = (LocalDateTime) b.get("eventTime");
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            })
            .toList());
        return ResponseEntity.ok(detail);
    }

    private Map<String, Object> toShipmentSummary(ShipmentOrder s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("orderNumber", s.getOrderNumber());
        m.put("status", s.getStatus().name());
        m.put("shipperCity", s.getShipperCity());
        m.put("shipperCountry", s.getShipperCountry());
        m.put("consigneeCity", s.getConsigneeCity());
        m.put("consigneeCountry", s.getConsigneeCountry());
        m.put("goodsDescription", s.getGoodsDescription());
        m.put("weightKg", s.getWeightKg());
        m.put("volumeM3", s.getVolumeM3());
        m.put("packagesCount", s.getPackagesCount());
        m.put("carrierName", s.getCarrier() != null ? s.getCarrier().getName() : null);
        m.put("quotedCost", s.getQuotedCost());
        m.put("finalCost", s.getFinalCost());
        m.put("costCurrency", s.getCostCurrency());
        m.put("incotermCode", s.getIncotermCode());
        m.put("estimatedDeliveryDate", s.getEstimatedDeliveryDate());
        m.put("actualDeliveryDate", s.getActualDeliveryDate());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    private Map<String, Object> toTrackingEvent(com.fleethub.model.TrackingEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("status", e.getStatus());
        m.put("location", e.getLocation());
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        m.put("description", e.getDescription());
        m.put("eventTime", e.getEventTime());
        m.put("source", e.getSource());
        return m;
    }

    private Long requireCompanyId(HttpServletRequest req) {
        Long companyId = ClientAuthFilter.clientCompanyId(req);
        if (companyId == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié");
        return companyId;
    }

    private UUID requireClientId(HttpServletRequest req) {
        UUID clientId = ClientAuthFilter.clientId(req);
        if (clientId == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié");
        return clientId;
    }
}
