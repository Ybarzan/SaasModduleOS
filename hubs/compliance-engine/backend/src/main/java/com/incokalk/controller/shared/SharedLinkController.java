package com.incokalk.controller.shared;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.SharedLink;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.SharedLinkService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/shared")
@RequiredArgsConstructor
@Tag(name = "Shared Links", description = "Liens de suivi partagés")
public class SharedLinkController {

    private final SharedLinkService sharedLinkService;

    // ── Admin endpoints (authenticated) ──────────────────────────────────

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Créer un lien de suivi partagé")
    public ResponseEntity<Map<String, Object>> createLink(
            @Valid @RequestBody CreateLinkReq req, HttpServletRequest httpReq) {
        UUID companyId = extractCompanyId(httpReq);
        UUID userId = extractUserId(httpReq);
        SharedLink link = sharedLinkService.createLink(
            companyId, req.shipmentId(), userId, req.label(), req.expiresHours());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", link.getId());
        result.put("token", link.getToken());
        result.put("label", link.getLabel());
        result.put("url", "/s/" + link.getToken());
        result.put("expiresAt", link.getExpiresAt() != null ? link.getExpiresAt().toString() : null);
        result.put("createdAt", link.getCreatedAt().toString());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Liste des liens partagés")
    public ResponseEntity<List<Map<String, Object>>> listLinks(HttpServletRequest httpReq) {
        UUID companyId = extractCompanyId(httpReq);
        List<SharedLink> links = sharedLinkService.listLinks(companyId);
        return ResponseEntity.ok(links.stream().map(this::toLinkSummary).toList());
    }

    @GetMapping("/shipment/{shipmentId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Liens pour une expédition")
    public ResponseEntity<List<Map<String, Object>>> linksForShipment(
            @PathVariable UUID shipmentId, HttpServletRequest httpReq) {
        UUID companyId = extractCompanyId(httpReq);
        List<SharedLink> links = sharedLinkService.listLinksForShipment(shipmentId, companyId);
        return ResponseEntity.ok(links.stream().map(this::toLinkSummary).toList());
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Révoquer un lien")
    public ResponseEntity<Map<String, String>> revokeLink(
            @PathVariable UUID id, HttpServletRequest httpReq) {
        UUID companyId = extractCompanyId(httpReq);
        sharedLinkService.revokeLink(id, companyId);
        return ResponseEntity.ok(Map.of("message", "Lien révoqué"));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Statistiques des liens partagés")
    public ResponseEntity<Map<String, Object>> linkStats(HttpServletRequest httpReq) {
        UUID companyId = extractCompanyId(httpReq);
        return ResponseEntity.ok(sharedLinkService.linkStats(companyId));
    }

    // ── Public endpoint ───────────────────────────────────────────────────

    @GetMapping("/access/{token}")
    @Operation(summary = "Accéder au suivi via lien partagé (public)")
    public ResponseEntity<Map<String, Object>> accessSharedLink(@PathVariable String token) {
        SharedLink link = sharedLinkService.accessLink(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipment", toShipmentSummary(link.getShipment()));
        result.put("trackingEvents", link.getShipment().getTrackingEvents().stream()
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("status", e.getStatus());
                m.put("location", e.getLocation());
                m.put("latitude", e.getLatitude());
                m.put("longitude", e.getLongitude());
                m.put("description", e.getDescription());
                m.put("eventTime", e.getEventTime().toString());
                m.put("source", e.getSource());
                return m;
            })
            .sorted((a, b) -> ((String) b.get("eventTime")).compareTo((String) a.get("eventTime")))
            .toList());
        result.put("companyName", link.getCompany().getName());
        result.put("companyLogo", link.getCompany().getLogoUrl());
        result.put("label", link.getLabel());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toLinkSummary(SharedLink link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", link.getId());
        m.put("token", link.getToken());
        m.put("label", link.getLabel());
        m.put("url", "/s/" + link.getToken());
        m.put("shipmentId", link.getShipment().getId());
        m.put("orderNumber", link.getShipment().getOrderNumber());
        m.put("active", link.isActive());
        m.put("accessCount", link.getAccessCount());
        m.put("lastAccessedAt", link.getLastAccessedAt());
        m.put("expiresAt", link.getExpiresAt());
        m.put("createdAt", link.getCreatedAt());
        return m;
    }

    private Map<String, Object> toShipmentSummary(com.incokalk.model.ShipmentOrder s) {
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
        m.put("carrierName", s.getCarrier() != null ? s.getCarrier().getName() : null);
        m.put("incotermCode", s.getIncotermCode());
        m.put("estimatedDeliveryDate", s.getEstimatedDeliveryDate());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new RuntimeException("Entreprise non trouvée");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    record CreateLinkReq(@jakarta.validation.constraints.NotNull UUID shipmentId, String label, Integer expiresHours) {}
}
