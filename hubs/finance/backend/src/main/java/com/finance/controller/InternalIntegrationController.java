package com.finance.controller;

import com.finance.dto.InternalIntegrationSettingsDto;
import com.finance.security.TenantContext;
import com.finance.service.InternalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Partage opt-in d'agrégats Finance vers Praxio (tableau de bord cross-hub)
 * et compliance-engine (FinanceIntegrationClient). Réservé ADMIN --
 * contrairement à "/v1/internal/**" (lecture machine-à-machine elle-même),
 * qui est permitAll et s'auto-authentifie via la clé (voir SecurityConfig).
 */
@RestController
@RequestMapping("/v1/internal-integration")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Partage opt-in d'agrégats Finance vers Praxio et compliance-engine")
public class InternalIntegrationController {

    private final InternalIntegrationService internalIntegrationService;

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statut du partage")
    public InternalIntegrationSettingsDto settings() {
        return internalIntegrationService.settings(TenantContext.get());
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer le partage", description = "Active la lecture des agrégats et (re)génère la clé si absente. La clé n'est montrée que dans cette réponse.")
    public InternalIntegrationSettingsDto enable() {
        return internalIntegrationService.enable(TenantContext.get());
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver le partage", description = "Désactive la lecture ; la clé existante est conservée mais rejetée tant que le partage est désactivé")
    public InternalIntegrationSettingsDto disable() {
        internalIntegrationService.disable(TenantContext.get());
        return internalIntegrationService.settings(TenantContext.get());
    }
}
