package com.incokalk.controller.config;

import com.incokalk.dto.config.ErpConfigDTO;
import com.incokalk.dto.config.ErpHealthDTO;
import com.incokalk.dto.config.ErpSyncLogDTO;
import com.incokalk.dto.shared.SyncRequestDTO;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.ErpSyncLog;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.erp.ErpSyncService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/erp")
@RequiredArgsConstructor
@Tag(name = "ERP Integration", description = "Intégration ERP (Odoo, SAP, QuickBooks)")
public class ErpController {

    private final ErpSyncService erpSyncService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les configurations ERP")
    public ResponseEntity<List<ErpConfig>> listConfigs(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.listConfigs(companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une configuration ERP")
    public ResponseEntity<ErpConfig> createConfig(
            @Valid @RequestBody ErpConfigDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.createConfig(dto, companyId));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour une configuration ERP")
    public ResponseEntity<ErpConfig> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody ErpConfigDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.updateConfig(id, dto, companyId));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une configuration ERP")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        erpSyncService.deleteConfig(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Tester la connexion ERP")
    public ResponseEntity<Map<String, Boolean>> testConnection(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        boolean success = erpSyncService.testConnection(id, companyId);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Lancer une synchronisation ERP")
    public ResponseEntity<ErpSyncLog> sync(
            @PathVariable UUID id,
            @Valid @RequestBody SyncRequestDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.sync(id, dto, companyId));
    }

    @GetMapping("/sync-logs")
    @Operation(summary = "Historique des synchronisations")
    public ResponseEntity<List<ErpSyncLogDTO>> getSyncLogs(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.getSyncLogs(companyId));
    }

    @GetMapping("/health")
    @Operation(summary = "État de santé des ERP configurés")
    public ResponseEntity<List<ErpHealthDTO>> getHealth(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.getHealth(companyId));
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "Récupérer les produits depuis l'ERP")
    public ResponseEntity<List<Map<String, Object>>> getProducts(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.getProducts(id, companyId));
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Récupérer les commandes depuis l'ERP")
    public ResponseEntity<List<Map<String, Object>>> getOrders(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.getOrders(id, companyId));
    }

    @GetMapping("/{id}/contacts")
    @Operation(summary = "Récupérer les contacts depuis l'ERP")
    public ResponseEntity<List<Map<String, Object>>> getContacts(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(erpSyncService.getContacts(id, companyId));
    }
}
