package com.incokalk.service;

import com.incokalk.config.EtaMlConfig;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.EtaPrediction;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EtaPredictionRepository;
import com.incokalk.service.ml.EtaMlClient;
import com.incokalk.service.ml.EtaRegressionModel;
import com.incokalk.service.ml.EtaTrainingService;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtaPredictionService {

    private final EtaPredictionRepository etaPredictionRepo;
    private final CompanyRepository companyRepo;
    private final EtaTrainingService etaTrainingService;
    private final EtaMlClient etaMlClient;
    private final EtaMlConfig etaMlConfig;

    private static final Map<String, Integer> BASE_TRANSIT_DAYS = Map.ofEntries(
            Map.entry("SEA_DEFAULT", 30),
            Map.entry("AIR_DEFAULT", 5),
            Map.entry("ROAD_DEFAULT", 3),
            Map.entry("RAIL_DEFAULT", 15)
    );

    private static final Set<String> CONGESTED_PORTS = Set.of(
            "CNSHA", "CNSZX", "CNNGB", "CNYNT",
            "NLRTM", "DEHAM", "BEANR",
            "SGSIN", "AEJEA"
    );

    // Baseline maritime par paire de régions — remplace l'ancien SEA_DEFAULT plat.
    // Sourcé sur les réseaux 2026 publiés par MSC, CMA CGM et Hapag-Lloyd : les lignes Asie-Europe
    // sont actuellement acheminées via le Cap de Bonne-Espérance (fermeture Mer Rouge), pas Suez,
    // ce qui ajoute ~10-14 jours par rapport à l'ancienne route. Le transpacifique n'est pas concerné.
    private static final Set<String> ASIA_COUNTRIES = Set.of(
            "CN", "VN", "KR", "JP", "SG", "IN", "TH", "MY", "ID", "TW", "HK", "PH"
    );

    private static final Set<String> EUROPE_COUNTRIES = Set.of(
            "NL", "DE", "BE", "FR", "GB", "IT", "ES", "PT", "PL", "GR"
    );

    private static final Set<String> NA_WEST_PORTS = Set.of(
            "USLAX", "USLGB", "USOAK", "USSEA", "USTAC", "CAVAN"
    );

    private static final Map<String, Integer> LANE_BASE_DAYS = Map.of(
            "ASIA_EUROPE", 40,
            "ASIA_NA_WEST", 17,
            "ASIA_NA_EAST", 30,
            "ASIA_ASIA", 8
    );

    @Transactional(readOnly = true)
    public List<EtaPrediction> getAll() {
        UUID companyId = TenantContext.get();
        return etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public EtaPrediction getById(UUID id) {
        UUID companyId = TenantContext.get();
        return etaPredictionRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Prédiction ETA non trouvée"));
    }

    @Transactional
    public EtaPrediction predict(EtaPrediction prediction) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        prediction.setCompany(company);

        String mode = prediction.getMode() != null ? prediction.getMode() : "SEA";
        int baseDays = calculateLaneBaseDays(prediction.getOrigin(), prediction.getDestination(), mode);
        baseDays = applyCarrierAdjustment(baseDays, prediction.getCarrierName());

        BigDecimal seasonalFactor = calculateSeasonalFactor();
        BigDecimal congestionFactor = calculateCongestionFactor(prediction.getOrigin(), prediction.getDestination());
        int customsDelay = estimateCustomsDelay(prediction.getOrigin(), prediction.getDestination());

        prediction.setSeasonalFactor(seasonalFactor);
        prediction.setCongestionFactor(congestionFactor);
        prediction.setCustomsDelayDays(customsDelay);

        int predictedDays = computeMlPrediction(companyId, prediction, baseDays, seasonalFactor, congestionFactor, customsDelay);

        prediction.setBaselineDays(baseDays);
        prediction.setPredictedDays(predictedDays);

        if (prediction.getCarrierEstimateDays() != null) {
            prediction.setVarianceDays(predictedDays - prediction.getCarrierEstimateDays());
        }

        BigDecimal confidence = calculateConfidence(prediction);
        prediction.setConfidencePercent(confidence);

        if (confidence.compareTo(BigDecimal.valueOf(80)) > 0) {
            prediction.setConfidenceLevel(EtaPrediction.ConfidenceLevel.HIGH);
        } else if (confidence.compareTo(BigDecimal.valueOf(60)) >= 0) {
            prediction.setConfidenceLevel(EtaPrediction.ConfidenceLevel.MEDIUM);
        } else {
            prediction.setConfidenceLevel(EtaPrediction.ConfidenceLevel.LOW);
        }

        if (prediction.getPredictedArrival() == null) {
            prediction.setPredictedArrival(LocalDateTime.now().plusDays(predictedDays));
        }

        StringBuilder riskFactors = new StringBuilder();
        if (isCapeRoutingLane(prediction.getOrigin(), prediction.getDestination(), mode)) {
            riskFactors.append("cape_routing,");
        }
        if (prediction.getSeasonalFactor().compareTo(BigDecimal.ONE) > 0) {
            riskFactors.append("seasonal,");
        }
        if (prediction.getCongestionFactor().compareTo(BigDecimal.ONE) > 0) {
            riskFactors.append("congestion,");
        }
        if (prediction.getCustomsDelayDays() > 0) {
            riskFactors.append("customs,");
        }
        if (prediction.getWeatherDelayDays() > 0) {
            riskFactors.append("weather,");
        }
        if (prediction.getVarianceDays() != null && prediction.getVarianceDays() > 0) {
            riskFactors.append("carrier_variability,");
        }
        if (riskFactors.length() > 0) {
            riskFactors.setLength(riskFactors.length() - 1);
            prediction.setRiskFactors(riskFactors.toString());
        }

        EtaPrediction saved = etaPredictionRepo.save(prediction);
        log.info("ETA prediction created: {} days, confidence {}%", predictedDays, confidence);
        return saved;
    }

    private int computeMlPrediction(UUID companyId, EtaPrediction prediction,
                                     int baseDays, BigDecimal seasonalFactor,
                                     BigDecimal congestionFactor, int customsDelay) {
        int heuristicDays = BigDecimal.valueOf(baseDays)
                .multiply(seasonalFactor)
                .multiply(congestionFactor)
                .add(BigDecimal.valueOf(customsDelay + prediction.getWeatherDelayDays()))
                .setScale(0, RoundingMode.CEILING).intValue();

        // 1. Try Python ML service (LightGBM/XGBoost — highest accuracy)
        try {
            Map<String, Object> mlResult = etaMlClient.predict(
                prediction.getOrigin(), prediction.getDestination(),
                prediction.getMode(), prediction.getCarrierName()
            );
            if (mlResult != null && mlResult.get("predictedDays") != null) {
                int mlDays = ((Number) mlResult.get("predictedDays")).intValue();
                double weight = etaMlConfig.getBlendWeight();
                int blended = (int) Math.round(mlDays * weight + heuristicDays * (1.0 - weight));
                log.info("[ETA-ML-PY] ML={}d, heuristic={}d, blended={}d (weight={})",
                    mlDays, heuristicDays, blended, weight);
                return Math.max(1, blended);
            }
        } catch (Exception e) {
            log.warn("[ETA-ML-PY] Python ML service unavailable: {}", e.getMessage());
        }

        // 2. Try Java in-memory model (fallback)
        try {
            EtaRegressionModel model = etaTrainingService.loadModel(companyId);
            if (model.isTrained()) {
                Map<String, String> mlFeatures = new LinkedHashMap<>();
                mlFeatures.put("mode", prediction.getMode() != null ? prediction.getMode() : "SEA");
                if (prediction.getCarrierName() != null) mlFeatures.put("carrier", prediction.getCarrierName());
                if (prediction.getOrigin() != null && prediction.getOrigin().length() >= 2)
                    mlFeatures.put("origin_region", prediction.getOrigin().substring(0, 2));
                if (prediction.getDestination() != null && prediction.getDestination().length() >= 2)
                    mlFeatures.put("dest_region", prediction.getDestination().substring(0, 2));
                mlFeatures.put("month", String.valueOf(LocalDateTime.now().getMonthValue()));

                int mlPrediction = (int) Math.round(model.predict(mlFeatures));
                log.debug("[ETA-ML-JAVA] Prédiction Java ML: {} jours (vs heuristique: {})", mlPrediction, heuristicDays);

                if (model.getTotalSamples() >= 50) {
                    return Math.max(1, mlPrediction);
                }
                return Math.max(1, (mlPrediction + heuristicDays) / 2);
            }
        } catch (Exception e) {
            log.warn("[ETA-ML-JAVA] Erreur prédiction Java ML: {}", e.getMessage());
        }

        // 3. Pure heuristic fallback
        return heuristicDays;
    }

    @Transactional(readOnly = true)
    public List<EtaPrediction> getByLane(String origin, String destination) {
        UUID companyId = TenantContext.get();
        return etaPredictionRepo.findByCompanyIdAndOriginAndDestination(companyId, origin, destination);
    }

    @Transactional
    public EtaPrediction updateActual(UUID id, LocalDateTime actualArrival) {
        EtaPrediction prediction = getById(id);

        prediction.setActualArrival(actualArrival);
        long actualDays = ChronoUnit.DAYS.between(prediction.getCreatedAt(), actualArrival);
        prediction.setActualDays((int) actualDays);

        prediction.setIsOnTime(!actualArrival.isAfter(prediction.getPredictedArrival()));

        if (prediction.getActualDays() > 0) {
            BigDecimal error = BigDecimal.valueOf(Math.abs(prediction.getPredictedDays() - prediction.getActualDays()));
            BigDecimal accuracy = error.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(prediction.getActualDays()), 2, RoundingMode.HALF_UP);
            prediction.setPredictionAccuracy(BigDecimal.valueOf(100).subtract(accuracy));
        }

        return etaPredictionRepo.save(prediction);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        List<EtaPrediction> predictions = etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) predictions.size());

        if (predictions.isEmpty()) {
            stats.put("avgAccuracy", BigDecimal.ZERO);
            stats.put("onTimePercent", BigDecimal.ZERO);
            stats.put("avgDays", 0);
            return stats;
        }

        BigDecimal totalAccuracy = predictions.stream()
                .filter(p -> p.getPredictionAccuracy() != null)
                .map(EtaPrediction::getPredictionAccuracy)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long withAccuracy = predictions.stream()
                .filter(p -> p.getPredictionAccuracy() != null)
                .count();

        stats.put("avgAccuracy", withAccuracy > 0
                ? totalAccuracy.divide(BigDecimal.valueOf(withAccuracy), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        long onTimeCount = predictions.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsOnTime()))
                .count();

        stats.put("onTimePercent", BigDecimal.valueOf(onTimeCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(predictions.size()), 2, RoundingMode.HALF_UP));

        double avgDays = predictions.stream()
                .filter(p -> p.getPredictedDays() != null)
                .mapToInt(EtaPrediction::getPredictedDays)
                .average()
                .orElse(0.0);
        stats.put("avgDays", (int) Math.round(avgDays));

        return stats;
    }

    private int calculateLaneBaseDays(String origin, String destination, String mode) {
        if (!"SEA".equals(mode)) {
            return BASE_TRANSIT_DAYS.getOrDefault(mode + "_DEFAULT", 30);
        }
        String key = laneKey(classifyRegion(origin), classifyRegion(destination));
        return LANE_BASE_DAYS.getOrDefault(key, BASE_TRANSIT_DAYS.get("SEA_DEFAULT"));
    }

    private boolean isCapeRoutingLane(String origin, String destination, String mode) {
        if (!"SEA".equals(mode)) return false;
        return "ASIA_EUROPE".equals(laneKey(classifyRegion(origin), classifyRegion(destination)));
    }

    private String laneKey(String regionA, String regionB) {
        if (isPair(regionA, regionB, "ASIA", "EUROPE")) return "ASIA_EUROPE";
        if (isPair(regionA, regionB, "ASIA", "NA_WEST")) return "ASIA_NA_WEST";
        if (isPair(regionA, regionB, "ASIA", "NA_EAST")) return "ASIA_NA_EAST";
        if ("ASIA".equals(regionA) && "ASIA".equals(regionB)) return "ASIA_ASIA";
        return "OTHER";
    }

    private boolean isPair(String regionA, String regionB, String x, String y) {
        return (x.equals(regionA) && y.equals(regionB)) || (y.equals(regionA) && x.equals(regionB));
    }

    private String classifyRegion(String unoCode) {
        if (unoCode == null || unoCode.length() < 2) return "OTHER";
        String upper = unoCode.toUpperCase();
        String country = upper.substring(0, 2);
        if (ASIA_COUNTRIES.contains(country)) return "ASIA";
        if (EUROPE_COUNTRIES.contains(country)) return "EUROPE";
        if (country.equals("US") || country.equals("CA")) {
            return NA_WEST_PORTS.contains(upper) ? "NA_WEST" : "NA_EAST";
        }
        return "OTHER";
    }

    private int applyCarrierAdjustment(int days, String carrierName) {
        if (carrierName == null) return days;
        String upper = carrierName.toUpperCase();
        if (upper.contains("CMA CGM")) return days + 3;
        if (upper.contains("MAERSK")) return days - 3;
        return days;
    }

    private BigDecimal calculateSeasonalFactor() {
        Month currentMonth = LocalDateTime.now().getMonth();
        return switch (currentMonth) {
            case OCTOBER, NOVEMBER, DECEMBER -> BigDecimal.valueOf(1.15);
            case JUNE, JULY, AUGUST -> BigDecimal.valueOf(1.05);
            default -> BigDecimal.ONE;
        };
    }

    private BigDecimal calculateCongestionFactor(String origin, String destination) {
        if (origin == null || destination == null) return BigDecimal.ONE;

        String originUpper = origin.toUpperCase();
        String destUpper = destination.toUpperCase();

        boolean originCongested = CONGESTED_PORTS.contains(originUpper)
                || originUpper.startsWith("CN");
        boolean destCongested = CONGESTED_PORTS.contains(destUpper)
                || destUpper.startsWith("EU") || destUpper.startsWith("NL")
                || destUpper.startsWith("DE") || destUpper.startsWith("BE");

        if (originCongested && destCongested) {
            return BigDecimal.valueOf(1.15);
        } else if (originCongested || destCongested) {
            return BigDecimal.valueOf(1.08);
        }
        return BigDecimal.ONE;
    }

    private int estimateCustomsDelay(String origin, String destination) {
        if (origin == null || destination == null) return 1;

        String originCountry = origin.length() >= 2 ? origin.substring(0, 2).toUpperCase() : "";
        String destCountry = destination.length() >= 2 ? destination.substring(0, 2).toUpperCase() : "";

        if (!originCountry.equals(destCountry)) {
            return 2;
        }
        return 1;
    }

    private BigDecimal calculateConfidence(EtaPrediction prediction) {
        BigDecimal confidence = BigDecimal.valueOf(85);

        if (prediction.getCarrierEstimateDays() != null && prediction.getVarianceDays() != null) {
            int absVariance = Math.abs(prediction.getVarianceDays());
            if (absVariance <= 2) {
                confidence = confidence.add(BigDecimal.valueOf(5));
            } else if (absVariance > 5) {
                confidence = confidence.subtract(BigDecimal.valueOf(10));
            }
        }

        if (prediction.getSeasonalFactor().compareTo(BigDecimal.valueOf(1.1)) > 0) {
            confidence = confidence.subtract(BigDecimal.valueOf(5));
        }
        if (prediction.getCongestionFactor().compareTo(BigDecimal.valueOf(1.1)) > 0) {
            confidence = confidence.subtract(BigDecimal.valueOf(5));
        }
        if (prediction.getCustomsDelayDays() > 2) {
            confidence = confidence.subtract(BigDecimal.valueOf(3));
        }
        if (prediction.getWeatherDelayDays() > 0) {
            confidence = confidence.subtract(BigDecimal.valueOf(5));
        }

        confidence = confidence.max(BigDecimal.valueOf(20)).min(BigDecimal.valueOf(98));
        return confidence.setScale(2, RoundingMode.HALF_UP);
    }
}
