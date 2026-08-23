package com.incokalk.controller.notification;

import com.incokalk.dto.notification.NotificationRuleDTO;
import com.incokalk.model.CompanyRole;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/notification-rules")
@RequiredArgsConstructor
@Tag(name = "Notification Rules", description = "Gestion des règles de notification")
public class NotificationRuleController {

    private final NotificationService notificationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les règles de notification")
    public ResponseEntity<?> listRules(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        if (page != null && size != null && size > 0) {
            Page<NotificationRule> result = notificationService.listRules(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(notificationService.listRules(companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une règle de notification")
    public ResponseEntity<NotificationRule> createRule(
            @Valid @RequestBody NotificationRuleDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(notificationService.createRule(dto, companyId, userId));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour une règle de notification")
    public ResponseEntity<NotificationRule> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody NotificationRuleDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(notificationService.updateRule(id, dto, companyId, userId));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une règle de notification")
    public ResponseEntity<Void> deleteRule(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        notificationService.deleteRule(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Envoyer une notification de test")
    public ResponseEntity<Void> sendTestNotification(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        notificationService.sendTestNotification(companyId);
        return ResponseEntity.ok().build();
    }
}
