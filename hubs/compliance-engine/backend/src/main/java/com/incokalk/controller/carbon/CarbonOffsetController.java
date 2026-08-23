package com.incokalk.controller.carbon;

import com.incokalk.model.CarbonOffset;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CarbonOffsetService;
import com.incokalk.service.CsrdReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/carbon-offsets")
@RequiredArgsConstructor
@Tag(name = "Carbon Offsets", description = "Suivi des crédits carbone et offset")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class CarbonOffsetController {

    private final CarbonOffsetService carbonOffsetService;
    private final CsrdReportingService csrdReportingService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les suivis carbone")
    public ResponseEntity<?> listAll() {
        return ResponseEntity.ok(carbonOffsetService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir un suivi carbone par ID")
    public ResponseEntity<CarbonOffset> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(carbonOffsetService.getById(id));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer un suivi carbone")
    public ResponseEntity<CarbonOffset> create(@Valid @RequestBody CreateCarbonOffset body) {
        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(body.co2EmissionsKg())
                .offsetCreditsPurchased(body.offsetCreditsPurchased())
                .offsetCreditsRetired(body.offsetCreditsRetired())
                .offsetProvider(body.offsetProvider())
                .offsetProjectName(body.offsetProjectName())
                .offsetProjectType(body.offsetProjectType())
                .offsetCostPerTon(body.offsetCostPerTon())
                .offsetTotalCost(body.offsetTotalCost())
                .offsetCurrency(body.offsetCurrency())
                .certificationId(body.certificationId())
                .notes(body.notes())
                .build();
        return ResponseEntity.ok(carbonOffsetService.create(offset));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour un suivi carbone")
    public ResponseEntity<CarbonOffset> update(@PathVariable UUID id, @Valid @RequestBody CreateCarbonOffset body) {
        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(body.co2EmissionsKg())
                .offsetCreditsPurchased(body.offsetCreditsPurchased())
                .offsetCreditsRetired(body.offsetCreditsRetired())
                .offsetProvider(body.offsetProvider())
                .offsetProjectName(body.offsetProjectName())
                .offsetProjectType(body.offsetProjectType())
                .offsetCostPerTon(body.offsetCostPerTon())
                .offsetTotalCost(body.offsetTotalCost())
                .offsetCurrency(body.offsetCurrency())
                .certificationId(body.certificationId())
                .notes(body.notes())
                .build();
        return ResponseEntity.ok(carbonOffsetService.update(id, offset));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer un suivi carbone")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        carbonOffsetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statistiques carbone")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(carbonOffsetService.getStats());
    }

    @GetMapping("/dashboard")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Tableau de bord carbone")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(carbonOffsetService.getDashboard());
    }

    @GetMapping("/csrd-report")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rapport CSRD / EU Taxonomy")
    public ResponseEntity<Map<String, Object>> getCsrdReport() {
        return ResponseEntity.ok(csrdReportingService.getCsrdReport());
    }

    public record CreateCarbonOffset(
            @NotNull @DecimalMin("0.0") BigDecimal co2EmissionsKg,
            @NotNull @DecimalMin("0.0") BigDecimal offsetCreditsPurchased,
            BigDecimal offsetCreditsRetired,
            String offsetProvider,
            String offsetProjectName,
            String offsetProjectType,
            @DecimalMin("0.0") BigDecimal offsetCostPerTon,
            @DecimalMin("0.0") BigDecimal offsetTotalCost,
            String offsetCurrency,
            String certificationId,
            String notes
    ) {}
}
