package com.incokalk.service.insurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.financial.CargoInsuranceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client HTTP vers un courtier / assureur externe.
 * Renvoie un facteur de marché qui ajuste le taux de base local.
 * Si non configuré ou en erreur, la tarification locale s'applique (factor = 1.0).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CargoInsurerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.insurance.base-url:https://api.insurer.example.com}")
    private String baseUrl;

    @Value("${incokalk.insurance.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<Double> fetchMarketRateFactor(CargoInsuranceRequest request) {
        if (!isConfigured()) {
            log.info("[INSURER] Non configuré, tarification locale");
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("goodsValue", request.getGoodsValue());
            body.put("weightKg", request.getWeightKg());
            body.put("transportMode", request.getTransportMode());
            body.put("goodsCategory", request.getGoodsCategory());
            body.put("originCountry", request.getOriginCountry());
            body.put("destinationCountry", request.getDestinationCountry());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = trimTrailingSlash(baseUrl) + "/v1/quotes/market-factor";
            log.info("[INSURER] POST {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("marketFactor") && root.path("marketFactor").isNumber()) {
                    double factor = root.path("marketFactor").asDouble(1.0);
                    return factor > 0 ? Optional.of(factor) : Optional.empty();
                }
            }
            log.warn("[INSURER] Réponse inattendue: {}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("[INSURER] Erreur appel externe: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
