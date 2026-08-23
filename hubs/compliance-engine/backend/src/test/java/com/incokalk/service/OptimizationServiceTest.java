package com.incokalk.service;

import com.incokalk.service.OptimizationService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OptimizationService — Tests unitaires")
class OptimizationServiceTest {

    private OptimizationService service;

    @BeforeEach
    void setUp() {
        service = new OptimizationService();
    }

    @Test
    @DisplayName("analyzeRoutes remplit les lanes, recommandations et consolidations")
    void analyzeRoutes_generatesData() {
        service.analyzeRoutes();

        assertThat(service.getLaneAnalysis()).isNotEmpty();
        assertThat(service.getRecommendations()).isNotEmpty();
        assertThat(service.getConsolidations()).isNotEmpty();
    }

    @Test
    @DisplayName("getStats retourne des statistiques valides après analyse")
    void getStats_returnsValidStats() {
        service.analyzeRoutes();
        OptimizationStats stats = service.getStats();

        assertThat(stats.getTotalRoutes()).isPositive();
        assertThat(stats.getTotalOptimizations()).isPositive();
        assertThat(stats.getAvgConfidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("getStats avant analyse retourne des valeurs par défaut")
    void getStats_beforeAnalyze_returnsDefaults() {
        OptimizationStats stats = service.getStats();

        assertThat(stats.getTotalRoutes()).isEqualTo(0);
        assertThat(stats.getTotalOptimizations()).isEqualTo(0);
        assertThat(stats.getAvgConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("LaneAnalysis contient des champs valides")
    void laneAnalysis_containsValidFields() {
        service.analyzeRoutes();
        List<LaneAnalysisDTO> lanes = service.getLaneAnalysis();

        LaneAnalysisDTO lane = lanes.get(0);
        assertThat(lane.getOrigin()).isNotEmpty();
        assertThat(lane.getDestination()).isNotEmpty();
        assertThat(lane.getTotalShipments()).isPositive();
        assertThat(lane.getBestCost()).isPositive();
        assertThat(lane.getWorstCost()).isGreaterThan(lane.getBestCost());
        assertThat(lane.getPotentialSavings()).isPositive();
        assertThat(lane.getAvgOnTimeRate()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Recommandations contiennent des champs valides")
    void recommendations_containValidFields() {
        service.analyzeRoutes();
        List<RateOptimizationDTO> recs = service.getRecommendations();

        RateOptimizationDTO rec = recs.get(0);
        assertThat(rec.getId()).isNotNull();
        assertThat(rec.getOrigin()).isNotEmpty();
        assertThat(rec.getDestination()).isNotEmpty();
        assertThat(rec.getPredictedCost()).isPositive();
        assertThat(rec.getConfidence()).isBetween(0.0, 1.0);
        assertThat(rec.getSavingsEstimate()).isPositive();
        assertThat(rec.getStatus()).isIn("PENDING", "ACCEPTED");
        assertThat(rec.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Consolidations contiennent des champs valides")
    void consolidations_containValidFields() {
        service.analyzeRoutes();
        List<ConsolidationDTO> consols = service.getConsolidations();

        ConsolidationDTO consol = consols.get(0);
        assertThat(consol.getId()).isNotNull();
        assertThat(consol.getOrigin()).isNotEmpty();
        assertThat(consol.getDestination()).isNotEmpty();
        assertThat(consol.getShipmentCount()).isGreaterThan(1);
        assertThat(consol.getCombinedCost()).isPositive();
        assertThat(consol.getConsolidatedCost()).isLessThan(consol.getCombinedCost());
        assertThat(consol.getEstimatedSavings()).isPositive();
        assertThat(consol.getStatus()).isIn("PENDING", "ACCEPTED");
    }

    @Test
    @DisplayName("acceptRecommendation change le statut et met à jour les économies")
    void acceptRecommendation_updatesStatus() {
        service.analyzeRoutes();
        List<RateOptimizationDTO> recs = service.getRecommendations();
        RateOptimizationDTO pending = recs.stream()
            .filter(r -> "PENDING".equals(r.getStatus()))
            .findFirst().orElse(null);

        if (pending != null) {
            double savingsBefore = service.getStats().getAcceptedSavings();
            boolean accepted = service.acceptRecommendation(pending.getId());
            assertThat(accepted).isTrue();

            RateOptimizationDTO updated = service.getRecommendations().stream()
                .filter(r -> r.getId().equals(pending.getId()))
                .findFirst().orElse(null);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
            assertThat(service.getStats().getAcceptedSavings()).isGreaterThanOrEqualTo(savingsBefore);
        }
    }

    @Test
    @DisplayName("acceptRecommendation avec ID inconnu retourne false")
    void acceptRecommendation_unknownId_returnsFalse() {
        assertThat(service.acceptRecommendation("unknown-id")).isFalse();
    }

    @Test
    @DisplayName("acceptConsolidation change le statut")
    void acceptConsolidation_updatesStatus() {
        service.analyzeRoutes();
        List<ConsolidationDTO> consols = service.getConsolidations();
        ConsolidationDTO pending = consols.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .findFirst().orElse(null);

        if (pending != null) {
            boolean accepted = service.acceptConsolidation(pending.getId());
            assertThat(accepted).isTrue();

            ConsolidationDTO updated = service.getConsolidations().stream()
                .filter(c -> c.getId().equals(pending.getId()))
                .findFirst().orElse(null);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
        }
    }

    @Test
    @DisplayName("acceptConsolidation avec ID inconnu retourne false")
    void acceptConsolidation_unknownId_returnsFalse() {
        assertThat(service.acceptConsolidation("unknown-id")).isFalse();
    }

    @Test
    @DisplayName("findConsolidation ajoute des opportunités")
    void findConsolidation_addsOpportunities() {
        int before = service.getConsolidations().size();
        service.findConsolidation();
        assertThat(service.getConsolidations().size()).isEqualTo(before + 3);
    }

    @Test
    @DisplayName("predict retourne un résultat valide")
    void predict_returnsValidResult() {
        PredictResult result = service.predict("FR", "DE", "ROAD", 500.0, 2.5);

        assertThat(result.getPredictedCost()).isPositive();
        assertThat(result.getRecommendedCarrier()).isNotNull();
        assertThat(result.getConfidence()).isBetween(0.0, 1.0);
        assertThat(result.getSavingsEstimate()).isPositive();
    }

    @Test
    @DisplayName("predict sans poids/volume retourne un résultat")
    void predict_withoutWeightVolume_returnsResult() {
        PredictResult result = service.predict("CN", "FR", "SEA", null, null);

        assertThat(result.getPredictedCost()).isPositive();
        assertThat(result.getRecommendedCarrier()).isNotNull();
    }

    @Test
    @DisplayName("analyzeRoutes peut être appelé plusieurs fois")
    void analyzeRoutes_idempotent() {
        service.analyzeRoutes();
        int lanesAfterFirst = service.getLaneAnalysis().size();

        service.analyzeRoutes();
        int lanesAfterSecond = service.getLaneAnalysis().size();

        assertThat(lanesAfterSecond).isEqualTo(lanesAfterFirst);
    }

    @Test
    @DisplayName("Plusieurs lanes dans l'analyse")
    void analyzeRoutes_multipleLanes() {
        service.analyzeRoutes();
        assertThat(service.getLaneAnalysis().size()).isGreaterThan(2);
    }
}
