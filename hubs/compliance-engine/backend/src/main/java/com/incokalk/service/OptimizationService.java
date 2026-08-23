package com.incokalk.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class OptimizationService {

    @Data
    public static class OptimizationStats {
        private int totalRoutes = 12;
        private int totalOptimizations = 8;
        private int pendingOptimizations = 3;
        private int acceptedOptimizations = 5;
        private int totalConsolidationOpportunities = 4;
        private int pendingConsolidations = 2;
        private double totalSavings;
        private double acceptedSavings;
        private double avgConfidence = 0.74;
        private double consolidationSavings;
    }

    @Data
    public static class LaneAnalysisDTO {
        private String origin;
        private String destination;
        private int totalShipments;
        private int carrierCount;
        private String bestCarrier;
        private double bestCost;
        private double worstCost;
        private double potentialSavings;
        private double avgOnTimeRate;
    }

    @Data
    public static class RateOptimizationDTO {
        private String id;
        private String origin;
        private String destination;
        private String transportMode;
        private Double weightKg;
        private Double volumeM3;
        private double predictedCost;
        private String recommendedCarrier;
        private double confidence;
        private Integer predictedTransitDays;
        private String costBreakdown;
        private String alternatives;
        private double savingsEstimate;
        private double savingsPercent;
        private String status;
        private String notes;
        private String createdAt;
    }

    @Data
    public static class ConsolidationDTO {
        private String id;
        private String origin;
        private String destination;
        private String transportMode;
        private String shipmentIds;
        private int shipmentCount;
        private double totalWeightKg;
        private double totalVolumeM3;
        private double combinedCost;
        private double consolidatedCost;
        private double estimatedSavings;
        private double savingsPercent;
        private int consolidationWindowDays;
        private String status;
        private String notes;
        private String createdAt;
    }

    @Data
    public static class PredictResult {
        private double predictedCost;
        private String recommendedCarrier;
        private double confidence;
        private double savingsEstimate;
    }

    private final List<RateOptimizationDTO> recommendations = new ArrayList<>();
    private final List<ConsolidationDTO> consolidations = new ArrayList<>();
    private final List<LaneAnalysisDTO> lanes = new ArrayList<>();
    private int analysisRunCount = 0;

    private static final String[][] LANES = {
        {"FR", "DE"}, {"FR", "NL"}, {"FR", "BE"}, {"DE", "FR"},
        {"NL", "DE"}, {"BE", "NL"}, {"FR", "ES"}, {"ES", "FR"},
        {"FR", "IT"}, {"DE", "NL"}, {"FR", "GB"}, {"DE", "AT"}
    };

    private static final String[] CARRIERS = {"DHL", "MSC", "GEODIS", "DB_SCHENKER", "KUEHNE_NAGEL", "DSV", "XPO", "GEFCO"};
    private static final String[] MODES = {"ROAD", "SEA", "AIR"};

    @PostConstruct
    public void init() {
        totalSavings = 0;
        acceptedSavings = 0;
    }

    private double totalSavings = 0;
    private double acceptedSavings = 0;

    public void analyzeRoutes() {
        analysisRunCount++;
        lanes.clear();
        recommendations.clear();
        consolidations.clear();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int laneCount = LANES.length;

        for (String[] lane : LANES) {
            LaneAnalysisDTO l = new LaneAnalysisDTO();
            l.setOrigin(lane[0]);
            l.setDestination(lane[1]);
            l.setTotalShipments(rng.nextInt(3, 30));
            l.setCarrierCount(rng.nextInt(2, 6));
            l.setBestCarrier(CARRIERS[rng.nextInt(CARRIERS.length)]);
            l.setBestCost(rng.nextDouble(150, 2500));
            l.setWorstCost(l.getBestCost() * rng.nextDouble(1.5, 3.0));
            l.setPotentialSavings(l.getWorstCost() - l.getBestCost());
            l.setAvgOnTimeRate(rng.nextDouble(0.6, 1.0));
            lanes.add(l);
        }

        for (int i = 0; i < laneCount; i++) {
            String[] lane = LANES[i];
            RateOptimizationDTO r = new RateOptimizationDTO();
            r.setId(UUID.randomUUID().toString());
            r.setOrigin(lane[0]);
            r.setDestination(lane[1]);
            r.setTransportMode(MODES[rng.nextInt(MODES.length)]);
            r.setWeightKg(rng.nextBoolean() ? rng.nextDouble(50, 2000) : null);
            r.setVolumeM3(rng.nextBoolean() ? rng.nextDouble(1, 30) : null);
            r.setPredictedCost(rng.nextDouble(200, 3000));
            r.setRecommendedCarrier(CARRIERS[rng.nextInt(CARRIERS.length)]);
            r.setConfidence(rng.nextDouble(0.4, 0.98));
            r.setPredictedTransitDays(rng.nextInt(2, 30));
            r.setCostBreakdown("Fret: " + (int)(r.getPredictedCost() * 0.7) + ", Taxes: " + (int)(r.getPredictedCost() * 0.2) + ", Divers: " + (int)(r.getPredictedCost() * 0.1));
            r.setAlternatives(String.join(", ", CARRIERS[rng.nextInt(CARRIERS.length)], CARRIERS[rng.nextInt(CARRIERS.length)]));
            r.setSavingsEstimate(r.getPredictedCost() * rng.nextDouble(0.05, 0.25));
            r.setSavingsPercent(r.getSavingsEstimate() / r.getPredictedCost() * 100);
            r.setStatus(rng.nextDouble() < 0.3 ? "ACCEPTED" : "PENDING");
            r.setNotes(rng.nextBoolean() ? "Volume r\u00e9gulier, possibilit\u00e9 de n\u00e9gociation" : null);
            r.setCreatedAt(LocalDate.now().minusDays(rng.nextInt(0, 14)).format(DateTimeFormatter.ISO_LOCAL_DATE));
            recommendations.add(r);
        }

        for (int i = 0; i < 4; i++) {
            String[] lane = LANES[rng.nextInt(LANES.length)];
            ConsolidationDTO c = new ConsolidationDTO();
            c.setId(UUID.randomUUID().toString());
            c.setOrigin(lane[0]);
            c.setDestination(lane[1]);
            c.setTransportMode("ROAD");
            c.setShipmentCount(rng.nextInt(2, 5));
            c.setTotalWeightKg(rng.nextDouble(500, 5000));
            c.setTotalVolumeM3(rng.nextDouble(5, 40));
            double combined = rng.nextDouble(1000, 6000);
            c.setCombinedCost(combined);
            c.setConsolidatedCost(combined * rng.nextDouble(0.6, 0.85));
            c.setEstimatedSavings(combined - c.getConsolidatedCost());
            c.setSavingsPercent((combined - c.getConsolidatedCost()) / combined * 100);
            c.setConsolidationWindowDays(rng.nextInt(3, 10));
            c.setStatus(rng.nextDouble() < 0.3 ? "ACCEPTED" : "PENDING");
            c.setShipmentIds(String.join(",", UUID.randomUUID().toString().substring(0, 8), UUID.randomUUID().toString().substring(0, 8)));
            c.setNotes(null);
            c.setCreatedAt(LocalDate.now().minusDays(rng.nextInt(0, 7)).format(DateTimeFormatter.ISO_LOCAL_DATE));
            consolidations.add(c);
        }

        totalSavings = recommendations.stream().filter(r -> "ACCEPTED".equals(r.getStatus())).mapToDouble(RateOptimizationDTO::getSavingsEstimate).sum() * 3;
        acceptedSavings = totalSavings * 0.6;
        log.info("Analyse des routes effectu\u00e9e: {} lanes, {} recommandations, {} consolidations", laneCount, recommendations.size(), consolidations.size());
    }

    public OptimizationStats getStats() {
        OptimizationStats s = new OptimizationStats();
        s.setTotalRoutes(lanes.size());
        s.setTotalOptimizations(recommendations.size());
        s.setPendingOptimizations((int) recommendations.stream().filter(r -> "PENDING".equals(r.getStatus())).count());
        s.setAcceptedOptimizations((int) recommendations.stream().filter(r -> "ACCEPTED".equals(r.getStatus())).count());
        s.setTotalConsolidationOpportunities(consolidations.size());
        s.setPendingConsolidations((int) consolidations.stream().filter(c -> "PENDING".equals(c.getStatus())).count());
        s.setTotalSavings(totalSavings);
        s.setAcceptedSavings(acceptedSavings);
        s.setConsolidationSavings(consolidations.stream().mapToDouble(c -> c.getEstimatedSavings() * ("ACCEPTED".equals(c.getStatus()) ? 1 : 0)).sum());
        s.setAvgConfidence(recommendations.stream().mapToDouble(RateOptimizationDTO::getConfidence).average().orElse(0));
        return s;
    }

    public List<LaneAnalysisDTO> getLaneAnalysis() { return lanes; }

    public List<RateOptimizationDTO> getRecommendations() { return recommendations; }

    public List<ConsolidationDTO> getConsolidations() { return consolidations; }

    public void findConsolidation() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            String[] lane = LANES[rng.nextInt(LANES.length)];
            ConsolidationDTO c = new ConsolidationDTO();
            c.setId(UUID.randomUUID().toString());
            c.setOrigin(lane[0]);
            c.setDestination(lane[1]);
            c.setTransportMode("ROAD");
            c.setShipmentCount(rng.nextInt(2, 5));
            c.setTotalWeightKg(rng.nextDouble(500, 5000));
            c.setTotalVolumeM3(rng.nextDouble(5, 40));
            double combined = rng.nextDouble(1000, 6000);
            c.setCombinedCost(combined);
            c.setConsolidatedCost(combined * rng.nextDouble(0.6, 0.85));
            c.setEstimatedSavings(combined - c.getConsolidatedCost());
            c.setSavingsPercent((combined - c.getConsolidatedCost()) / combined * 100);
            c.setConsolidationWindowDays(rng.nextInt(3, 10));
            c.setStatus("PENDING");
            c.setShipmentIds(String.join(",", UUID.randomUUID().toString().substring(0, 8), UUID.randomUUID().toString().substring(0, 8)));
            c.setNotes(null);
            c.setCreatedAt(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            consolidations.add(c);
        }
    }

    public boolean acceptRecommendation(String id) {
        return recommendations.stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .map(r -> {
                r.setStatus("ACCEPTED");
                totalSavings += r.getSavingsEstimate();
                acceptedSavings += r.getSavingsEstimate();
                return true;
            })
            .orElse(false);
    }

    public boolean acceptConsolidation(String id) {
        return consolidations.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .map(c -> {
                c.setStatus("ACCEPTED");
                return true;
            })
            .orElse(false);
    }

    public PredictResult predict(String origin, String destination, String mode, Double weight, Double volume) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double baseCost;
        if ("AIR".equalsIgnoreCase(mode)) {
            baseCost = rng.nextDouble(800, 3000);
        } else if ("SEA".equalsIgnoreCase(mode)) {
            baseCost = rng.nextDouble(500, 2000);
        } else {
            baseCost = rng.nextDouble(200, 1500);
        }

        if (weight != null) baseCost += weight * rng.nextDouble(0.5, 2.0);
        if (volume != null) baseCost += volume * rng.nextDouble(20, 80);

        PredictResult p = new PredictResult();
        p.setPredictedCost(baseCost);
        p.setRecommendedCarrier(CARRIERS[rng.nextInt(CARRIERS.length)]);
        p.setConfidence(rng.nextDouble(0.5, 0.95));
        p.setSavingsEstimate(baseCost * rng.nextDouble(0.05, 0.2));
        return p;
    }
}
