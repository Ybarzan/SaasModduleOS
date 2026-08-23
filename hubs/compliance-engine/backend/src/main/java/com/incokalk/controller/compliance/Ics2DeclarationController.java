package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.Ics2Declaration;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.Ics2DeclarationService;
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
@RequestMapping("/v1/ics2-declarations")
@RequiredArgsConstructor
@Tag(name = "ICS2 Declarations", description = "Import Control System 2 security pre-declarations")
@RequiresPlan(Company.Plan.PRO)
public class Ics2DeclarationController {

    private final Ics2DeclarationService ics2DeclarationService;
    private final com.incokalk.service.DocumentExportService documentExportService;
    private final com.incokalk.service.DeclarationValidationService validationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les déclarations ICS2")
    public ResponseEntity<List<Ics2Declaration>> list() {
        return ResponseEntity.ok(ics2DeclarationService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une déclaration ICS2 par ID")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(ics2DeclarationService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une déclaration ICS2")
    public ResponseEntity<?> create(@Valid @RequestBody CreateIcs2Declaration req) {
        try {
            Ics2Declaration declaration = new Ics2Declaration();
            declaration.setSenderEori(req.senderEori());
            declaration.setReceiverEori(req.receiverEori());
            declaration.setVesselName(req.vesselName());
            declaration.setVoyageNumber(req.voyageNumber());
            declaration.setContainerNumber(req.containerNumber());
            declaration.setHsCode6(req.hsCode6());
            declaration.setGoodsDescription(req.goodsDescription());
            declaration.setGrossWeight(req.grossWeight());
            declaration.setPackagesCount(req.packagesCount());
            return ResponseEntity.ok(ics2DeclarationService.create(declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Modifier une déclaration ICS2 (brouillon uniquement)")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody CreateIcs2Declaration req) {
        try {
            Ics2Declaration declaration = new Ics2Declaration();
            if (req.senderEori() != null) declaration.setSenderEori(req.senderEori());
            if (req.receiverEori() != null) declaration.setReceiverEori(req.receiverEori());
            if (req.vesselName() != null) declaration.setVesselName(req.vesselName());
            if (req.voyageNumber() != null) declaration.setVoyageNumber(req.voyageNumber());
            if (req.containerNumber() != null) declaration.setContainerNumber(req.containerNumber());
            if (req.hsCode6() != null) declaration.setHsCode6(req.hsCode6());
            if (req.goodsDescription() != null) declaration.setGoodsDescription(req.goodsDescription());
            if (req.grossWeight() != null) declaration.setGrossWeight(req.grossWeight());
            if (req.packagesCount() != null) declaration.setPackagesCount(req.packagesCount());
            return ResponseEntity.ok(ics2DeclarationService.update(id, declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une déclaration ICS2")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate req) {
        try {
            Ics2Declaration.Ics2Status newStatus = Ics2Declaration.Ics2Status.valueOf(req.status());
            return ResponseEntity.ok(ics2DeclarationService.updateStatus(id, newStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une déclaration ICS2")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            ics2DeclarationService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Déclaration ICS2 supprimée"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir les statistiques des déclarations ICS2")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(ics2DeclarationService.getStats());
    }

    @GetMapping("/{id}/pdf")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Exporter une déclaration ICS2 en PDF")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        try {
            Ics2Declaration d = ics2DeclarationService.getById(id);
            byte[] pdf = documentExportService.generateIcs2Pdf(d);
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
    @Operation(summary = "Valider une déclaration ICS2 et retourner les alertes")
    public ResponseEntity<?> validate(@PathVariable UUID id) {
        try {
            Ics2Declaration d = ics2DeclarationService.getById(id);
            var alerts = validationService.validateIcs2(d);
            return ResponseEntity.ok(Map.of("valid", alerts.isEmpty(), "alerts", alerts));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    record CreateIcs2Declaration(
        String senderEori,
        String receiverEori,
        String vesselName,
        String voyageNumber,
        String containerNumber,
        String hsCode6,
        String goodsDescription,
        java.math.BigDecimal grossWeight,
        Integer packagesCount
    ) {}

    record StatusUpdate(@NotNull String status) {}
}
