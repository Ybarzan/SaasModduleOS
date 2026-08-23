package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.ShippingRateDTO;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ShippingRate;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ShippingRateService;
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
@RequestMapping("/v1/shipping-rates")
@RequiredArgsConstructor
@Tag(name = "Shipping Rates", description = "Gestion des tarifs de transport")
@RequiresPlan(Company.Plan.STARTER)
public class ShippingRateController {

    private final ShippingRateService shippingRateService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister tous les tarifs de l'entreprise")
    public ResponseEntity<?> listRates(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        if (page != null && size != null && size > 0) {
            Page<ShippingRate> result = shippingRateService.listRates(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(shippingRateService.listRates(companyId));
    }

    @GetMapping("/carrier/{carrierId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les tarifs d'un transporteur")
    public ResponseEntity<List<ShippingRate>> listRatesByCarrier(
            @PathVariable UUID carrierId,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shippingRateService.listRatesByCarrier(carrierId, companyId));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer un tarif de transport")
    public ResponseEntity<ShippingRate> createRate(
            @Valid @RequestBody ShippingRateDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shippingRateService.createRate(dto, companyId));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour un tarif de transport")
    public ResponseEntity<ShippingRate> updateRate(
            @PathVariable UUID id,
            @Valid @RequestBody ShippingRateDTO dto,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shippingRateService.updateRate(id, dto, companyId));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un tarif de transport")
    public ResponseEntity<Void> deleteRate(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        shippingRateService.deleteRate(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Activer/Désactiver un tarif")
    public ResponseEntity<ShippingRate> toggleActive(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shippingRateService.toggleActive(id, companyId));
    }

    @GetMapping("/compare")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Comparer les tarifs pour un trajet donné")
    public ResponseEntity<List<ShippingRate>> compareRates(
            @RequestParam String originCountry,
            @RequestParam String destinationCountry,
            @RequestParam String transportMode,
            @RequestParam(required = false) Double weightKg,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(shippingRateService.findMatchingRates(
                companyId, originCountry, destinationCountry, transportMode, weightKg, java.time.LocalDateTime.now()));
    }
}
