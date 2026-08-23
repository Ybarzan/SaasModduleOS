package com.incokalk.controller.analytics;

import com.incokalk.dto.analytics.ChartDataDTO;
import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.dto.financial.CostByCarrierDTO;
import com.incokalk.dto.financial.CostByModeDTO;
import com.incokalk.dto.financial.CostTrendDTO;
import com.incokalk.dto.financial.IncotermUsageDTO;
import com.incokalk.dto.shipment.CarrierPerformanceDTO;
import com.incokalk.dto.shipment.ShipmentByStatusDTO;
import com.incokalk.dto.shipment.ShipmentsOverTimeDTO;
import com.incokalk.dto.shipment.TopRouteDTO;
import com.incokalk.service.AnalyticsService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/shipments-over-time")
    @Operation(summary = "Expéditions dans le temps")
    public ResponseEntity<List<ShipmentsOverTimeDTO>> getShipmentsOverTime(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(defaultValue = "day") String granularity) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getShipmentsOverTime(companyId, period, granularity));
    }

    @GetMapping("/shipments-by-status")
    @Operation(summary = "Répartition des expéditions par statut")
    public ResponseEntity<List<ShipmentByStatusDTO>> getShipmentsByStatus() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getShipmentsByStatus(companyId));
    }

    @GetMapping("/cost-by-carrier")
    @Operation(summary = "Coûts par transporteur")
    public ResponseEntity<List<CostByCarrierDTO>> getCostByCarrier() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getCostByCarrier(companyId));
    }

    @GetMapping("/cost-by-mode")
    @Operation(summary = "Coûts par mode de transport")
    public ResponseEntity<List<CostByModeDTO>> getCostByMode() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getCostByMode(companyId));
    }

    @GetMapping("/top-routes")
    @Operation(summary = "Routes les plus fréquentées")
    public ResponseEntity<List<TopRouteDTO>> getTopRoutes(
            @RequestParam(defaultValue = "5") int limit) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getTopRoutes(companyId, limit));
    }

    @GetMapping("/incoterm-usage")
    @Operation(summary = "Utilisation des Incoterms")
    public ResponseEntity<List<IncotermUsageDTO>> getIncotermUsage() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getIncotermUsage(companyId));
    }

    @GetMapping("/weight-distribution")
    @Operation(summary = "Distribution des poids")
    public ResponseEntity<ChartDataDTO> getWeightDistribution() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getWeightDistribution(companyId));
    }

    @GetMapping("/volume-distribution")
    @Operation(summary = "Distribution des volumes")
    public ResponseEntity<ChartDataDTO> getVolumeDistribution() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getVolumeDistribution(companyId));
    }

    @GetMapping("/cost-trends")
    @Operation(summary = "Tendances des coûts dans le temps")
    public ResponseEntity<List<CostTrendDTO>> getCostTrends(
            @RequestParam(defaultValue = "90d") String period,
            @RequestParam(defaultValue = "month") String granularity) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getCostTrends(companyId, period, granularity));
    }

    @GetMapping("/carrier-performance")
    @Operation(summary = "Performance par transporteur")
    public ResponseEntity<List<CarrierPerformanceDTO>> getCarrierPerformance() {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(analyticsService.getCarrierPerformance(companyId));
    }
}
