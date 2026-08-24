package com.incokalk.service.fleethub;

import com.incokalk.model.FleetHubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client REST pour fleet-hub (service indépendant, sa propre base -- voir
 * docs/07-integration-fleet-hub.md). Ne se connecte jamais directement à sa
 * base de données, toujours via son API existante (POST /api/auth/login puis
 * GET /api/map/vehicles avec le token obtenu).
 *
 * Pas de cache de token dans cette première version : un login par appel,
 * volontairement simple -- à optimiser seulement si la fréquence d'appel le
 * justifie réellement (mesurer avant d'anticiper, même principe que le reste
 * du projet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetHubClient {

    private final RestTemplate restTemplate;

    public String login(FleetHubConfig config) {
        String url = trimTrailingSlash(config.getBaseUrl()) + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", config.getUsername(), "password", config.getPassword());

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<?, ?> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new FleetHubException("Échec de connexion à fleet-hub (statut " + response.getStatusCode() + ")");
        }
        if (Boolean.TRUE.equals(responseBody.get("totpRequired"))) {
            throw new FleetHubException(
                    "Le compte de service fleet-hub a la double authentification (2FA) activée -- désactivez-la pour ce compte, l'intégration ne peut pas la compléter");
        }
        Object token = responseBody.get("token");
        if (!(token instanceof String tokenStr) || tokenStr.isBlank()) {
            throw new FleetHubException("Réponse de connexion fleet-hub sans token");
        }
        return tokenStr;
    }

    public List<FleetHubVehicle> getVehicles(FleetHubConfig config) {
        String token = login(config);
        String url = trimTrailingSlash(config.getBaseUrl()) + "/api/map/vehicles";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<FleetHubVehicle[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), FleetHubVehicle[].class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new FleetHubException("Échec de récupération des véhicules fleet-hub (statut " + response.getStatusCode() + ")");
        }
        return List.of(response.getBody());
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static class FleetHubException extends RuntimeException {
        public FleetHubException(String message) {
            super(message);
        }
    }
}
