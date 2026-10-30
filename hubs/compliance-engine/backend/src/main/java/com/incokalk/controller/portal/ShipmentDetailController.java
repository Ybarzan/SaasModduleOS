package com.incokalk.controller.portal;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ShipmentDetailService;
import com.incokalk.service.integration.fleethub.ShipmentDetailDto;
import com.incokalk.service.integration.fleethub.ShipmentItemDetailDto;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lecture du détail d'une expédition fleet-hub par l'app mobile (restaure
 * GET /v1/shipments/{id} et .../items, retirés avec le domaine Transport --
 * voir mobile/src/pages/ShipmentDetail.tsx). Lecture seule : la mise à jour
 * de statut (PATCH .../status, appelée par mobile/src/pages/ShipmentDetail.tsx)
 * reste volontairement non restaurée -- changer le statut d'une expédition
 * fleet-hub depuis compliance-engine est une action d'écriture cross-hub qui
 * mérite sa propre décision (même discipline que la création externe,
 * ADR-0002), pas une extrapolation du chemin de lecture.
 */
@RestController
@RequestMapping("/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Expéditions (mobile)", description = "Détail d'une expédition fleet-hub, lecture seule")
public class ShipmentDetailController {

    private final ShipmentDetailService shipmentDetailService;

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Détail d'une expédition")
    public ResponseEntity<ShipmentDetailDto> getShipment(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentDetailService.getDetail(id, companyId));
    }

    @GetMapping("/{id}/items")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Articles d'une expédition")
    public ResponseEntity<List<ShipmentItemDetailDto>> getShipmentItems(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shipmentDetailService.getItems(id, companyId));
    }
}
