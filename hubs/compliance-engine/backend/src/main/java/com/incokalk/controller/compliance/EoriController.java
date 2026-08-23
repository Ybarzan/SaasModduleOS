package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.EoriService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/eori")
@RequiredArgsConstructor
@Tag(name = "EORI", description = "Gestion des numéros EORI pour les opérations douanières")
@RequiresPlan(Company.Plan.PRO)
public class EoriController {

    private final EoriService eoriService;

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un numéro EORI")
    public ResponseEntity<?> create(@Valid @RequestBody CreateEoriReq req) {
        UUID companyId = TenantContext.get();
        try {
            var eori = eoriService.create(companyId, req.eori(), req.holderName(),
                req.holderAddress(), req.holderCountry(), req.isDefault());
            return ResponseEntity.status(HttpStatus.CREATED).body(eori);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les EORI de l'entreprise")
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID companyId = TenantContext.get();
        if (page != null && size != null && size > 0) {
            Page<?> result = eoriService.list(companyId, PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(eoriService.list(companyId));
    }

    @GetMapping("/default")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Obtenir l'EORI par défaut")
    public ResponseEntity<?> getDefault() {
        UUID companyId = TenantContext.get();
        try {
            return ResponseEntity.ok(eoriService.getDefault(companyId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/default")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Définir un EORI comme par défaut")
    public ResponseEntity<?> setDefault(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        try {
            return ResponseEntity.ok(eoriService.setDefault(companyId, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un EORI")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        UUID companyId = TenantContext.get();
        try {
            eoriService.delete(companyId, id);
            return ResponseEntity.ok(Map.of("message", "EORI supprimé"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "Valider le format d'un numéro EORI")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody Map<String, String> body) {
        String eori = body.getOrDefault("eori", "");
        return ResponseEntity.ok(eoriService.validate(eori));
    }

    record CreateEoriReq(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}\\d{8,15}$",
            message = "Format EORI invalide (2 lettres + 8-15 chiffres)") String eori,
        @NotBlank String holderName,
        String holderAddress,
        @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays à 2 lettres") String holderCountry,
        boolean isDefault
    ) {}
}
