package com.fleethub.controller;

import com.fleethub.dto.BillingSummaryDto;
import com.fleethub.dto.InternalDashboardSummaryDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.InternalBillingSummaryService;
import com.fleethub.service.InternalDashboardSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Résumé d'activité fleet-hub pour le tableau de bord cross-hub de Praxio --
 * jamais un utilisateur connecté. La clé X-Internal-Integration-Key est
 * authentifiée en amont par InternalApiKeyFilter, qui pose la société dans
 * {@link TenantContext} et vérifie les droits de la clé (DASHBOARD_READ /
 * BILLING_READ : portail Praxio, compliance-engine pour le résumé d'activité).
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Résumé d'activité pour le tableau de bord cross-hub de Praxio")
public class InternalDashboardController {

    private final InternalDashboardSummaryService internalDashboardSummaryService;
    private final InternalBillingSummaryService internalBillingSummaryService;

    @GetMapping("/dashboard-summary")
    @Operation(summary = "Résumé d'activité (Praxio)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Résumé retourné")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public InternalDashboardSummaryDto getDashboardSummary() {
        Long companyId = TenantContext.require().getId();
        return internalDashboardSummaryService.getDashboardSummary(companyId);
    }

    @GetMapping("/billing-summary")
    @Operation(summary = "Résumé de facturation (Praxio)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Résumé retourné")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public BillingSummaryDto getBillingSummary() {
        Long companyId = TenantContext.require().getId();
        return internalBillingSummaryService.getBillingSummary(companyId);
    }
}
