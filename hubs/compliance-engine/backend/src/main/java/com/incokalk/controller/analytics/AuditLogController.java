package com.incokalk.controller.analytics;

import com.incokalk.model.AuditLog;
import com.incokalk.service.AuditLogService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Journal d'activité")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Lister les journaux d'activité")
    public ResponseEntity<Page<AuditLog>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(auditLogService.listByCompany(companyId, page, size));
    }

    @GetMapping("/action/{action}")
    @Operation(summary = "Filtrer par action")
    public ResponseEntity<Page<AuditLog>> listByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(auditLogService.listByCompanyAndAction(companyId, action, page, size));
    }

    @GetMapping("/entity/{entityType}")
    @Operation(summary = "Filtrer par type d'entité")
    public ResponseEntity<Page<AuditLog>> listByEntity(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(auditLogService.listByCompanyAndEntity(companyId, entityType, page, size));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Filtrer par utilisateur")
    public ResponseEntity<Page<AuditLog>> listByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(auditLogService.listByCompanyAndUser(companyId, userId, page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques du journal d'activité")
    public ResponseEntity<Map<String, Object>> getStats() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(auditLogService.getStats(companyId));
    }
}
