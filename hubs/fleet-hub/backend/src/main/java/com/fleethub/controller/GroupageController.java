package com.fleethub.controller;

import com.fleethub.model.Groupage;
import com.fleethub.model.GroupageMember;
import com.fleethub.security.TenantContext;
import com.fleethub.service.GroupageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Groupage / consolidation multi-exportateurs (co-loading) — porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/groupages")
@RequiredArgsConstructor
@Tag(name = "Groupage", description = "Consolidation multi-exportateurs / co-loading")
public class GroupageController {

    private final GroupageService groupageService;

    @PostMapping
    @Operation(summary = "Créer un groupage")
    public ResponseEntity<Groupage> create(@Valid @RequestBody CreateGroupageRequest body) {
        return ResponseEntity.ok(groupageService.create(
            TenantContext.companyId(), body.getName(), body.getTransportMode(), body.getCarrierName(),
            body.getOrigin(), body.getDestination(), body.getCapacityWeightKg(),
            body.getCapacityVolumeM3(), body.getPlannedDeparture(), body.getPlannedArrival()));
    }

    @GetMapping
    @Operation(summary = "Lister les groupages de l'entreprise")
    public ResponseEntity<List<Groupage>> list() {
        return ResponseEntity.ok(groupageService.list(TenantContext.companyId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un groupage (membres et taux d'utilisation)")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(groupageService.getDetail(id, TenantContext.companyId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un groupage")
    public ResponseEntity<Groupage> update(@PathVariable UUID id, @Valid @RequestBody UpdateGroupageRequest body) {
        return ResponseEntity.ok(groupageService.update(
            id, TenantContext.companyId(), body.getName(), body.getTransportMode(), body.getCarrierName(),
            body.getOrigin(), body.getDestination(), body.getCapacityWeightKg(),
            body.getCapacityVolumeM3(), body.getPlannedDeparture(), body.getPlannedArrival()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un groupage (statuts non engagés uniquement)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupageService.delete(id, TenantContext.companyId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Faire avancer le statut d'un groupage")
    public ResponseEntity<Groupage> updateStatus(@PathVariable UUID id, @RequestBody StatusRequest body) {
        return ResponseEntity.ok(groupageService.updateStatus(id, TenantContext.companyId(), body.getStatus()));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Ajouter une expédition (ou chargement externe) au groupage")
    public ResponseEntity<GroupageMember> addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest body) {
        return ResponseEntity.ok(groupageService.addMember(
            id, TenantContext.companyId(), body.getShipmentOrderId(), body.getExternalCompany(),
            body.getReference(), body.getWeightKg(), body.getVolumeM3()));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @Operation(summary = "Retirer une expédition du groupage")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        groupageService.removeMember(id, TenantContext.companyId(), memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques des groupages")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(groupageService.stats(TenantContext.companyId()));
    }

    @Data
    public static class CreateGroupageRequest {
        @NotBlank
        private String name;
        private String transportMode;
        private String carrierName;
        private String origin;
        private String destination;
        @Positive
        private BigDecimal capacityWeightKg;
        @Positive
        private BigDecimal capacityVolumeM3;
        private LocalDate plannedDeparture;
        private LocalDate plannedArrival;
    }

    @Data
    public static class UpdateGroupageRequest {
        private String name;
        private String transportMode;
        private String carrierName;
        private String origin;
        private String destination;
        @Positive
        private BigDecimal capacityWeightKg;
        @Positive
        private BigDecimal capacityVolumeM3;
        private LocalDate plannedDeparture;
        private LocalDate plannedArrival;
    }

    @Data
    public static class StatusRequest {
        @NotNull
        private Groupage.Status status;
    }

    @Data
    public static class AddMemberRequest {
        private UUID shipmentOrderId;
        private String externalCompany;
        private String reference;
        @NotNull @DecimalMin("0")
        private BigDecimal weightKg;
        @NotNull @DecimalMin("0")
        private BigDecimal volumeM3;
    }
}
