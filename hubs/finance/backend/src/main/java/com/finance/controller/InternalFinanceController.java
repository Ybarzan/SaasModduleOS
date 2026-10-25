package com.finance.controller;

import com.finance.dto.internal.CarbonTotalsDto;
import com.finance.dto.internal.DashboardSummaryDto;
import com.finance.dto.internal.FinancialTotalsDto;
import com.finance.dto.internal.InvoiceTimelinessDto;
import com.finance.service.FinancialReportingService;
import com.finance.service.InternalFinanceDataService;
import com.finance.service.InternalIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Agrégats Finance pour les appelants machine-à-machine : Praxio (tableau de
 * bord cross-hub) et compliance-engine (FinanceIntegrationClient --
 * AnalyticsService, QualityMetricsService, MultiBranchService,
 * MobileDashboardService, CurrencyController). permitAll côté Spring
 * Security (voir SecurityConfig, "/v1/internal/**") : l'authentification se
 * fait ici, via X-Internal-Integration-Key plutôt qu'un JWT, résolue en
 * société par {@link InternalIntegrationService}, jamais depuis un
 * identifiant fourni par l'appelant.
 */
@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
@Tag(name = "Intégration interne", description = "Agrégats Finance pour Praxio et compliance-engine")
public class InternalFinanceController {

    private final InternalIntegrationService internalIntegrationService;
    private final InternalFinanceDataService internalFinanceDataService;
    private final FinancialReportingService financialReportingService;

    @GetMapping("/dashboard-summary")
    @Operation(summary = "Résumé d'activité (Praxio, AnalyticsService)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    @ApiResponse(responseCode = "200", description = "Résumé retourné")
    @ApiResponse(responseCode = "401", description = "Clé manquante, invalide, ou partage désactivé")
    public DashboardSummaryDto getDashboardSummary(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return internalFinanceDataService.getDashboardSummary(companyId);
    }

    @GetMapping("/invoice-timeliness")
    @Operation(summary = "Ponctualité de facturation (QualityMetricsService)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    public InvoiceTimelinessDto getInvoiceTimeliness(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return internalFinanceDataService.getInvoiceTimeliness(companyId);
    }

    @GetMapping("/financial-totals")
    @Operation(summary = "Agrégats CA/coût/marge (MultiBranchService)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    public FinancialTotalsDto getFinancialTotals(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return internalFinanceDataService.getFinancialTotals(companyId);
    }

    @GetMapping("/carbon-totals")
    @Operation(summary = "Agrégats carbone (MultiBranchService, MobileDashboardService)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    public CarbonTotalsDto getCarbonTotals(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return internalFinanceDataService.getCarbonTotals(companyId);
    }

    @GetMapping("/fx-exposure-report")
    @Operation(summary = "Rapport d'exposition de change (CurrencyController)",
            description = "Appel machine-à-machine, authentifié par X-Internal-Integration-Key")
    public Map<String, Object> getFxExposureReport(
            @RequestHeader(value = "X-Internal-Integration-Key", required = false) String apiKey) {
        UUID companyId = internalIntegrationService.resolveCompanyId(apiKey);
        return financialReportingService.getFxExposureReport(companyId);
    }
}
