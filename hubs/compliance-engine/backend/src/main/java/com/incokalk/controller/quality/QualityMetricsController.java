package com.incokalk.controller.quality;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.quality.QualityMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quality")
@RequiredArgsConstructor
@Tag(name = "Quality Metrics", description = "Indicateurs qualité Six Sigma calculés sur les données opérationnelles réelles")
@RequiresPlan(Company.Plan.STARTER)
public class QualityMetricsController {

    private final QualityMetricsService qualityMetricsService;

    @GetMapping("/metrics")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rapport qualité Six Sigma (DPMO, rendement, niveau sigma par caractéristique critique)")
    public ResponseEntity<QualityMetricsService.QualityReport> getMetrics() {
        return ResponseEntity.ok(qualityMetricsService.getReport());
    }
}
