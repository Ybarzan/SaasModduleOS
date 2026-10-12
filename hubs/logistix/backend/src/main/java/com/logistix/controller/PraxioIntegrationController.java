package com.logistix.controller;

import com.logistix.dto.PraxioIntegrationSettingsDto;
import com.logistix.security.TenantContext;
import com.logistix.service.PraxioIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Partage opt-in d'un résumé d'activité vers le tableau de bord cross-hub de
 * Praxio. Réservé ADMIN -- contrairement à "/v1/internal/**" (lecture
 * machine-à-machine elle-même), qui est permitAll et s'auto-authentifie via
 * la clé (voir SecurityConfig).
 */
@RestController
@RequestMapping("/v1/praxio-integration")
@RequiredArgsConstructor
@Tag(name = "Intégration Praxio", description = "Partage opt-in d'un résumé d'activité vers le tableau de bord cross-hub de Praxio")
public class PraxioIntegrationController {

    private final PraxioIntegrationService praxioIntegrationService;

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statut du partage")
    public PraxioIntegrationSettingsDto settings() {
        return praxioIntegrationService.settings(TenantContext.get());
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer le partage", description = "Active la lecture du résumé d'activité et (re)génère la clé si absente. La clé n'est montrée que dans cette réponse.")
    public PraxioIntegrationSettingsDto enable() {
        return praxioIntegrationService.enable(TenantContext.get());
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver le partage", description = "Désactive la lecture ; la clé existante est conservée mais rejetée tant que le partage est désactivé")
    public PraxioIntegrationSettingsDto disable() {
        praxioIntegrationService.disable(TenantContext.get());
        return praxioIntegrationService.settings(TenantContext.get());
    }
}
