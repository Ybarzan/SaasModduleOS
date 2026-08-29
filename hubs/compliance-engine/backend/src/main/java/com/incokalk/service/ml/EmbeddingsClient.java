package com.incokalk.service.ml;

import com.incokalk.config.EmbeddingsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the local, self-hosted Python embeddings service (embeddings-service,
 * FastAPI + sentence-transformers). Runs entirely on this machine — no product
 * description ever leaves the deployment.
 * <p>
 * Calls POST /v1/embeddings/encode. Used by SemanticClassificationService to add a
 * semantic-similarity source to HsCodeSuggestionService's existing blend of
 * keyword/ML/TARIC suggestions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingsClient {

    private final RestTemplate restTemplate;
    private final EmbeddingsConfig config;

    /**
     * Encode a batch of texts into embedding vectors.
     *
     * @return one vector per input text, in the same order, or an empty list if the
     *         service is disabled or unreachable — callers must treat that as
     *         "this source has nothing to contribute", never as an error.
     */
    @SuppressWarnings("unchecked")
    public List<float[]> encode(List<String> texts) {
        if (!config.isEnabled() || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String url = config.getBaseUrl() + "/v1/embeddings/encode";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of("texts", texts);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
                log.warn("Embeddings service returned status {}", response.getStatusCode());
                return Collections.emptyList();
            }

            List<List<Number>> rawVectors = (List<List<Number>>) responseBody.get("embeddings");
            if (rawVectors == null) {
                return Collections.emptyList();
            }

            return rawVectors.stream().map(this::toFloatArray).toList();

        } catch (Exception e) {
            log.warn("Embeddings service unavailable, semantic source skipped: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Convenience for the common single-text case (a user's product description). */
    public float[] encodeOne(String text) {
        List<float[]> result = encode(List.of(text));
        return result.isEmpty() ? null : result.get(0);
    }

    public boolean isHealthy() {
        try {
            String url = config.getBaseUrl() + "/health";
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private float[] toFloatArray(List<Number> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }
}
