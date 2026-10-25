package com.logistix.controller;

import com.logistix.dto.DashboardSummaryDto;
import com.logistix.service.DashboardSummaryService;
import com.logistix.service.PraxioIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Résumé d'activité Logistix pour le tableau de bord cross-hub de Praxio --
 * jamais un utilisateur connecté. permitAll côté Spring Security (voir
 * SecurityConfig, "/v1/internal/**") : l'authentification se fait ici, via
 * X-Internal-Integration-Key plutôt qu'un JWT, résolue en société par
 * {@link PraxioIntegrationService}, jamais depuis un identifiant fourni par
 * l'appelant.
 */
@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Résumé d'activité pour le tableau de bord cross-hub de Praxio")
public class InternalDashboardController {

    private final PraxioIntegrationService praxioIntegrationService;
    private final DashboardSummaryService dashboardSummaryService;

    @GetMapping("/dashboard-summary")
    @Operation(summary = "Résumé d'activité (Praxio)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Résumé retourné")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public DashboardSummaryDto getDashboardSummary(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = praxioIntegrationService.resolveCompanyId(apiKey);
        return dashboardSummaryService.getDashboardSummary(companyId);
    }
}
