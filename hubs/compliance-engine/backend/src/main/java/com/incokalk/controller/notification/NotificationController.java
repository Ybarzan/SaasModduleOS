package com.incokalk.controller.notification;

import com.incokalk.dto.notification.MarkReadDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.Notification;
import com.incokalk.model.NotificationRule;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.NotificationService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Gestion des notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les notifications de l'utilisateur")
    public ResponseEntity<?> listNotifications(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        UUID userId = (UUID) httpReq.getAttribute("userId");
        if (page != null && size != null && size > 0) {
            Page<Notification> result = notificationService.listNotifications(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(notificationService.listNotifications(companyId, userId));
    }

    @GetMapping("/unread-count")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        UUID userId = (UUID) httpReq.getAttribute("userId");
        int count = notificationService.getUnreadCount(companyId, userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/read")
    @Operation(summary = "Marquer des notifications comme lues")
    public ResponseEntity<Void> markAsRead(
            @Valid @RequestBody MarkReadDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        notificationService.markAsRead(dto.getNotificationIds(), companyId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Void> markAllAsRead(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        UUID userId = (UUID) httpReq.getAttribute("userId");
        notificationService.markAllAsRead(companyId, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archiver une notification")
    public ResponseEntity<Void> archiveNotification(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        notificationService.archiveNotification(id, companyId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une notification")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        notificationService.deleteNotification(id, companyId);
        return ResponseEntity.noContent().build();
    }
}
