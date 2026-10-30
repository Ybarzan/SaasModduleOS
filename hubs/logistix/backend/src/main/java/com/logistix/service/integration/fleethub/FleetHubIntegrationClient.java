package com.logistix.service.integration.fleethub;

import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Client REST pour fleet-hub (service indépendant, sa propre base -- même
 * principe que {@code com.incokalk.service.integration.fleethub.FleetHubIntegrationClient}
 * côté compliance-engine). Ne se connecte jamais directement à la base de
 * fleet-hub, toujours via son API HTTP.
 *
 * <p>Remplace, côté Logistix, la dérivation automatique de lignes de
 * réception depuis une expédition -- capacité qu'offrait l'ancien
 * compliance-engine via une lecture JPA cross-domain directe de
 * ShipmentOrder/ShipmentItem, éliminée par cette extraction (voir le TODO
 * historique de {@code ReceivingService.createOrder}).</p>
 *
 * <p><b>Authentification inter-hub :</b> fleet-hub résout son tenant courant à
 * partir d'un JWT émis par lui-même (voir son {@code TenantFilter}) ; Logistix
 * n'en a pas. {@link #getShipmentItems} s'authentifie donc via une clé propre
 * à chaque société (en-tête {@code X-Internal-Integration-Key}), générée côté
 * fleet-hub quand celle-ci active le partage ("Logistix & Compliance Engine"
 * dans ses paramètres) puis collée ici (voir {@code Company.fleetHubApiKey} /
 * paramètres entreprise). fleet-hub résout la société depuis cette clé, jamais
 * depuis un identifiant fourni par l'appel -- une société dont la clé n'est
 * pas configurée reçoit une erreur explicite plutôt qu'un appel anonyme qui
 * échouerait en 401 côté fleet-hub.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetHubIntegrationClient {

    private final RestTemplate restTemplate;
    private final CompanyRepository companyRepository;

    @Value("${app.integration.fleethub.base-url:http://localhost:8090}")
    private String baseUrl;

    /** Appelle {@code GET /api/internal/shipments/{id}/items} sur fleet-hub,
     * authentifié par la clé d'intégration de cette société. */
    public List<ShipmentItemSummary> getShipmentItems(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments/" + shipmentId + "/items";
        try {
            ResponseEntity<ShipmentItemSummary[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ShipmentItemSummary[].class);
            ShipmentItemSummary[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FleetHubIntegrationException(
                        "Échec de l'appel lignes d'expédition fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return List.of(body);
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments/" + shipmentId + "/items) : " + e.getMessage(), e);
        }
    }

    /**
     * Recherche en direct d'expéditions par texte (numéro de commande ou nom du
     * destinataire) -- appelle {@code GET /api/internal/shipments?q=...} sur
     * fleet-hub, pour l'autocomplétion (Receiving.tsx, qui n'avait jusque-là
     * qu'une saisie manuelle d'UUID -- même endpoint que celui ajouté côté
     * compliance-engine pour CustomsInvoices.tsx).
     */
    public List<ShipmentSearchResult> searchShipments(UUID companyId, String query) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(baseUrl) + "/api/internal/shipments")
                .queryParam("q", query)
                .toUriString();
        try {
            ResponseEntity<ShipmentSearchResult[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ShipmentSearchResult[].class);
            ShipmentSearchResult[] body = response.getBody();
            return body != null ? List.of(body) : List.of();
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments?q=) : " + e.getMessage(), e);
        }
    }

    /** Résout la clé d'intégration fleet-hub de cette société -- jamais un
     * identifiant de société transmis tel quel : fleet-hub ne fait confiance
     * qu'à la clé elle-même pour déterminer qui appelle. */
    private String resolveApiKey(UUID companyId) {
        String apiKey = companyId != null
                ? companyRepository.findById(companyId).map(Company::getFleetHubApiKey).orElse(null)
                : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new FleetHubIntegrationException(
                    "Intégration fleet-hub non configurée pour cette société (voir Paramètres)");
        }
        return apiKey;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static class FleetHubIntegrationException extends RuntimeException {
        public FleetHubIntegrationException(String message) {
            super(message);
        }

        public FleetHubIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
