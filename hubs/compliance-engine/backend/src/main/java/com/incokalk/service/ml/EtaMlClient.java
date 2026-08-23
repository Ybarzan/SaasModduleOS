package com.incokalk.service.ml;

import com.incokalk.config.EtaMlConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Client for the Python ML maritime delay prediction service.
 * <p>
 * Calls POST /v1/eta-predictions/predict on the maritime-delay-predictor FastAPI
 * and returns a raw Map matching IncoKalk's EtaPrediction fields.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtaMlClient {

    private final RestTemplate restTemplate;
    private final EtaMlConfig config;

    /**
     * Call the Python ML service for an ETA prediction.
     *
     * @param origin      origin port UNO code (e.g. "CNSHA")
     * @param destination destination port UNO code (e.g. "NLRTM")
     * @param mode        transport mode (SEA, AIR, ROAD, RAIL)
     * @param carrierName carrier name (nullable)
     * @return prediction map with EtaPrediction-compatible keys, or null if unavailable
     */
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

    /**
     * Check if the ML service is reachable.
     */
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
