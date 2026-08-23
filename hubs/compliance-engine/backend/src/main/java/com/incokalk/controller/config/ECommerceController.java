package com.incokalk.controller.config;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ECommerceSyncLog;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ECommerceSyncService;
import com.incokalk.service.ecommerce.ECommerceAdapter;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/ecommerce")
@RequiredArgsConstructor
@Tag(name = "E-Commerce", description = "Integrations e-commerce (Shopify, WooCommerce, PrestaShop)")
public class ECommerceController {

    private final ECommerceSyncService syncService;
    private final List<ECommerceAdapter> adapters;

    @PostMapping("/integrations")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une nouvelle intégration e-commerce")
    public ResponseEntity<ECommerceIntegration> createIntegration(
            @Valid @RequestBody CreateIntegrationRequest request,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();

        ECommerceIntegration saved = syncService.createIntegration(
            request.getPlatform(), request.getStoreUrl(), request.getApiKey(),
            request.getApiSecret(), request.getWebhookSecret(), request.getSyncFrequencyMin(), companyId);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/integrations")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN, com.incokalk.model.CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les intégrations de l'entreprise")
    public ResponseEntity<List<ECommerceIntegration>> listIntegrations(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(syncService.listIntegrations(companyId));
    }

    @PutMapping("/integrations/{id}")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN})
    @Operation(summary = "Modifier une intégration")
    public ResponseEntity<ECommerceIntegration> updateIntegration(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIntegrationRequest request,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ECommerceIntegration saved = syncService.updateIntegration(
            id, companyId, request.getStoreUrl(), request.getApiKey(),
            request.getApiSecret(), request.getWebhookSecret(),
            request.getIsActive(), request.getSyncFrequencyMin());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/integrations/{id}")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN})
    @Operation(summary = "Désactiver une intégration")
    public ResponseEntity<Void> deactivateIntegration(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        syncService.deactivateIntegration(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/integrations/{id}/sync")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN, com.incokalk.model.CompanyRole.Role.MANAGER})
    @Operation(summary = "Déclencher une synchronisation manuelle")
    public ResponseEntity<ECommerceSyncLog> triggerSync(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ECommerceIntegration integration = syncService.findIntegrationById(id, companyId)
            .orElseThrow(() -> new RuntimeException("Integration not found"));
        ECommerceSyncLog result = syncService.syncSingleIntegration(integration);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/integrations/{id}/orders")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN, com.incokalk.model.CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les commandes synchronisées")
    public ResponseEntity<List<Map<String, Object>>> listSyncedOrders(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ECommerceIntegration integration = syncService.findIntegrationById(id, companyId)
            .orElseThrow(() -> new RuntimeException("Integration not found"));
        ECommerceAdapter adapter = syncService.resolveAdapter(integration.getPlatform());
        if (adapter == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> orders = adapter.syncOrders(integration);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/sync-log")
    @RolesAllowed({com.incokalk.model.CompanyRole.Role.OWNER, com.incokalk.model.CompanyRole.Role.ADMIN, com.incokalk.model.CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des synchronisations")
    public ResponseEntity<List<ECommerceSyncLog>> getSyncLog(HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(syncService.getSyncLogs(companyId));
    }

    @Data
    public static class CreateIntegrationRequest {
        @jakarta.validation.constraints.NotNull
        private ECommerceIntegration.Platform platform;
        private String storeUrl;
        private String apiKey;
        private String apiSecret;
        private String webhookSecret;
        private Integer syncFrequencyMin;
    }

    @Data
    public static class UpdateIntegrationRequest {
        private String storeUrl;
        private String apiKey;
        private String apiSecret;
        private String webhookSecret;
        private Boolean isActive;
        private Integer syncFrequencyMin;
    }
}
