package com.fleethub.controller;

import com.fleethub.dto.ShippingRateDTO;
import com.fleethub.model.ShippingRate;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ShippingRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Tarifs de transport — porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/shipping-rates")
@RequiredArgsConstructor
@Tag(name = "Shipping Rates", description = "Gestion des tarifs de transport")
public class ShippingRateController {

    private final ShippingRateService shippingRateService;

    @GetMapping
    @Operation(summary = "Lister tous les tarifs de l'entreprise")
    public ResponseEntity<?> listRates(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long companyId = TenantContext.companyId();
        if (page != null && size != null && size > 0) {
            Page<ShippingRate> result = shippingRateService.listRates(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(shippingRateService.listRates(companyId));
    }

    @GetMapping("/carrier/{carrierId}")
    @Operation(summary = "Lister les tarifs d'un transporteur")
    public ResponseEntity<List<ShippingRate>> listRatesByCarrier(@PathVariable UUID carrierId) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shippingRateService.listRatesByCarrier(carrierId, companyId));
    }

    @PostMapping
    @Operation(summary = "Créer un tarif de transport")
    public ResponseEntity<ShippingRate> createRate(@Valid @RequestBody ShippingRateDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shippingRateService.createRate(dto, companyId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un tarif de transport")
    public ResponseEntity<ShippingRate> updateRate(@PathVariable UUID id, @Valid @RequestBody ShippingRateDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shippingRateService.updateRate(id, dto, companyId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un tarif de transport")
    public ResponseEntity<Void> deleteRate(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        shippingRateService.deleteRate(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activer/Désactiver un tarif")
    public ResponseEntity<ShippingRate> toggleActive(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shippingRateService.toggleActive(id, companyId));
    }

    @GetMapping("/compare")
    @Operation(summary = "Comparer les tarifs pour un trajet donné")
    public ResponseEntity<List<ShippingRate>> compareRates(
            @RequestParam String originCountry,
            @RequestParam String destinationCountry,
            @RequestParam String transportMode,
            @RequestParam(required = false) Double weightKg) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shippingRateService.findMatchingRates(
                companyId, originCountry, destinationCountry, transportMode, weightKg, java.time.LocalDateTime.now()));
    }
}
