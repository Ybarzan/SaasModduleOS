package com.fleethub.controller;

import com.fleethub.dto.MarketplaceAvailabilityDto;
import com.fleethub.dto.MarketplaceSettingsDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Partage opt-in vers FleetMarket. /settings, /opt-in et /opt-out sont
 * réservés ADMIN (voir SecurityConfig, "/api/marketplace/**" -> ADMIN) ;
 * /availability est permitAll côté Spring Security et s'auto-authentifie
 * via l'en-tête X-Marketplace-Key (appel machine-à-machine de FleetMarket,
 * jamais un utilisateur connecté).
 */
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Tag(name = "Marketplace", description = "Partage opt-in de disponibilité vers FleetMarket")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/settings")
    @Operation(summary = "Statut du partage", description = "Indique si la société publie sa disponibilité sur FleetMarket")
    public MarketplaceSettingsDto settings() {
        return marketplaceService.settings(TenantContext.companyId());
    }

    @PostMapping("/opt-in")
    @Operation(summary = "Activer le partage", description = "Active la publication de disponibilité et (re)génère la clé si absente. La clé n'est montrée que dans cette réponse.")
    public MarketplaceSettingsDto optIn() {
        return marketplaceService.optIn(TenantContext.companyId());
    }

    @PostMapping("/opt-out")
    @Operation(summary = "Désactiver le partage", description = "Désactive la publication ; la clé existante est conservée mais rejetée tant que le partage est désactivé")
    public MarketplaceSettingsDto optOut() {
        marketplaceService.optOut(TenantContext.companyId());
        return marketplaceService.settings(TenantContext.companyId());
    }

    @GetMapping("/availability")
    @Operation(summary = "Disponibilité (FleetMarket)", description = "Appel machine-à-machine de FleetMarket, authentifié par X-Marketplace-Key")
    @ApiResponse(responseCode = "200", description = "Disponibilité retournée")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public MarketplaceAvailabilityDto availability(@RequestHeader(value = "X-Marketplace-Key", required = false) String apiKey) {
        return marketplaceService.availability(apiKey);
    }
}
