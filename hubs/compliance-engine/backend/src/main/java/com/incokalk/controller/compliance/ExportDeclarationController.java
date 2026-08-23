package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ExportDeclaration;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ExportDeclarationService;
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
@RequestMapping("/v1/export-declarations")
@RequiredArgsConstructor
@Tag(name = "Export Declarations", description = "AES/EXS export declarations")
@RequiresPlan(Company.Plan.PRO)
public class ExportDeclarationController {

    private final ExportDeclarationService exportDeclarationService;
    private final com.incokalk.service.DocumentExportService documentExportService;
    private final com.incokalk.service.DeclarationValidationService validationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les déclarations d'export")
    public ResponseEntity<List<ExportDeclaration>> list() {
        return ResponseEntity.ok(exportDeclarationService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une déclaration d'export par ID")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(exportDeclarationService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une déclaration d'export")
    public ResponseEntity<?> create(@Valid @RequestBody CreateExportDeclaration req) {
        try {
            ExportDeclaration declaration = new ExportDeclaration();
            if (req.declarationType() != null) {
                declaration.setDeclarationType(ExportDeclaration.ExportType.valueOf(req.declarationType()));
            }
            declaration.setExporterEori(req.exporterEori());
            declaration.setDestinationCountry(req.destinationCountry());
            declaration.setGoodsDescription(req.goodsDescription());
            declaration.setHsCode(req.hsCode());
            declaration.setDeclaredValue(req.declaredValue());
            declaration.setCurrency(req.currency() != null ? req.currency() : "EUR");
            declaration.setNetWeight(req.netWeight());
            declaration.setGrossWeight(req.grossWeight());
            declaration.setPackagesCount(req.packagesCount());
            return ResponseEntity.ok(exportDeclarationService.create(declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Modifier une déclaration d'export (brouillon uniquement)")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody CreateExportDeclaration req) {
        try {
            ExportDeclaration declaration = new ExportDeclaration();
            if (req.declarationType() != null) declaration.setDeclarationType(ExportDeclaration.ExportType.valueOf(req.declarationType()));
            if (req.exporterEori() != null) declaration.setExporterEori(req.exporterEori());
            if (req.destinationCountry() != null) declaration.setDestinationCountry(req.destinationCountry());
            if (req.goodsDescription() != null) declaration.setGoodsDescription(req.goodsDescription());
            if (req.hsCode() != null) declaration.setHsCode(req.hsCode());
            if (req.declaredValue() != null) declaration.setDeclaredValue(req.declaredValue());
            if (req.currency() != null) declaration.setCurrency(req.currency());
            if (req.netWeight() != null) declaration.setNetWeight(req.netWeight());
            if (req.grossWeight() != null) declaration.setGrossWeight(req.grossWeight());
            if (req.packagesCount() != null) declaration.setPackagesCount(req.packagesCount());
            return ResponseEntity.ok(exportDeclarationService.update(id, declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une déclaration d'export")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate req) {
        try {
            ExportDeclaration.ExportStatus newStatus = ExportDeclaration.ExportStatus.valueOf(req.status());
            return ResponseEntity.ok(exportDeclarationService.updateStatus(id, newStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une déclaration d'export")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            exportDeclarationService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Déclaration d'export supprimée"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir les statistiques des déclarations d'export")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(exportDeclarationService.getStats());
    }

    @GetMapping("/{id}/pdf")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Exporter une déclaration d'export en PDF")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        try {
            ExportDeclaration d = exportDeclarationService.getById(id);
            byte[] pdf = documentExportService.generateExportPdf(d);
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
    @Operation(summary = "Valider une déclaration d'export et retourner les alertes")
    public ResponseEntity<?> validate(@PathVariable UUID id) {
        try {
            ExportDeclaration d = exportDeclarationService.getById(id);
            var alerts = validationService.validateExport(d);
            return ResponseEntity.ok(Map.of("valid", alerts.isEmpty(), "alerts", alerts));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    record CreateExportDeclaration(
        String declarationType,
        String exporterEori,
        String destinationCountry,
        String goodsDescription,
        String hsCode,
        java.math.BigDecimal declaredValue,
        String currency,
        java.math.BigDecimal netWeight,
        java.math.BigDecimal grossWeight,
        Integer packagesCount
    ) {}

    record StatusUpdate(@NotNull String status) {}
}
