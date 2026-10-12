package com.fleethub.controller;

import com.fleethub.dto.InternalIntegrationSettingsDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.InternalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Partage opt-in d'expéditions vers compliance-engine / Logistix. Réservé
 * ADMIN (voir SecurityConfig, "/api/internal-integrations/**" -> ADMIN) --
 * contrairement à "/api/internal/**" (lecture machine-à-machine elle-même),
 * qui est permitAll et s'auto-authentifie via la clé.
 */
@RestController
@RequestMapping("/api/internal-integrations")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Partage opt-in d'expéditions vers compliance-engine / Logistix")
public class InternalIntegrationController {

    private final InternalIntegrationService internalIntegrationService;

    @GetMapping("/settings")
    @Operation(summary = "Statut du partage")
    public InternalIntegrationSettingsDto settings() {
        return internalIntegrationService.settings(TenantContext.companyId());
    }

    @PostMapping("/enable")
    @Operation(summary = "Activer le partage", description = "Active la lecture d'expéditions et (re)génère la clé si absente. La clé n'est montrée que dans cette réponse.")
    public InternalIntegrationSettingsDto enable() {
        return internalIntegrationService.enable(TenantContext.companyId());
    }

    @PostMapping("/disable")
    @Operation(summary = "Désactiver le partage", description = "Désactive la lecture ; la clé existante est conservée mais rejetée tant que le partage est désactivé")
    public InternalIntegrationSettingsDto disable() {
        internalIntegrationService.disable(TenantContext.companyId());
        return internalIntegrationService.settings(TenantContext.companyId());
    }
}
