package com.incokalk.controller.portal;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.MobileDevice;
import com.incokalk.model.MobileNotification;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.MobileDashboardService;
import com.incokalk.service.PushNotificationService;
import com.incokalk.service.ShipmentService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/v1/mobile")
@RequiredArgsConstructor
@Tag(name = "Mobile API", description = "API dédiée à l'application mobile native")
public class MobileApiController {

    private final MobileDashboardService dashboardService;
    private final PushNotificationService pushNotificationService;
    private final ShipmentService shipmentService;

    @GetMapping("/dashboard")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Tableau de bord mobile — résumé léger")
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest req) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(dashboardService.getDashboard(companyId));
    }

    @GetMapping("/quick-quote")
    @Operation(summary = "Devis instantané simplifié pour mobile")
    public ResponseEntity<Map<String, Object>> quickQuote(
            @RequestParam @NotBlank String origin,
            @RequestParam @NotBlank String destination,
            @RequestParam @Positive Double weight,
            @RequestParam @NotBlank String incoterm) {
        double baseFreight = weight * 2.5;
        double fuelSurcharge = baseFreight * 0.15;
        double securitySurcharge = baseFreight * 0.03;
        double handlingFee = 120.0;
        double documentationFee = 75.0;
        double totalEstimated = baseFreight + fuelSurcharge + securitySurcharge + handlingFee + documentationFee;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("origin", origin);
        result.put("destination", destination);
        result.put("weight_kg", weight);
        result.put("incoterm", incoterm);
        result.put("currency", "EUR");
        result.put("estimated_total", Math.round(totalEstimated * 100.0) / 100.0);
        result.put("breakdown", Map.of(
                "base_freight", Math.round(baseFreight * 100.0) / 100.0,
                "fuel_surcharge", Math.round(fuelSurcharge * 100.0) / 100.0,
                "security_surcharge", Math.round(securitySurcharge * 100.0) / 100.0,
                "handling_fee", handlingFee,
                "documentation_fee", documentationFee
        ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notifications")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Liste des notifications push pour l'utilisateur")
    public ResponseEntity<List<MobileNotification>> notifications(HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        return ResponseEntity.ok(pushNotificationService.getNotifications(userId));
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "SSE — flux temps réel des notifications push")
    public SseEmitter streamNotifications(HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        return pushNotificationService.subscribe(userId);
    }

    @PostMapping("/notifications/{id}/read")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<Void> markNotificationRead(
            @PathVariable UUID id,
            HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        pushNotificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notifications/read-all")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Void> markAllNotificationsRead(HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        pushNotificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/device/register")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Enregistrer un token de device pour push notifications")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @Valid @RequestBody DeviceRegisterRequest body,
            HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        UUID companyId = TenantContext.get();

        MobileDevice device = pushNotificationService.registerDevice(
            body.getDeviceToken(), body.getPlatform(), body.getAppVersion(), userId, companyId);

        return ResponseEntity.ok(Map.of(
                "id", device.getId(),
                "registered", true
        ));
    }

    @PostMapping("/device/unregister")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Désenregistrer un device token")
    public ResponseEntity<Void> unregisterDevice(
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {
        pushNotificationService.unregisterDevice(body.get("deviceToken"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/profile")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Profil utilisateur + entreprise pour affichage mobile")
    public ResponseEntity<Map<String, Object>> profile(HttpServletRequest req) {
        UUID userId = (UUID) req.getAttribute("userId");
        UUID companyId = TenantContext.get();

        var user = dashboardService.getUserProfile(userId);
        var company = dashboardService.getCompanyProfile(companyId);
        if (user == null || company == null) return ResponseEntity.notFound().build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "full_name", user.getFullName(),
                "plan", user.getPlan().name()
        ));
        result.put("company", Map.of(
                "id", company.getId(),
                "name", company.getName(),
                "slug", company.getSlug(),
                "logo_url", company.getLogoUrl(),
                "plan", company.getPlan().name()
        ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent-shipments")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "20 dernières expéditions avec champs simplifiés")
    public ResponseEntity<List<Map<String, Object>>> recentShipments(HttpServletRequest req) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(dashboardService.getRecentShipments(companyId, 20));
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    public static class DeviceRegisterRequest {
        @NotBlank
        private String deviceToken;
        @NotBlank
        private String platform;
        private String appVersion;
    }
}
