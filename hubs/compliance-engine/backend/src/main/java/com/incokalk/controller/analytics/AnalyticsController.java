package com.incokalk.controller.analytics;

import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.service.AnalyticsService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * shipments-over-time/shipments-by-status/cost-by-carrier/cost-by-mode/top-routes/
 * incoterm-usage/weight-distribution/volume-distribution/cost-trends/carrier-performance
 * ont été retirés avec le domaine Transport (voir AnalyticsService). Seul
 * /dashboard subsiste, réduit à ses deux champs indépendants du domaine
 * Transport (totalSimulations/simulationsThisMonth).
 */
@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Tableau de bord et statistiques")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "KPIs principaux du tableau de bord")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats(
            @RequestParam(defaultValue = "30d") String period) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getDashboardStats(companyId, period));
    }
}
