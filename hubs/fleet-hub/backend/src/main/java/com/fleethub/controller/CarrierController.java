package com.fleethub.controller;

import com.fleethub.dto.CarrierDTO;
import com.fleethub.model.Carrier;
import com.fleethub.security.TenantContext;
import com.fleethub.service.CarrierService;
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

/**
 * Gestion des transporteurs tiers (carriers) — porté depuis compliance-engine.
 * Gating : lecture/écriture réservées au staff (ADMIN/GESTIONNAIRE, règle
 * générique de SecurityConfig) ; suppression réservée à ADMIN (règle dédiée
 * DELETE /api/carriers/**, l'original la réservait à OWNER/ADMIN).
 */
@RestController
@RequestMapping("/api/carriers")
@RequiredArgsConstructor
@Tag(name = "Carriers", description = "Gestion des transporteurs")
public class CarrierController {

    private final CarrierService carrierService;

    @GetMapping
    @Operation(summary = "Lister les transporteurs de l'entreprise")
    public ResponseEntity<?> listCarriers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long companyId = TenantContext.companyId();
        if (page != null && size != null && size > 0) {
            Page<Carrier> result = carrierService.listCarriers(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(carrierService.listCarriers(companyId));
    }

    @PostMapping
    @Operation(summary = "Créer un transporteur")
    public ResponseEntity<Carrier> createCarrier(@Valid @RequestBody CarrierDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(carrierService.createCarrier(dto, companyId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un transporteur")
    public ResponseEntity<Carrier> updateCarrier(@PathVariable UUID id, @Valid @RequestBody CarrierDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(carrierService.updateCarrier(id, dto, companyId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un transporteur")
    public ResponseEntity<Void> deleteCarrier(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        carrierService.deleteCarrier(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activer/Désactiver un transporteur")
    public ResponseEntity<Carrier> toggleActive(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(carrierService.toggleActive(id, companyId));
    }
}
