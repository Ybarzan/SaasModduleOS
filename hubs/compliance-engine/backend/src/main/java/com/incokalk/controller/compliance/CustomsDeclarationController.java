package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CustomsDeclarationService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/customs-declarations")
@RequiredArgsConstructor
@Tag(name = "Customs Declarations", description = "DAU customs declaration management")
@RequiresPlan(Company.Plan.PRO)
public class CustomsDeclarationController {

    private final CustomsDeclarationService declarationService;
    private final com.incokalk.service.DocumentExportService documentExportService;
    private final com.incokalk.service.DeclarationValidationService validationService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les déclarations douanières")
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null && size > 0) {
            Page<CustomsDeclaration> result = declarationService.getAll(PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(declarationService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une déclaration par ID")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(declarationService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer une déclaration douanière")
    public ResponseEntity<?> create(@Valid @RequestBody CreateDeclaration req) {
        try {
            CustomsDeclaration declaration = CustomsDeclaration.builder()
                .declarationType(req.declarationType())
                .customsOffice(req.customsOffice())
                .customsRegime(req.customsRegime())
                .customsCode(req.customsCode())
                .declaredValue(req.declaredValue())
                .currency(req.currency() != null ? req.currency() : "EUR")
                .originCountry(req.originCountry())
                .destinationCountry(req.destinationCountry())
                .hsCode(req.hsCode())
                .goodsDescription(req.goodsDescription())
                .netWeight(req.netWeight())
                .grossWeight(req.grossWeight())
                .packages(req.packages())
                .notes(req.notes())
                .build();

            CustomsDeclaration saved = declarationService.create(declaration);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Modifier une déclaration (brouillon uniquement)")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody CreateDeclaration req) {
        try {
            CustomsDeclaration declaration = new CustomsDeclaration();
            if (req.declarationType() != null) declaration.setDeclarationType(req.declarationType());
            if (req.customsOffice() != null) declaration.setCustomsOffice(req.customsOffice());
            if (req.customsRegime() != null) declaration.setCustomsRegime(req.customsRegime());
            if (req.customsCode() != null) declaration.setCustomsCode(req.customsCode());
            if (req.declaredValue() != null) declaration.setDeclaredValue(req.declaredValue());
            if (req.currency() != null) declaration.setCurrency(req.currency());
            if (req.originCountry() != null) declaration.setOriginCountry(req.originCountry());
            if (req.destinationCountry() != null) declaration.setDestinationCountry(req.destinationCountry());
            if (req.hsCode() != null) declaration.setHsCode(req.hsCode());
            if (req.goodsDescription() != null) declaration.setGoodsDescription(req.goodsDescription());
            if (req.netWeight() != null) declaration.setNetWeight(req.netWeight());
            if (req.grossWeight() != null) declaration.setGrossWeight(req.grossWeight());
            if (req.packages() != null) declaration.setPackages(req.packages());
            if (req.notes() != null) declaration.setNotes(req.notes());
            return ResponseEntity.ok(declarationService.update(id, declaration));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une déclaration")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate req) {
        try {
            return ResponseEntity.ok(declarationService.updateStatus(id, req.status()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une déclaration douanière")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            declarationService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Déclaration supprimée"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/pdf")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Exporter une déclaration DAU en PDF")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        try {
            CustomsDeclaration d = declarationService.getById(id);
            byte[] pdf = documentExportService.generateDauPdf(d);
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=" + d.getDeclarationNumber() + ".pdf")
                .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/xml")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Exporter une déclaration DAU en XML")
    public ResponseEntity<byte[]> exportXml(@PathVariable UUID id) {
        try {
            CustomsDeclaration d = declarationService.getById(id);
            byte[] xml = documentExportService.generateDauXml(d);
            return ResponseEntity.ok()
                .header("Content-Type", "application/xml")
                .header("Content-Disposition", "attachment; filename=" + d.getDeclarationNumber() + ".xml")
                .body(xml);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/validate")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Valider une déclaration DAU et retourner les alertes")
    public ResponseEntity<?> validate(@PathVariable UUID id) {
        try {
            CustomsDeclaration d = declarationService.getById(id);
            var alerts = validationService.validateDau(d);
            return ResponseEntity.ok(Map.of("valid", alerts.isEmpty(), "alerts", alerts));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques des déclarations douanières")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(declarationService.getStats());
    }

    record StatusUpdate(@NotNull CustomsDeclaration.DeclarationStatus status) {}

    record CreateDeclaration(
        CustomsDeclaration.DeclarationType declarationType,
        String customsOffice,
        String customsRegime,
        String customsCode,
        BigDecimal declaredValue,
        String currency,
        String originCountry,
        String destinationCountry,
        String hsCode,
        String goodsDescription,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        Integer packages,
        String notes
    ) {}
}
