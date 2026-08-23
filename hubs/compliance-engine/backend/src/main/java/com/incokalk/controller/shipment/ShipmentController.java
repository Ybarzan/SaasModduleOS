package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.ShipmentOrderDTO;
import com.incokalk.dto.shipment.ShipmentStatusUpdateDTO;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ShipmentItem;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ShipmentService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Gestion des commandes d'expédition")
@RequiresPlan(Company.Plan.STARTER)
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les expéditions de l'entreprise")
    public ResponseEntity<?> listShipments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        if (page != null && size != null && size > 0) {
            Page<ShipmentOrder> result = shipmentService.listShipments(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(shipmentService.listShipments(companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une commande d'expédition")
    public ResponseEntity<ShipmentOrder> createShipment(
            @Valid @RequestBody ShipmentOrderDTO dto,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.createShipment(dto, userId, companyId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une expédition avec suivi")
    public ResponseEntity<ShipmentOrder> getShipment(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.getShipmentWithTracking(id, companyId));
    }

    @PatchMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour le statut + ajouter un événement de suivi")
    public ResponseEntity<ShipmentOrder> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentStatusUpdateDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.updateStatus(id, dto, companyId));
    }

    @PatchMapping("/{id}/client")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rattacher (ou détacher) une expédition à un compte client du portail")
    public ResponseEntity<ShipmentOrder> assignClient(
            @PathVariable UUID id,
            @RequestBody AssignClientReq req,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.assignClient(id, req.clientId(), companyId));
    }

    public record AssignClientReq(UUID clientId) {}

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une expédition")
    public ResponseEntity<Void> deleteShipment(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        shipmentService.deleteShipment(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Lister les articles d'une expédition")
    public ResponseEntity<List<ShipmentItem>> listShipmentItems(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.getShipmentItems(id, companyId));
    }

    @PostMapping("/{id}/items")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Ajouter un article à une expédition")
    public ResponseEntity<ShipmentItem> addShipmentItem(
            @PathVariable UUID id,
            @Valid @RequestBody ShipmentOrderDTO.ShipmentOrderItemDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentService.addShipmentItem(id, dto, companyId));
    }

    @DeleteMapping("/{id}/items")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer tous les articles d'une expédition")
    public ResponseEntity<Void> deleteShipmentItems(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        shipmentService.deleteShipmentItems(id, companyId);
        return ResponseEntity.noContent().build();
    }
}
