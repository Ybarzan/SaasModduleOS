package com.incokalk.service.ml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EtaRegressionModel {

    private double intercept;
    private final Map<String, Double> coefficients;
    private double rSquared;
    private int totalSamples;

    public EtaRegressionModel() {
        this.intercept = 0;
        this.coefficients = new HashMap<>();
        this.rSquared = 0;
        this.totalSamples = 0;
    }

    public EtaRegressionModel(double intercept, Map<String, Double> coefficients, double rSquared, int totalSamples) {
        this.intercept = intercept;
        this.coefficients = new HashMap<>(coefficients);
        this.rSquared = rSquared;
        this.totalSamples = totalSamples;
    }

    public double predict(Map<String, String> features) {
        double prediction = intercept;
        for (Map.Entry<String, String> feature : features.entrySet()) {
            String key = feature.getKey() + ":" + feature.getValue();
            prediction += coefficients.getOrDefault(key, 0.0);
        }
        return Math.max(1, Math.round(prediction));
    }

    public static EtaRegressionModel train(List<TrainingSample> samples) {
        if (samples.size() < 10) {
            return new EtaRegressionModel();
        }

        double meanActual = samples.stream()
            .mapToDouble(TrainingSample::actualDays)
            .average()
            .orElse(0);

        Map<String, Double> featureAvgActual = new HashMap<>();
        Map<String, Integer> featureCount = new HashMap<>();
        Map<String, Double> featureAvgAll = new HashMap<>();

        for (TrainingSample sample : samples) {
            for (Map.Entry<String, String> feat : sample.features().entrySet()) {
                String key = feat.getKey() + ":" + feat.getValue();
                Double existing = featureAvgActual.get(key);
                featureAvgActual.put(key, (existing != null ? existing : 0.0) + sample.actualDays());
                Integer cnt = featureCount.get(key);
                featureCount.put(key, (cnt != null ? cnt : 0) + 1);

                double allSum = featureAvgAll.getOrDefault(key, 0.0);
                featureAvgAll.put(key, allSum + meanActual);
            }
        }

        Map<String, Double> coefficients = new HashMap<>();
        for (Map.Entry<String, Double> entry : featureAvgActual.entrySet()) {
            String key = entry.getKey();
            int count = featureCount.get(key);
            double avgActual = entry.getValue() / count;
            double avgAll = featureAvgAll.getOrDefault(key, 0.0);
            coefficients.put(key, avgActual - avgAll);
        }

        int totalSamples = samples.size();
        double sumSquaredResiduals = 0;
        double sumSquaredTotal = 0;

        for (TrainingSample sample : samples) {
            double predicted = meanActual;
            for (Map.Entry<String, String> feat : sample.features().entrySet()) {
                String key = feat.getKey() + ":" + feat.getValue();
                predicted += coefficients.getOrDefault(key, 0.0);
            }
            predicted = Math.max(1, predicted);

            double residual = sample.actualDays() - predicted;
            sumSquaredResiduals += residual * residual;

            double totalDiff = sample.actualDays() - meanActual;
            sumSquaredTotal += totalDiff * totalDiff;
        }

        double rSquared = sumSquaredTotal > 0
            ? 1 - (sumSquaredResiduals / sumSquaredTotal)
            : 0;

        return new EtaRegressionModel(meanActual, coefficients, rSquared, totalSamples);
    }

    public double getIntercept() { return intercept; }
    public Map<String, Double> getCoefficients() { return coefficients; }
    public double getRSquared() { return rSquared; }
    public int getTotalSamples() { return totalSamples; }
    public boolean isTrained() { return totalSamples >= 10 && rSquared > 0; }

    public BigDecimal rSquaredBigDecimal() {
        return BigDecimal.valueOf(rSquared).setScale(6, RoundingMode.HALF_UP);
    }

    public record TrainingSample(
        Map<String, String> features,
        double actualDays
    ) {}
}
