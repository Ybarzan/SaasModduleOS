package com.incokalk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViesClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.vies.base-url:https://ec.europa.eu/taxation_customs/vies/rest-api}")
    private String baseUrl;

    @Value("${incokalk.vies.online-validation:false}")
    private boolean onlineValidation;

    public record ViesCheck(boolean valid, String name, String address, String message) {}

    @Cacheable(value = "vies-check", key = "#vatNumber")
    public ViesCheck checkVat(String vatNumber) {
        if (!onlineValidation) {
            return new ViesCheck(false, null, null, "VIES online validation disabled");
        }
        try {
            String url = baseUrl + "/check-vat/" + vatNumber;
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode node = objectMapper.readTree(resp.getBody());
                boolean valid = node.path("valid").asBoolean(false);
                String name = node.hasNonNull("name") ? node.path("name").asText() : null;
                String address = node.hasNonNull("address") ? node.path("address").asText() : null;
                return new ViesCheck(valid, name, address, null);
            }
            log.warn("[VIES] Réponse inattendue {} pour {}", resp.getStatusCode(), vatNumber);
        } catch (Exception e) {
            log.warn("[VIES] Échec de la vérification en ligne pour {}: {}", vatNumber, e.getMessage());
        }
        return new ViesCheck(false, null, null, "VIES online check failed");
    }
}
