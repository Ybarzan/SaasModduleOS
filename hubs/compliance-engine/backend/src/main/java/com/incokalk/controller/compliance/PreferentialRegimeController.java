package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CumulationGroup;
import com.incokalk.repository.CumulationGroupRepository;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.compliance.CumulationService;
import com.incokalk.service.compliance.PreferentialRegimeService;
import com.incokalk.service.compliance.RulesOfOriginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/compliance/preferential")
@RequiredArgsConstructor
@Tag(name = "Preferential Regimes", description = "R�gimes pr�f�rentiels EU, v�rification r�gles d'origine")
@RequiresPlan(Company.Plan.PRO)
public class PreferentialRegimeController {

    private final PreferentialRegimeService preferentialService;
    private final RulesOfOriginService rulesOfOriginService;
    private final CumulationService cumulationService;
    private final CumulationGroupRepository cumulationGroupRepository;

    @PostMapping("/verify-origin")
    @Operation(summary = "V�rifier l'origine pr�f�rentielle d'un produit")
    public ResponseEntity<RulesOfOriginService.OriginVerificationResult> verifyOrigin(
            @Valid @RequestBody OriginVerificationRequest body,
            HttpServletRequest httpReq) {
        var result = rulesOfOriginService.verifyOrigin(
                body.hsCode(), body.originCountry(), body.destCountry(),
                body.valueAdded(), body.totalCost(), body.manufacturingSteps());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-cumulation")
    @Operation(summary = "V�rifier l'origine via la cumulation bilat�rale ou r�gionale")
    public ResponseEntity<CumulationService.CumulationResult> verifyCumulation(
            @Valid @RequestBody CumulationVerificationRequest body) {
        var result = cumulationService.assess(
                body.hsCode(), body.originCountry(), body.destCountry(),
                body.valueAdded(), body.totalCost(), body.materials());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-origin-with-cumulation")
    @Operation(summary = "V�rifier l'origine (r�gles classiques puis cumulation)")
    public ResponseEntity<RulesOfOriginService.OriginVerificationResult> verifyOriginWithCumulation(
            @Valid @RequestBody CumulationVerificationRequest body) {
        var result = cumulationService.verifyWithCumulation(
                body.hsCode(), body.originCountry(), body.destCountry(),
                body.valueAdded(), body.totalCost(),
                body.manufacturingSteps() != null ? body.manufacturingSteps() : List.of(),
                body.materials());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cumulation-groups")
    @Operation(summary = "Lister les groupes de cumul r�gionaux")
    public ResponseEntity<List<CumulationGroup>> listCumulationGroups() {
        return ResponseEntity.ok(cumulationGroupRepository.findByIsActiveTrue());
    }

    @GetMapping("/rates")
    @Operation(summary = "Lister les taux pr�f�rentiels pour un code HS")
    public ResponseEntity<List<PreferentialRegimeService.PreferentialResult>> getPreferentialRates(
            @RequestParam @NotBlank String hsCode,
            @RequestParam @NotBlank String destCountry,
            @RequestParam(defaultValue = "100") Double goodsValue,
            @RequestParam(defaultValue = "0") Double valueAdded,
            @RequestParam(defaultValue = "0") Double totalCost) {
        var results = preferentialService.getAllPreferentialRates(
                hsCode, destCountry, goodsValue, valueAdded, totalCost);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculer les droits pr�f�rentiels pour une exp�dition")
    public ResponseEntity<PreferentialRegimeService.PreferentialResult> calculatePreferentialDuty(
            @Valid @RequestBody PreferentialCalculationRequest body,
            HttpServletRequest httpReq) {
        var result = preferentialService.calculatePreferentialDuty(
                body.hsCode(), body.originCountry(), body.destCountry(),
                body.goodsValue(), body.valueAdded(), body.totalCost());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/agreements")
    @Operation(summary = "Lister les accords commerciaux applicables")
    public ResponseEntity<List<Map<String, String>>> getApplicableAgreements(
            @RequestParam @NotBlank String originCountry) {
        var criteria = rulesOfOriginService.getApplicableCriteria(originCountry);
        return ResponseEntity.ok(List.of(
                Map.of("originCountry", originCountry, "criteria", criteria.toString())));
    }

    public record OriginVerificationRequest(
            @NotBlank String hsCode,
            @NotBlank String originCountry,
            @NotBlank String destCountry,
            @NotNull Double valueAdded,
            @NotNull Double totalCost,
            List<String> manufacturingSteps
    ) {}

    public record PreferentialCalculationRequest(
            @NotBlank String hsCode,
            @NotBlank String originCountry,
            @NotBlank String destCountry,
            @NotNull Double goodsValue,
            @NotNull Double valueAdded,
            @NotNull Double totalCost
    ) {}

    public record CumulationVerificationRequest(
            @NotBlank String hsCode,
            @NotBlank String originCountry,
            @NotBlank String destCountry,
            @NotNull Double valueAdded,
            @NotNull Double totalCost,
            List<String> manufacturingSteps,
            @NotNull List<CumulationService.Material> materials
    ) {}
}