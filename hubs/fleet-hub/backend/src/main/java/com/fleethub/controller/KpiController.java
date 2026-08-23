package com.fleethub.controller;

import com.fleethub.dto.CoupleDetailDto;
import com.fleethub.dto.CoupleKpiDto;
import com.fleethub.dto.TruckDetailDto;
import com.fleethub.service.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kpis")
@RequiredArgsConstructor
@Tag(name = "Indicateurs (KPI)", description = "Indicateurs de performance de la flotte et des couples chauffeur-camion")
public class KpiController {

    private final KpiService kpiService;

    @GetMapping("/couples")
    @Operation(summary = "KPI de tous les couples", description = "Calcule les indicateurs de performance pour tous les couples chauffeur-camion actifs")
    @ApiResponse(responseCode = "200", description = "Indicateurs calculés avec succès")
    public List<CoupleKpiDto> allCouples(@RequestParam(defaultValue = "MONTH") String period) {
        return kpiService.computeAllCouples(period);
    }

    @GetMapping("/couples/{assignmentId}")
    @Operation(summary = "Détail d'un couple", description = "Retourne les KPI détaillés d'un couple chauffeur-camion spécifique")
    @ApiResponse(responseCode = "200", description = "Détails retournés avec succès")
    @ApiResponse(responseCode = "404", description = "Couple introuvable")
    public CoupleDetailDto coupleDetail(@PathVariable Long assignmentId,
                                        @RequestParam(defaultValue = "MONTH") String period) {
        return kpiService.computeDetail(assignmentId, period);
    }

    @GetMapping("/trucks/{truckId}")
    @Operation(summary = "KPI d'un camion", description = "Retourne les indicateurs de performance d'un camion spécifique")
    @ApiResponse(responseCode = "200", description = "Indicateurs retournés avec succès")
    @ApiResponse(responseCode = "404", description = "Camion introuvable")
    public TruckDetailDto truckDetail(@PathVariable Long truckId,
                                      @RequestParam(defaultValue = "MONTH") String period) {
        return kpiService.computeTruckDetail(truckId, period);
    }

    @GetMapping("/periods")
    @Operation(summary = "Périodes disponibles", description = "Retourne la liste des périodes de calcul disponibles (DAY, WEEK, MONTH)")
    @ApiResponse(responseCode = "200", description = "Liste des périodes retournée")
    public List<String> periods() {
        return List.of("DAY", "WEEK", "MONTH");
    }

    @GetMapping("/definitions")
    @Operation(summary = "Définitions des KPI", description = "Retourne les définitions et descriptions de chaque indicateur de performance")
    @ApiResponse(responseCode = "200", description = "Définitions retournées avec succès")
    public Map<String, String> definitions() {
        return Map.ofEntries(
                Map.entry("costPerKm", "Coût total / km parcourus"),
                Map.entry("utilizationRate", "Temps en service / temps disponible"),
                Map.entry("maintenanceComplianceRate", "Entretiens réalisés à temps / entretiens planifiés"),
                Map.entry("unplannedDowntimeRate", "Temps d'immobilisation imprévue / temps total"),
                Map.entry("riskEventsPer1000Km", "Événements à risque / 1000 km"),
                Map.entry("ecoScore", "Score d'éco-conduite (consommation + événements)"),
                Map.entry("driveTimeShare", "Part du temps passé en roulage"),
                Map.entry("idleShare", "Part du temps moteur au ralenti"),
                Map.entry("onTimeRate", "Livraisons à l'heure / livraisons totales"),
                Map.entry("drivingTimeComplianceRate", "Jours conformes réglementation 561/2006"),
                Map.entry("consumptionPer100Km", "Consommation moyenne L/100km"),
                Map.entry("truckUptimeRate", "Taux de disponibilité opérationnelle"),
                Map.entry("loadedRunRate", "Part de km en charge"),
                Map.entry("performanceScore", "Score composite du couple Chauffeur x Camion")
        );
    }
}
