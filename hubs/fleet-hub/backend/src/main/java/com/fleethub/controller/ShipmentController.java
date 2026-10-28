package com.fleethub.controller;

import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.dto.ShipmentStatusUpdateDTO;
import com.fleethub.model.ShipmentItem;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.security.AppUserPrincipal;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Commandes d'expédition — cœur du domaine Transport, porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Gestion des commandes d'expédition")
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    @Operation(summary = "Lister les expéditions de l'entreprise")
    public ResponseEntity<?> listShipments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long companyId = TenantContext.companyId();
        if (page != null && size != null && size > 0) {
            Page<ShipmentOrder> result = shipmentService.listShipments(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(shipmentService.listShipments(companyId));
    }

    @PostMapping
    @Operation(summary = "Créer une commande d'expédition")
    public ResponseEntity<ShipmentOrder> createShipment(@Valid @RequestBody ShipmentOrderDTO dto) {
        Long userId = principal().getId();
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.createShipment(dto, userId, companyId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une expédition avec suivi")
    public ResponseEntity<ShipmentOrder> getShipment(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.getShipmentWithTracking(id, companyId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mettre à jour le statut + ajouter un événement de suivi")
    public ResponseEntity<ShipmentOrder> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentStatusUpdateDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.updateStatus(id, dto, companyId));
    }

    @PatchMapping("/{id}/client")
    @Operation(summary = "Rattacher (ou détacher) une expédition à un compte client du portail")
    public ResponseEntity<ShipmentOrder> assignClient(
            @PathVariable UUID id,
            @RequestBody AssignClientReq req) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.assignClient(id, req.clientId(), companyId));
    }

    public record AssignClientReq(UUID clientId) {}

    @PatchMapping("/{id}/vessel")
    @Operation(summary = "Lier (ou délier) une expédition à un navire suivi par MMSI")
    public ResponseEntity<ShipmentOrder> assignVessel(
            @PathVariable UUID id,
            @RequestBody AssignVesselReq req) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.assignVessel(id, req.mmsi(), companyId));
    }

    public record AssignVesselReq(String mmsi) {}

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une expédition")
    public ResponseEntity<Void> deleteShipment(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        shipmentService.deleteShipment(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Lister les articles d'une expédition")
    public ResponseEntity<List<ShipmentItem>> listShipmentItems(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.getShipmentItems(id, companyId));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Ajouter un article à une expédition")
    public ResponseEntity<ShipmentItem> addShipmentItem(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentOrderDTO.ShipmentOrderItemDTO dto) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(shipmentService.addShipmentItem(id, dto, companyId));
    }

    @DeleteMapping("/{id}/items")
    @Operation(summary = "Supprimer tous les articles d'une expédition")
    public ResponseEntity<Void> deleteShipmentItems(@PathVariable UUID id) {
        Long companyId = TenantContext.companyId();
        shipmentService.deleteShipmentItems(id, companyId);
        return ResponseEntity.noContent().build();
    }

    private AppUserPrincipal principal() {
        return (AppUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
