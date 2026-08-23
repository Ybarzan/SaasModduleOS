package com.incokalk.controller.shipment;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.EtaPrediction;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.EtaPredictionService;
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

@RestController
@RequestMapping("/v1/eta-predictions")
@RequiredArgsConstructor
@Tag(name = "ETA Predictions", description = "Prédiction du temps d'arrivée")
public class EtaPredictionController {

    private final EtaPredictionService etaPredictionService;

    record PredictRequest(String origin, String destination, String mode, String carrierName, String shipmentId) {}

    record ActualArrivalUpdate(@NotNull String actualArrival) {}

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les prédictions ETA")
    public ResponseEntity<List<EtaPrediction>> listAll() {
        return ResponseEntity.ok(etaPredictionService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Détail d'une prédiction ETA")
    public ResponseEntity<EtaPrediction> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(etaPredictionService.getById(id));
    }

    @PostMapping("/predict")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Prédire l'ETA d'un envoi")
    public ResponseEntity<EtaPrediction> predict(@Valid @RequestBody PredictRequest request) {
        EtaPrediction prediction = EtaPrediction.builder()
                .origin(request.origin())
                .destination(request.destination())
                .mode(request.mode())
                .carrierName(request.carrierName())
                .build();
        return ResponseEntity.ok(etaPredictionService.predict(prediction));
    }

    @PutMapping("/{id}/actual")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour l'arrivée réelle")
    public ResponseEntity<EtaPrediction> updateActual(
            @PathVariable UUID id,
            @Valid @RequestBody ActualArrivalUpdate update) {
        LocalDateTime actualArrival = LocalDateTime.parse(update.actualArrival());
        return ResponseEntity.ok(etaPredictionService.updateActual(id, actualArrival));
    }

    @GetMapping("/by-lane")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Prédictions par lane (origine → destination)")
    public ResponseEntity<List<EtaPrediction>> getByLane(
            @RequestParam String origin,
            @RequestParam String destination) {
        return ResponseEntity.ok(etaPredictionService.getByLane(origin, destination));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques des prédictions ETA")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(etaPredictionService.getStats());
    }
}
