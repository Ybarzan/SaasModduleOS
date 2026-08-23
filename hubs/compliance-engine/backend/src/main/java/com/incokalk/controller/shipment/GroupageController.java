package com.incokalk.controller.shipment;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.Groupage;
import com.incokalk.model.GroupageMember;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.GroupageService;
import com.incokalk.tenant.TenantContext;
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

@RestController
@RequestMapping("/v1/groupages")
@RequiredArgsConstructor
@Tag(name = "Groupage", description = "Consolidation multi-exportateurs / co-loading")
@RequiresPlan(Company.Plan.STARTER)
public class GroupageController {

    private final GroupageService groupageService;

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer un groupage")
    public ResponseEntity<Groupage> create(@Valid @RequestBody CreateGroupageRequest body) {
        return ResponseEntity.ok(groupageService.create(
            TenantContext.get(), body.getName(), body.getTransportMode(), body.getCarrierName(),
            body.getOrigin(), body.getDestination(), body.getCapacityWeightKg(),
            body.getCapacityVolumeM3(), body.getPlannedDeparture(), body.getPlannedArrival()));
    }

    @GetMapping
    @Operation(summary = "Lister les groupages de l'entreprise")
    public ResponseEntity<List<Groupage>> list() {
        return ResponseEntity.ok(groupageService.list(TenantContext.get()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un groupage (membres et taux d'utilisation)")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(groupageService.getDetail(id, TenantContext.get()));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Modifier un groupage")
    public ResponseEntity<Groupage> update(@PathVariable UUID id, @Valid @RequestBody UpdateGroupageRequest body) {
        return ResponseEntity.ok(groupageService.update(
            id, TenantContext.get(), body.getName(), body.getTransportMode(), body.getCarrierName(),
            body.getOrigin(), body.getDestination(), body.getCapacityWeightKg(),
            body.getCapacityVolumeM3(), body.getPlannedDeparture(), body.getPlannedArrival()));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Supprimer un groupage (statuts non engagés uniquement)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupageService.delete(id, TenantContext.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Faire avancer le statut d'un groupage")
    public ResponseEntity<Groupage> updateStatus(@PathVariable UUID id, @RequestBody StatusRequest body) {
        return ResponseEntity.ok(groupageService.updateStatus(id, TenantContext.get(), body.getStatus()));
    }

    @PostMapping("/{id}/members")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Ajouter une expédition (ou chargement externe) au groupage")
    public ResponseEntity<GroupageMember> addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest body) {
        return ResponseEntity.ok(groupageService.addMember(
            id, TenantContext.get(), body.getShipmentOrderId(), body.getExternalCompany(),
            body.getReference(), body.getWeightKg(), body.getVolumeM3()));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Retirer une expédition du groupage")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        groupageService.removeMember(id, TenantContext.get(), memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques des groupages")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(groupageService.stats(TenantContext.get()));
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
