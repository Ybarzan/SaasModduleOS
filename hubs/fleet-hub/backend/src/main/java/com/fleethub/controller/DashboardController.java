package com.fleethub.controller;

import com.fleethub.dto.DashboardSummaryDto;
import com.fleethub.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tableau de bord", description = "Résumé et vue d'ensemble de la flotte")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Résumé du tableau de bord", description = "Retourne le résumé des indicateurs clés de la flotte pour une période donnée")
    @ApiResponse(responseCode = "200", description = "Résumé retourné avec succès")
    public DashboardSummaryDto summary(@RequestParam(defaultValue = "MONTH") String period) {
        return dashboardService.summary(period);
    }
}
