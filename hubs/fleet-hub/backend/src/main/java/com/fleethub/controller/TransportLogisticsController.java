package com.fleethub.controller;

import com.fleethub.dto.PackagingRequest;
import com.fleethub.dto.PackagingResult;
import com.fleethub.dto.RouteOptimizationRequest;
import com.fleethub.dto.RouteOptimizationResult;
import com.fleethub.dto.TruckingRateRequest;
import com.fleethub.dto.TruckingRateResult;
import com.fleethub.service.PackagingService;
import com.fleethub.service.RouteOptimizationService;
import com.fleethub.service.TruckingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Packaging, cubage et optimisation d'itinéraire — porté depuis compliance-engine
 * (LogisticsController). Seuls {@code /packaging}, {@code /trucking} et
 * {@code /route-optimization} sont repris ici : {@code /customs-duty} et
 * {@code /insurance} restent dans compliance-engine (CustomsDutyService /
 * CargoInsuranceService ne font pas partie de ce domaine).
 */
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
@Tag(name = "Logistics", description = "Packaging, cubage et optimisation d'itinéraire")
public class TransportLogisticsController {

    private final PackagingService packagingService;
    private final TruckingService truckingService;
    private final RouteOptimizationService routeOptimizationService;

    @PostMapping("/packaging")
    @Operation(summary = "Calculer le packaging optimal (first-fit decreasing)")
    public ResponseEntity<PackagingResult> calculatePackaging(@Valid @RequestBody PackagingRequest request) {
        return ResponseEntity.ok(packagingService.calculatePackaging(request));
    }

    @PostMapping("/trucking")
    @Operation(summary = "Obtenir les tarifs LTL/FTL/Express pour une expédition")
    public ResponseEntity<TruckingRateResult> calculateTruckingRates(@Valid @RequestBody TruckingRateRequest request) {
        return ResponseEntity.ok(truckingService.calculateRates(request));
    }

    @PostMapping("/route-optimization")
    @Operation(summary = "Optimiser un itinéraire routier multi-stops")
    public ResponseEntity<RouteOptimizationResult> optimizeRoute(@Valid @RequestBody RouteOptimizationRequest request) {
        return ResponseEntity.ok(routeOptimizationService.optimize(request));
    }
}
