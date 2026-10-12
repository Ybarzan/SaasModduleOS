package com.fleethub.controller;

import com.fleethub.dto.InternalDashboardSummaryDto;
import com.fleethub.service.InternalDashboardSummaryService;
import com.fleethub.service.InternalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Résumé d'activité fleet-hub pour le tableau de bord cross-hub de Praxio --
 * jamais un utilisateur connecté. permitAll côté Spring Security (voir
 * SecurityConfig, "/api/internal/**") : l'authentification se fait ici, via
 * X-Internal-Integration-Key, résolue en société par
 * {@link InternalIntegrationService} -- la même clé que celle utilisée par
 * compliance-engine / Logistix pour lire les expéditions.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Résumé d'activité pour le tableau de bord cross-hub de Praxio")
public class InternalDashboardController {

    private final InternalIntegrationService internalIntegrationService;
    private final InternalDashboardSummaryService internalDashboardSummaryService;

    @GetMapping("/dashboard-summary")
    @Operation(summary = "Résumé d'activité (Praxio)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Résumé retourné")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public InternalDashboardSummaryDto getDashboardSummary(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        Long companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return internalDashboardSummaryService.getDashboardSummary(companyId);
    }
}
