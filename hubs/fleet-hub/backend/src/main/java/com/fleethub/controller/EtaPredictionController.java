package com.fleethub.controller;

import com.fleethub.model.EtaPrediction;
import com.fleethub.service.EtaPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prédiction ETA — porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/eta-predictions")
@RequiredArgsConstructor
@Tag(name = "ETA Predictions", description = "Prédiction du temps d'arrivée")
public class EtaPredictionController {

    private final EtaPredictionService etaPredictionService;

    record PredictRequest(String origin, String destination, String mode, String carrierName, String shipmentId) {}

    record ActualArrivalUpdate(@NotNull String actualArrival) {}

    @GetMapping
    @Operation(summary = "Lister les prédictions ETA")
    public ResponseEntity<List<EtaPrediction>> listAll() {
        return ResponseEntity.ok(etaPredictionService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une prédiction ETA")
    public ResponseEntity<EtaPrediction> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(etaPredictionService.getById(id));
    }

    @PostMapping("/predict")
    @Operation(summary = "Prédire l'ETA d'un envoi")
    public ResponseEntity<EtaPrediction> predict(@Valid @RequestBody PredictRequest request) {
        EtaPrediction prediction = EtaPrediction.builder()
                .origin(request.origin())
                .destination(request.destination())
                .mode(request.mode())
                .carrierName(request.carrierName())
                .build();
        UUID shipmentId = request.shipmentId() != null && !request.shipmentId().isBlank()
                ? UUID.fromString(request.shipmentId())
                : null;
        return ResponseEntity.ok(etaPredictionService.predict(prediction, shipmentId));
    }

    @PutMapping("/{id}/actual")
    @Operation(summary = "Mettre à jour l'arrivée réelle")
    public ResponseEntity<EtaPrediction> updateActual(
            @PathVariable UUID id,
            @Valid @RequestBody ActualArrivalUpdate update) {
        LocalDateTime actualArrival = LocalDateTime.parse(update.actualArrival());
        return ResponseEntity.ok(etaPredictionService.updateActual(id, actualArrival));
    }

    @GetMapping("/by-lane")
    @Operation(summary = "Prédictions par lane (origine → destination)")
    public ResponseEntity<List<EtaPrediction>> getByLane(
            @RequestParam String origin,
            @RequestParam String destination) {
        return ResponseEntity.ok(etaPredictionService.getByLane(origin, destination));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques des prédictions ETA")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(etaPredictionService.getStats());
    }
}
