package com.incokalk.controller.shipment;

import com.incokalk.config.EtaMlConfig;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ml.EtaMlClient;
import com.incokalk.service.ml.EtaRegressionModel;
import com.incokalk.service.ml.EtaTrainingService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/eta-ml")
@RequiredArgsConstructor
@Tag(name = "ETA ML", description = "Modèle de prédiction ETA par Machine Learning")
public class EtaMlController {

    private final EtaTrainingService etaTrainingService;
    private final EtaMlClient etaMlClient;
    private final EtaMlConfig etaMlConfig;

    @PostMapping("/train")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Entraîner le modèle ML ETA pour cette entreprise")
    public ResponseEntity<Map<String, Object>> trainModel() {
        UUID companyId = TenantContext.get();
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
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir l'état du modèle ML ETA")
    public ResponseEntity<Map<String, Object>> getModelInfo() {
        UUID companyId = TenantContext.get();
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
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
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
