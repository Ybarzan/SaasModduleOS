package com.incokalk.controller.portal;

import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.ClientAuthService;
import com.incokalk.service.DocumentExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/v1/client")
@RequiredArgsConstructor
@Tag(name = "Client Portal", description = "Portail client self-service")
public class ClientPortalController {

    private final ClientAuthService clientAuthService;
    private final DocumentExportService documentExportService;

    @GetMapping("/shipments/{id}/documents/{type}")
    @Operation(summary = "Telecharger un document lie a l'expedition (label, cmr, dgd, certificate-of-origin)")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID id, @PathVariable String type, HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        UUID clientId = extractClientId(req);
        ShipmentOrder shipment = clientAuthService.getShipmentDetail(companyId, clientId, id)
            .orElseThrow(() -> new RuntimeException("Expédition introuvable"));

        byte[] pdf = switch (type) {
            case "label" -> documentExportService.generateShippingLabelPdf(shipment);
            case "cmr" -> documentExportService.generateCmrPdf(shipment);
            case "dgd" -> documentExportService.generateDgdPdf(shipment);
            case "certificate-of-origin" -> documentExportService.generateCertificateOfOriginPdf(shipment);
            default -> throw new IllegalArgumentException("Type de document inconnu: " + type);
        };

        String filename = type + "-" + shipment.getOrderNumber() + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/shipments")
    @Operation(summary = "Liste des expéditions du client")
    public ResponseEntity<List<Map<String, Object>>> myShipments(HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        UUID clientId = extractClientId(req);
        List<ShipmentOrder> shipments = clientAuthService.getMyShipments(companyId, clientId);
        return ResponseEntity.ok(shipments.stream().map(this::toShipmentSummary).toList());
    }

    @GetMapping("/shipments/{id}")
    @Operation(summary = "Détail d'une expédition avec suivi")
    public ResponseEntity<Map<String, Object>> shipmentDetail(
            @PathVariable UUID id, HttpServletRequest req) {
        UUID companyId = extractCompanyId(req);
        UUID clientId = extractClientId(req);
        ShipmentOrder shipment = clientAuthService.getShipmentDetail(companyId, clientId, id)
            .orElseThrow(() -> new RuntimeException("Expédition introuvable"));
        Map<String, Object> detail = toShipmentSummary(shipment);
        detail.put("trackingEvents", shipment.getTrackingEvents().stream()
            .map(this::toTrackingEvent)
            .sorted((a, b) -> ((LocalDateTime) b.get("eventTime")).compareTo((LocalDateTime) a.get("eventTime")))
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

    private Map<String, Object> toTrackingEvent(com.incokalk.model.TrackingEvent e) {
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

    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new RuntimeException("Entreprise non trouvée");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    // Pour un token CLIENT, l'attribut "userId" porte l'id du ClientUser authentifié.
    private UUID extractClientId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Client non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }
}
