package com.incokalk.controller.financial;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.LandedCost;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.LandedCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/landed-costs")
@RequiredArgsConstructor
@Tag(name = "Landed Cost Calculator", description = "Calcul du coût complet débarqué")
public class LandedCostController {

    private final LandedCostService landedCostService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Lister les coûts débarqués")
    public ResponseEntity<List<LandedCost>> list() {
        return ResponseEntity.ok(landedCostService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Obtenir un coût débarqué par ID")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(landedCostService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/calculate")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Calculer et sauvegarder un coût débarqué")
    public ResponseEntity<?> calculate(@Valid @RequestBody CreateLandedCost req) {
        try {
            LandedCost cost = LandedCost.builder()
                .calculationName(req.calculationName())
                .originCountry(req.originCountry())
                .destinationCountry(req.destinationCountry())
                .incoterm(req.incoterm() != null ? req.incoterm() : "FOB")
                .hsCode(req.hsCode())
                .transportMode(req.transportMode() != null ? req.transportMode() : "SEA")
                .productValue(req.productValue())
                .currency(req.currency() != null ? req.currency() : "EUR")
                .freightCost(req.freightCost() != null ? req.freightCost() : BigDecimal.ZERO)
                .insuranceCost(req.insuranceCost() != null ? req.insuranceCost() : BigDecimal.ZERO)
                .portCharges(req.portCharges() != null ? req.portCharges() : BigDecimal.ZERO)
                .customsFees(req.customsFees() != null ? req.customsFees() : BigDecimal.ZERO)
                .handlingFees(req.handlingFees() != null ? req.handlingFees() : BigDecimal.ZERO)
                .lastMileCost(req.lastMileCost() != null ? req.lastMileCost() : BigDecimal.ZERO)
                .unitCount(req.unitCount() != null ? req.unitCount() : 1)
                .sellingPrice(req.sellingPrice())
                .notes(req.notes())
                .build();

            LandedCost saved = landedCostService.calculate(cost);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Mettre à jour un coût débarqué")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody CreateLandedCost req) {
        try {
            LandedCost cost = LandedCost.builder()
                .calculationName(req.calculationName())
                .originCountry(req.originCountry())
                .destinationCountry(req.destinationCountry())
                .incoterm(req.incoterm() != null ? req.incoterm() : "FOB")
                .hsCode(req.hsCode())
                .transportMode(req.transportMode() != null ? req.transportMode() : "SEA")
                .productValue(req.productValue())
                .currency(req.currency() != null ? req.currency() : "EUR")
                .freightCost(req.freightCost() != null ? req.freightCost() : BigDecimal.ZERO)
                .insuranceCost(req.insuranceCost() != null ? req.insuranceCost() : BigDecimal.ZERO)
                .portCharges(req.portCharges() != null ? req.portCharges() : BigDecimal.ZERO)
                .customsFees(req.customsFees() != null ? req.customsFees() : BigDecimal.ZERO)
                .handlingFees(req.handlingFees() != null ? req.handlingFees() : BigDecimal.ZERO)
                .lastMileCost(req.lastMileCost() != null ? req.lastMileCost() : BigDecimal.ZERO)
                .unitCount(req.unitCount() != null ? req.unitCount() : 1)
                .sellingPrice(req.sellingPrice())
                .notes(req.notes())
                .build();

            return ResponseEntity.ok(landedCostService.update(id, cost));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Supprimer un coût débarqué")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            landedCostService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Coût débarqué supprimé"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/from-shipment/{shipmentId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Créer un coût débarqué depuis un shipment")
    public ResponseEntity<?> createFromShipment(@PathVariable UUID shipmentId) {
        try {
            LandedCost saved = landedCostService.createFromShipment(shipmentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/what-if")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Comparer des scénarios what-if (sans sauvegarder)")
    public ResponseEntity<List<Map<String, Object>>> whatIf(@Valid @RequestBody List<CreateLandedCost> scenarios) {
        List<LandedCost> built = scenarios.stream().map(req -> LandedCost.builder()
            .originCountry(req.originCountry())
            .destinationCountry(req.destinationCountry())
            .incoterm(req.incoterm() != null ? req.incoterm() : "FOB")
            .hsCode(req.hsCode())
            .transportMode(req.transportMode() != null ? req.transportMode() : "SEA")
            .productValue(req.productValue())
            .currency(req.currency() != null ? req.currency() : "EUR")
            .freightCost(req.freightCost() != null ? req.freightCost() : BigDecimal.ZERO)
            .insuranceCost(req.insuranceCost() != null ? req.insuranceCost() : BigDecimal.ZERO)
            .portCharges(req.portCharges() != null ? req.portCharges() : BigDecimal.ZERO)
            .customsFees(req.customsFees() != null ? req.customsFees() : BigDecimal.ZERO)
            .handlingFees(req.handlingFees() != null ? req.handlingFees() : BigDecimal.ZERO)
            .lastMileCost(req.lastMileCost() != null ? req.lastMileCost() : BigDecimal.ZERO)
            .unitCount(req.unitCount() != null ? req.unitCount() : 1)
            .build()).toList();
        return ResponseEntity.ok(landedCostService.compareScenarios(built));
    }

    @PostMapping("/{id}/share")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Générer un lien de partage public")
    public ResponseEntity<?> share(@PathVariable UUID id) {
        try {
            String token = landedCostService.generateShareToken(id);
            return ResponseEntity.ok(Map.of("shareUrl", "/s/landed-cost/" + token, "token", token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/public/{token}")
    @Operation(summary = "Consulter un coût débarqué via lien public (sans auth)")
    public ResponseEntity<?> getPublic(@PathVariable String token) {
        try {
            return ResponseEntity.ok(landedCostService.getByShareToken(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @RequiresPlan(Company.Plan.STARTER)
    @Operation(summary = "Statistiques des coûts débarqués")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(landedCostService.getStats());
    }

    record CreateLandedCost(
        String calculationName,
        @jakarta.validation.constraints.NotBlank String originCountry,
        @jakarta.validation.constraints.NotBlank String destinationCountry,
        String incoterm,
        String hsCode,
        String transportMode,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal productValue,
        String currency,
        BigDecimal freightCost,
        BigDecimal insuranceCost,
        BigDecimal portCharges,
        BigDecimal customsFees,
        BigDecimal handlingFees,
        BigDecimal lastMileCost,
        Integer unitCount,
        BigDecimal sellingPrice,
        String notes
    ) {}
}
