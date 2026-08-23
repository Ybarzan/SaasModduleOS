package com.incokalk.controller.financial;

import com.incokalk.dto.financial.CargoInsuranceRequest;
import com.incokalk.dto.financial.CargoInsuranceResult;
import com.incokalk.model.CargoInsuranceQuote;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CargoInsuranceService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insurance")
@RequiredArgsConstructor
@Tag(name = "Assurance cargo", description = "Devis et polices d'assurance cargo")
@RequiresPlan(Company.Plan.STARTER)
public class CargoInsuranceController {

    private final CargoInsuranceService cargoInsuranceService;

    @PostMapping("/quotes")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Calculer et enregistrer un devis d'assurance cargo")
    public ResponseEntity<CargoInsuranceQuote> saveQuote(@Valid @RequestBody CargoInsuranceRequest request) {
        CargoInsuranceResult result = cargoInsuranceService.calculate(request);
        return ResponseEntity.ok(cargoInsuranceService.saveQuote(request, result, TenantContext.get()));
    }

    @GetMapping("/quotes")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des devis d'assurance cargo")
    public ResponseEntity<List<CargoInsuranceQuote>> listQuotes() {
        return ResponseEntity.ok(cargoInsuranceService.listQuotes(TenantContext.get()));
    }

    @PostMapping("/quotes/{id}/policy")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Souscrire la police pour un devis")
    public ResponseEntity<CargoInsuranceQuote> activatePolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(cargoInsuranceService.activatePolicy(id, TenantContext.get()));
    }
}
