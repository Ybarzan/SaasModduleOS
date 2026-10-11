package com.fleethub.controller;

import com.fleethub.config.EtaMlConfig;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ml.EtaMlClient;
import com.fleethub.service.ml.EtaRegressionModel;
import com.fleethub.service.ml.EtaTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modèle de prédiction ETA par Machine Learning — porté depuis compliance-engine.
 * Gating : {@code /train} et {@code /external-health} réservés à ADMIN (l'original
 * les réservait à OWNER/ADMIN) ; {@code /model} reste sous la règle générique
 * ADMIN/GESTIONNAIRE de SecurityConfig.
 */
@RestController
@RequestMapping("/api/eta-ml")
@RequiredArgsConstructor
@Tag(name = "ETA ML", description = "Modèle de prédiction ETA par Machine Learning")
public class EtaMlController {

    private final EtaTrainingService etaTrainingService;
    private final EtaMlClient etaMlClient;
    private final EtaMlConfig etaMlConfig;

    @PostMapping("/train")
    @Operation(summary = "Entraîner le modèle ML ETA pour cette entreprise")
    public ResponseEntity<Map<String, Object>> trainModel() {
        Long companyId = TenantContext.companyId();
        EtaRegressionModel model = etaTrainingService.trainModel(companyId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", model.isTrained() ? "trained" : "insufficient_data");
        resp.put("samples", model.getTotalSamples());
        resp.put("rSquared", model.getRSquared());
        resp.put("intercept", model.getIntercept());
        resp.put("features", model.getCoefficients().size());
        resp.put("isTrained", model.isTrained());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/model")
    @Operation(summary = "Obtenir l'état du modèle ML ETA")
    public ResponseEntity<Map<String, Object>> getModelInfo() {
        Long companyId = TenantContext.companyId();
        EtaRegressionModel model = etaTrainingService.loadModel(companyId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("isTrained", model.isTrained());
        resp.put("samples", model.getTotalSamples());
        resp.put("rSquared", model.getRSquared());
        resp.put("intercept", model.getIntercept());
        resp.put("coefficients", model.getCoefficients());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/external-health")
    @Operation(summary = "Vérifier la santé du service ML Python externe")
    public ResponseEntity<Map<String, Object>> getExternalMlHealth() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("enabled", etaMlConfig.isEnabled());
        resp.put("baseUrl", etaMlConfig.getBaseUrl());
        resp.put("blendWeight", etaMlConfig.getBlendWeight());

        boolean healthy = etaMlClient.isHealthy();
        resp.put("reachable", healthy);
        resp.put("status", healthy ? "OK" : "UNREACHABLE");

        return ResponseEntity.ok(resp);
    }
}
