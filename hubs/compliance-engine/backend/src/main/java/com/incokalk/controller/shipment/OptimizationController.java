package com.incokalk.controller.shipment;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.OptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/optimization")
@RequiredArgsConstructor
@Tag(name = "Optimization", description = "Moteur d'optimisation tarifaire P3")
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping("/analyze-routes")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lancer une analyse des routes")
    public ResponseEntity<Void> analyzeRoutes() {
        optimizationService.analyzeRoutes();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/predict")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Prédire un tarif pour une lane donnée")
    public ResponseEntity<OptimizationService.PredictResult> predict(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Double weight,
            @RequestParam(required = false) Double volume) {
        return ResponseEntity.ok(optimizationService.predict(origin, destination, mode, weight, volume));
    }

    @GetMapping("/recommendations")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Liste des recommandations d'optimisation")
    public ResponseEntity<List<OptimizationService.RateOptimizationDTO>> getRecommendations() {
        return ResponseEntity.ok(optimizationService.getRecommendations());
    }

    @GetMapping("/lane-analysis")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Analyse des lanes")
    public ResponseEntity<List<OptimizationService.LaneAnalysisDTO>> getLaneAnalysis() {
        return ResponseEntity.ok(optimizationService.getLaneAnalysis());
    }

    @PostMapping("/consolidation")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rechercher des opportunités de consolidation")
    public ResponseEntity<Void> findConsolidation() {
        optimizationService.findConsolidation();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/consolidation")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Liste des opportunités de consolidation")
    public ResponseEntity<List<OptimizationService.ConsolidationDTO>> getConsolidations() {
        return ResponseEntity.ok(optimizationService.getConsolidations());
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Statistiques d'optimisation")
    public ResponseEntity<OptimizationService.OptimizationStats> getStats() {
        return ResponseEntity.ok(optimizationService.getStats());
    }

    @PatchMapping("/accept/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Accepter une recommandation")
    public ResponseEntity<?> acceptRecommendation(@PathVariable String id) {
        boolean found = optimizationService.acceptRecommendation(id);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PatchMapping("/consolidation/accept/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Accepter une consolidation")
    public ResponseEntity<?> acceptConsolidation(@PathVariable String id) {
        boolean found = optimizationService.acceptConsolidation(id);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
