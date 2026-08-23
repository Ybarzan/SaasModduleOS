package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.dto.shipment.SimulationResult;
import com.incokalk.model.Incoterm;
import com.incokalk.model.Simulation;
import com.incokalk.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/simulate")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Calcul des coûts Incoterms 2020")
public class SimulationController {

    private final SimulationService service;

    @PostMapping
    @Operation(summary = "Simuler le coût total pour un Incoterm")
    public ResponseEntity<SimulationResult> simulate(
            @Valid @RequestBody SimulationRequest req,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        // ⚡ La simulation est automatiquement sauvegardée dans cette méthode
        return ResponseEntity.ok(service.simulate(req, userId));
    }

    @PostMapping("/compare")
    @Operation(summary = "Comparer tous les Incoterms compatibles")
    public ResponseEntity<List<SimulationResult.IncotermComparison>> compareAll(
            @Valid @RequestBody SimulationRequest req,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(service.compareAll(req, userId));
    }

    // ❌ SUPPRIMÉ - La sauvegarde est maintenant automatique dans /simulate

    @GetMapping("/simulations")
    @Operation(summary = "Historique des simulations de l'utilisateur")
    public ResponseEntity<?> getUserSimulations(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        if (page != null && size != null && size > 0) {
            Page<Simulation> result = service.getUserSimulations(userId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(service.getUserSimulations(userId));
    }

    @GetMapping("/simulations/{id}")
    @Operation(summary = "Obtenir une simulation par ID")
    public ResponseEntity<Simulation> getSimulation(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        return ResponseEntity.ok(service.getSimulation(id, userId));
    }

    @DeleteMapping("/simulations/{id}")
    @Operation(summary = "Supprimer une simulation")
    public ResponseEntity<Void> deleteSimulation(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        service.deleteSimulation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/incoterms")
    @Operation(summary = "Liste des 11 Incoterms 2020 (public)")
    public ResponseEntity<List<Map<String, Object>>> listIncoterms() {
        return ResponseEntity.ok(Arrays.stream(Incoterm.values()).map(it ->
            Map.<String, Object>of(
                "id", it.name(),
                "code", it.name(),
                "fullName", it.fullName,
                "transportMode", it.mode.name(),
                "buyerRiskScore", it.buyerRiskScore
            )
        ).toList());
    }

    @GetMapping("/incoterms/{code}")
    @Operation(summary = "Détail d'un Incoterm (public)")
    public ResponseEntity<Map<String, Object>> getIncoterm(@PathVariable String code) {
        try {
            Incoterm it = Incoterm.valueOf(code.toUpperCase());
            return ResponseEntity.ok(Map.of(
                "id", it.name(),
                "code", it.name(), "fullName", it.fullName,
                "transportMode", it.mode.name(), "buyerRiskScore", it.buyerRiskScore
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
} 