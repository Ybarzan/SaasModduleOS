package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.CarrierDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CarrierService;
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
@RequestMapping("/v1/carriers")
@RequiredArgsConstructor
@Tag(name = "Carriers", description = "Gestion des transporteurs")
@RequiresPlan(Company.Plan.STARTER)
public class CarrierController {

    private final CarrierService carrierService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les transporteurs de l'entreprise")
    public ResponseEntity<?> listCarriers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        if (page != null && size != null && size > 0) {
            Page<Carrier> result = carrierService.listCarriers(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(carrierService.listCarriers(companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer un transporteur")
    public ResponseEntity<Carrier> createCarrier(
            @Valid @RequestBody CarrierDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(carrierService.createCarrier(dto, companyId));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour un transporteur")
    public ResponseEntity<Carrier> updateCarrier(
            @PathVariable UUID id,
            @Valid @RequestBody CarrierDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(carrierService.updateCarrier(id, dto, companyId));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un transporteur")
    public ResponseEntity<Void> deleteCarrier(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        carrierService.deleteCarrier(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Activer/Désactiver un transporteur")
    public ResponseEntity<Carrier> toggleActive(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(carrierService.toggleActive(id, companyId));
    }
}
