package com.incokalk.controller.compliance;

import com.incokalk.dto.compliance.ComplianceAlert;
import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.model.Company;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.model.DeniedPartyCheck;
import com.incokalk.model.EoriNumber;
import com.incokalk.model.Incoterm;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.DeniedPartyCheckRepository;
import com.incokalk.repository.EoriNumberRepository;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.ComplianceService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Moteur de règles de conformité internationale")
@RequiresPlan(Company.Plan.PRO)
public class ComplianceController {

    private final ComplianceService complianceService;
    private final CustomsDeclarationRepository declarationRepo;
    private final DeniedPartyCheckRepository dpsRepo;
    private final EoriNumberRepository eoriRepo;

    @GetMapping("/stats")
    @Operation(summary = "Statistiques de conformité de l'entreprise")
    public ResponseEntity<Map<String, Long>> stats() {
        UUID companyId = TenantContext.get();

        long declarationsPending =
            declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.DRAFT)
            + declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.SUBMITTED)
            + declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.UNDER_REVIEW);

        long dpsAlerts =
            dpsRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.MATCH)
            + dpsRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.POSSIBLE_MATCH)
            + dpsRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.BLOCKED);

        long sanctionsMatches = dpsRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.BLOCKED);

        long expiringEori = eoriRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
            .filter(e -> !e.isValid())
            .count();

        return ResponseEntity.ok(Map.of(
            "declarationsPending", declarationsPending,
            "dpsAlerts", dpsAlerts,
            "sanctionsMatches", sanctionsMatches,
            "expiringEori", expiringEori
        ));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Liste des alertes de conformité récentes")
    public ResponseEntity<List<Map<String, Object>>> alerts() {
        UUID companyId = TenantContext.get();
        DateTimeFormatter iso = DateTimeFormatter.ISO_DATE_TIME;
        List<Map<String, Object>> alerts = new ArrayList<>();

        for (DeniedPartyCheck dps : dpsRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (dps.getResult() == DeniedPartyCheck.CheckResult.CLEAR) continue;
            String severity = switch (dps.getResult()) {
                case BLOCKED -> "high";
                case MATCH -> "high";
                case POSSIBLE_MATCH -> "medium";
                default -> "low";
            };
            alerts.add(Map.of(
                "id", dps.getId().toString(),
                "type", "DENIED_PARTY",
                "title", "Screening tiers : " + dps.getCheckedName(),
                "description", dps.getMatchedListName() != null
                    ? "Correspondance trouvée sur la liste " + dps.getMatchedListName()
                    : "Résultat de contrôle : " + dps.getResult(),
                "severity", severity,
                "date", dps.getCreatedAt() != null ? dps.getCreatedAt().format(iso) : ""
            ));
        }

        for (CustomsDeclaration d : declarationRepo.findByCompanyIdAndStatus(
                companyId, CustomsDeclaration.DeclarationStatus.REJECTED)) {
            alerts.add(Map.of(
                "id", d.getId().toString(),
                "type", "DECLARATION",
                "title", "Déclaration rejetée : " + d.getDeclarationNumber(),
                "description", "La déclaration douanière a été rejetée et nécessite une correction",
                "severity", "high",
                "date", d.getUpdatedAt() != null ? d.getUpdatedAt().format(iso) : ""
            ));
        }

        for (EoriNumber e : eoriRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (e.isValid()) continue;
            alerts.add(Map.of(
                "id", e.getId().toString(),
                "type", "EORI",
                "title", "Numéro EORI non validé : " + e.getEori(),
                "description", "Ce numéro EORI n'a pas encore été validé auprès de la base européenne",
                "severity", "medium",
                "date", e.getUpdatedAt() != null ? e.getUpdatedAt().format(iso) : ""
            ));
        }

        alerts.sort(Comparator.comparing(a -> (String) a.get("date"), Comparator.reverseOrder()));
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/check")
    @Operation(summary = "Vérifier la conformité d'une configuration de shipment")
    public ResponseEntity<List<ComplianceAlert>> checkCompliance(
            @Valid @RequestBody SimulationRequest request) {
        Incoterm incoterm = request.getIncoterm();
        return ResponseEntity.ok(complianceService.checkCompliance(request, incoterm));
    }

    @GetMapping("/rules")
    @Operation(summary = "Lister les catégories de règles de conformité")
    public ResponseEntity<List<Map<String, String>>> listRules() {
        return ResponseEntity.ok(List.of(
            Map.of("category", "INCOTERM", "description", "Compatibilité Incoterm / mode de transport"),
            Map.of("category", "COUNTRY", "description", "Risques pays : sanctions, embargos, routes à risque"),
            Map.of("category", "HS_CODE", "description", "Validation du code SH : format, chapitres restreints, biens à double usage"),
            Map.of("category", "TRANSPORT", "description", "Seuils de valeur, adéquation assurance, exigences documentaires")
        ));
    }
}
