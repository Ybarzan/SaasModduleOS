package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.DebDeclaration;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.DebDeclarationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/deb-declarations")
@RequiredArgsConstructor
@Tag(name = "DEB/Intrastat Declarations", description = "Déclaration d'Échanges de Biens management")
@RequiresPlan(Company.Plan.PRO)
public class DebDeclarationController {

    private final DebDeclarationService debDeclarationService;
    private final com.incokalk.service.DocumentExportService documentExportService;
    private final com.incokalk.service.DeclarationValidationService validationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les déclarations DEB")
    public ResponseEntity<List<DebDeclaration>> list() {
        return ResponseEntity.ok(debDeclarationService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une déclaration DEB par ID")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(debDeclarationService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une déclaration DEB")
    public ResponseEntity<?> create(@Valid @RequestBody CreateDebDeclaration req) {
        try {
            DebDeclaration declaration = new DebDeclaration();
            if (req.declarationType() != null) {
                declaration.setDeclarationType(DebDeclaration.DebType.valueOf(req.declarationType()));
            }
            declaration.setPeriod(req.period());
            declaration.setPartnerCountry(req.partnerCountry());
            declaration.setNatureOfTransaction(req.natureOfTransaction());
            declaration.setModeOfTransport(req.modeOfTransport());
            declaration.setNetMass(req.netMass());
            declaration.setStatisticalValue(req.statisticalValue());
            declaration.setHsCode8(req.hsCode8());
            declaration.setGoodsDescription(req.goodsDescription());
            return ResponseEntity.ok(debDeclarationService.create(declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Modifier une déclaration DEB (brouillon uniquement)")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody CreateDebDeclaration req) {
        try {
            DebDeclaration declaration = new DebDeclaration();
            if (req.declarationType() != null) declaration.setDeclarationType(DebDeclaration.DebType.valueOf(req.declarationType()));
            if (req.period() != null) declaration.setPeriod(req.period());
            if (req.partnerCountry() != null) declaration.setPartnerCountry(req.partnerCountry());
            if (req.natureOfTransaction() != null) declaration.setNatureOfTransaction(req.natureOfTransaction());
            if (req.modeOfTransport() != null) declaration.setModeOfTransport(req.modeOfTransport());
            if (req.netMass() != null) declaration.setNetMass(req.netMass());
            if (req.statisticalValue() != null) declaration.setStatisticalValue(req.statisticalValue());
            if (req.hsCode8() != null) declaration.setHsCode8(req.hsCode8());
            if (req.goodsDescription() != null) declaration.setGoodsDescription(req.goodsDescription());
            return ResponseEntity.ok(debDeclarationService.update(id, declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une déclaration DEB")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate req) {
        try {
            DebDeclaration.DebStatus newStatus = DebDeclaration.DebStatus.valueOf(req.status());
            return ResponseEntity.ok(debDeclarationService.updateStatus(id, newStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une déclaration DEB")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            debDeclarationService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Déclaration DEB supprimée"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques des déclarations DEB")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(debDeclarationService.getStats());
    }

    @GetMapping("/{id}/pdf")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Exporter une déclaration DEB en PDF")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        try {
            DebDeclaration d = debDeclarationService.getById(id);
            byte[] pdf = documentExportService.generateDebPdf(d);
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=" + d.getDeclarationNumber() + ".pdf")
                .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/validate")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Valider une déclaration DEB et retourner les alertes")
    public ResponseEntity<?> validate(@PathVariable UUID id) {
        try {
            DebDeclaration d = debDeclarationService.getById(id);
            var alerts = validationService.validateDeb(d);
            return ResponseEntity.ok(Map.of("valid", alerts.isEmpty(), "alerts", alerts));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/by-period/{period}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir les déclarations DEB par période")
    public ResponseEntity<?> getByPeriod(@PathVariable String period) {
        try {
            return ResponseEntity.ok(debDeclarationService.getByPeriod(period));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    record CreateDebDeclaration(
        String declarationType,
        String period,
        String partnerCountry,
        String natureOfTransaction,
        String modeOfTransport,
        java.math.BigDecimal netMass,
        java.math.BigDecimal statisticalValue,
        String hsCode8,
        String goodsDescription
    ) {}

    record StatusUpdate(@NotNull String status) {}
}
