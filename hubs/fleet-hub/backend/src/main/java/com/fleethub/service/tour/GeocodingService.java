package com.fleethub.service.tour;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Géocodage d'adresses françaises via la Base Adresse Nationale
 * (api-adresse.data.gouv.fr : service public, gratuit, sans clé).
 * Évite la saisie manuelle des coordonnées GPS des sites.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean enabled;

    public GeocodingService(RestClient restClient,
                            @Value("${app.geocoding.base-url:https://api-adresse.data.gouv.fr/search/}") String baseUrl,
                            @Value("${app.geocoding.enabled:true}") boolean enabled) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public record GeocodeResult(String label, String address, String postalCode, String city,
                                double latitude, double longitude, double score) {}

    public List<GeocodeResult> search(String query) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Géocodage désactivé");
        }
        if (query == null || query.trim().length() < 3) {
            throw new IllegalArgumentException("Saisissez au moins 3 caractères");
        }
        String uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("q", query.trim())
                .queryParam("limit", 5)
                .build().toUriString();
        JsonNode body;
        try {
            body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Géocodage indisponible : {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service de géocodage indisponible");
        }
        List<GeocodeResult> results = new ArrayList<>();
        if (body == null || !body.has("features")) {
            return results;
        }
        for (JsonNode f : body.get("features")) {
            JsonNode coords = f.path("geometry").path("coordinates");
            JsonNode p = f.path("properties");
            if (coords.size() < 2) continue;
            results.add(new GeocodeResult(
                    p.path("label").asText(),
                    p.path("name").asText(null),
                    p.path("postcode").asText(null),
                    p.path("city").asText(null),
                    coords.get(1).asDouble(),   // GeoJSON : [longitude, latitude]
                    coords.get(0).asDouble(),
                    p.path("score").asDouble()));
        }
        return results;
    }
}
