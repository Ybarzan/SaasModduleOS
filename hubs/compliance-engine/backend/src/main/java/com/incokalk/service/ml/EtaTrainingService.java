package com.incokalk.service.ml;

import com.incokalk.model.EtaModelCoefficient;
import com.incokalk.model.EtaPrediction;
import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EtaModelCoefficientRepository;
import com.incokalk.repository.EtaPredictionRepository;
import com.incokalk.scheduling.DistributedJobLock;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class EtaTrainingService {

    private final EtaPredictionRepository etaPredictionRepo;
    private final EtaModelCoefficientRepository coefficientRepo;
    private final CompanyRepository companyRepo;
    private final DistributedJobLock jobLock;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void trainAllModels() {
        jobLock.runExclusively("eta-train-all-models", Duration.ofMinutes(30), () -> {
            List<Company> companies = companyRepo.findAll();
            log.info("[ETA-ML] Entraînement des modèles pour {} entreprises", companies.size());
            for (Company company : companies) {
                try {
                    trainModel(company.getId());
                } catch (Exception e) {
                    log.error("[ETA-ML] Erreur entraînement entreprise {}: {}", company.getId(), e.getMessage());
                }
            }
        });
    }

    @Transactional
    public EtaRegressionModel trainModel(UUID companyId) {
        List<EtaPrediction> historical = etaPredictionRepo
            .findByCompanyIdAndActualDaysNotNullAndPredictionAccuracyNotNull(companyId);

        if (historical.size() < 10) {
            log.info("[ETA-ML] Pas assez de données pour {} ({} < 10)", companyId, historical.size());
            return new EtaRegressionModel();
        }

        List<EtaRegressionModel.TrainingSample> samples = historical.stream()
            .map(this::toTrainingSample)
            .toList();

        EtaRegressionModel model = EtaRegressionModel.train(samples);
        if (!model.isTrained()) {
            log.warn("[ETA-ML] Modèle non entraîné pour {} (R²={})", companyId, model.getRSquared());
            return model;
        }

        saveModelCoefficients(companyId, model);
        log.info("[ETA-ML] Modèle entraîné pour {}: {} échantillons, R²={}",
            companyId, model.getTotalSamples(),
            BigDecimal.valueOf(model.getRSquared()).setScale(4, RoundingMode.HALF_UP));

        return model;
    }

    private EtaRegressionModel.TrainingSample toTrainingSample(EtaPrediction p) {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("mode", p.getMode() != null ? p.getMode() : "UNKNOWN");
        if (p.getCarrierName() != null) {
            features.put("carrier", p.getCarrierName());
        }
        if (p.getOrigin() != null && p.getOrigin().length() >= 2) {
            features.put("origin_region", p.getOrigin().substring(0, 2));
        }
        if (p.getDestination() != null && p.getDestination().length() >= 2) {
            features.put("dest_region", p.getDestination().substring(0, 2));
        }
        if (p.getCreatedAt() != null) {
            features.put("month", String.valueOf(p.getCreatedAt().getMonthValue()));
        }
        return new EtaRegressionModel.TrainingSample(features, p.getActualDays());
    }

    private void saveModelCoefficients(UUID companyId, EtaRegressionModel model) {
        List<EtaModelCoefficient> existing = coefficientRepo.findByCompanyIdAndIsActiveTrue(companyId);
        existing.forEach(c -> c.setActive(false));
        coefficientRepo.saveAll(existing);

        for (Map.Entry<String, Double> entry : model.getCoefficients().entrySet()) {
            EtaModelCoefficient coeff = EtaModelCoefficient.builder()
                .company(Company.builder().id(companyId).build())
                .featureName(entry.getKey().split(":")[0])
                .featureValue(entry.getKey().split(":", 2)[1])
                .coefficient(BigDecimal.valueOf(entry.getValue())
                    .setScale(6, RoundingMode.HALF_UP))
                .samplesCount(model.getTotalSamples())
                .isActive(true)
                .trainedAt(LocalDateTime.now())
                .intercept(BigDecimal.valueOf(model.getIntercept())
                    .setScale(6, RoundingMode.HALF_UP))
                .rSquared(model.rSquaredBigDecimal())
                .build();
            coefficientRepo.save(coeff);
        }
    }

    @Transactional(readOnly = true)
    public EtaRegressionModel loadModel(UUID companyId) {
        List<EtaModelCoefficient> coeffs = coefficientRepo.findByCompanyIdAndIsActiveTrue(companyId);
        if (coeffs.isEmpty()) {
            return new EtaRegressionModel();
        }

        Map<String, Double> coefficients = new HashMap<>();
        double intercept = 0;
        double rSquared = 0;
        int totalSamples = 0;

        for (EtaModelCoefficient c : coeffs) {
            String key = c.getFeatureName() + ":" + c.getFeatureValue();
            coefficients.put(key, c.getCoefficient().doubleValue());
            intercept = c.getIntercept().doubleValue();
            rSquared = c.getRSquared() != null ? c.getRSquared().doubleValue() : 0;
            totalSamples = c.getSamplesCount();
        }

        return new EtaRegressionModel(intercept, coefficients, rSquared, totalSamples);
    }
}
