package com.fleethub.service.ml;

import com.fleethub.config.EtaMlConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client pour le service ML Python externe de prédiction de délai maritime
 * (facultatif — désactivé par défaut tant qu'aucun service n'est déployé).
 * Appelle POST /v1/eta-predictions/predict et renvoie une Map brute compatible
 * avec les champs de {@link com.fleethub.model.EtaPrediction}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtaMlClient {

    private final RestTemplate restTemplate;
    private final EtaMlConfig config;

    public Map<String, Object> predict(String origin, String destination, String mode, String carrierName) {
        if (!config.isEnabled()) {
            log.debug("ML ETA prediction disabled, skipping");
            return null;
        }

        try {
            String url = config.getBaseUrl() + "/v1/eta-predictions/predict";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                "origin", origin != null ? origin : "",
                "destination", destination != null ? destination : "",
                "mode", mode != null ? mode : "SEA",
                "carrierName", carrierName != null ? carrierName : ""
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                log.info("ML prediction OK for {} -> {}: predictedDays={}",
                    origin, destination, responseBody.get("predictedDays"));
                return responseBody;
            }

            log.warn("ML prediction returned status {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.warn("ML service unavailable, falling back to heuristic: {}", e.getMessage());
            return null;
        }
    }

    public boolean isHealthy() {
        try {
            String url = config.getBaseUrl() + "/health";
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map respBody = resp.getBody();
            if (resp.getStatusCode().is2xxSuccessful() && respBody != null) {
                Object modelLoaded = respBody.get("model_loaded");
                return Boolean.TRUE.equals(modelLoaded);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
