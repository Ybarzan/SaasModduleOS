package com.fleethub.controller;

import com.fleethub.service.OptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Moteur d'optimisation tarifaire — porté tel quel depuis compliance-engine.
 * Démo/analytique en mémoire (données factices générées à chaque appel de
 * analyze-routes), ne persiste rien en base : utile pour une démo produit,
 * pas une vraie recommandation tarifaire. Porté sans investissement
 * supplémentaire, comme demandé.
 */
@RestController
@RequestMapping("/api/optimization")
@RequiredArgsConstructor
@Tag(name = "Optimization", description = "Moteur d'optimisation tarifaire (démo)")
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping("/analyze-routes")
    @Operation(summary = "Lancer une analyse des routes")
    public ResponseEntity<Void> analyzeRoutes() {
        optimizationService.analyzeRoutes();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/predict")
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
    @Operation(summary = "Liste des recommandations d'optimisation")
    public ResponseEntity<List<OptimizationService.RateOptimizationDTO>> getRecommendations() {
        return ResponseEntity.ok(optimizationService.getRecommendations());
    }

    @GetMapping("/lane-analysis")
    @Operation(summary = "Analyse des lanes")
    public ResponseEntity<List<OptimizationService.LaneAnalysisDTO>> getLaneAnalysis() {
        return ResponseEntity.ok(optimizationService.getLaneAnalysis());
    }

    @PostMapping("/consolidation")
    @Operation(summary = "Rechercher des opportunités de consolidation")
    public ResponseEntity<Void> findConsolidation() {
        optimizationService.findConsolidation();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/consolidation")
    @Operation(summary = "Liste des opportunités de consolidation")
    public ResponseEntity<List<OptimizationService.ConsolidationDTO>> getConsolidations() {
        return ResponseEntity.ok(optimizationService.getConsolidations());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques d'optimisation")
    public ResponseEntity<OptimizationService.OptimizationStats> getStats() {
        return ResponseEntity.ok(optimizationService.getStats());
    }

    @PatchMapping("/accept/{id}")
    @Operation(summary = "Accepter une recommandation")
    public ResponseEntity<?> acceptRecommendation(@PathVariable String id) {
        boolean found = optimizationService.acceptRecommendation(id);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PatchMapping("/consolidation/accept/{id}")
    @Operation(summary = "Accepter une consolidation")
    public ResponseEntity<?> acceptConsolidation(@PathVariable String id) {
        boolean found = optimizationService.acceptConsolidation(id);
        return found ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
