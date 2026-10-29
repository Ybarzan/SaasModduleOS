package com.finance.service.integration.fleethub;

import com.finance.model.Company;
import com.finance.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client REST pour fleet-hub (service indépendant, sa propre base), même
 * principe que les clients homonymes de compliance-engine et hubs/logistix.
 * Ne se connecte jamais directement à la base de fleet-hub, toujours via son
 * API HTTP.
 *
 * <p>Utilisé par {@code CsrdReportingService} pour résoudre la ville
 * shipper/consignee d'une expédition (regroupement par lane dans le rapport
 * CSRD) -- capacité retirée avec le domaine Transport (CarbonOffset.shipment
 * était une relation JPA locale, {@code CarbonOffset.shipmentId} est
 * désormais un UUID brut, voir migration V9 et la javadoc de Company).</p>
 *
 * <p><b>Authentification inter-hub :</b> comme pour compliance-engine et
 * Logistix, authentifié via une clé propre à chaque société (en-tête
 * {@code X-Internal-Integration-Key}), générée côté fleet-hub quand celle-ci
 * active le partage puis collée ici (voir {@code Company.fleetHubApiKey} /
 * CompanySettingsController). fleet-hub résout la société depuis cette clé,
 * jamais depuis un identifiant fourni par l'appel.</p>
 */
@Component
@RequiredArgsConstructor
public class FleetHubIntegrationClient {

    private final RestTemplate restTemplate;
    private final CompanyRepository companyRepository;

    @Value("${app.integration.fleethub.base-url:http://localhost:8090}")
    private String baseUrl;

    /**
     * Appelle {@code GET /api/internal/shipments/{id}} sur fleet-hub,
     * authentifié par la clé d'intégration de cette société.
     */
    public ShipmentLaneSummary getShipmentSummary(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments/" + shipmentId;
        try {
            ResponseEntity<ShipmentLaneSummary> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ShipmentLaneSummary.class);
            ShipmentLaneSummary body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FleetHubIntegrationException(
                        "Expédition introuvable sur fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return body;
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments/" + shipmentId + ") : " + e.getMessage(), e);
        }
    }

    /** true si cette société a une clé d'intégration fleet-hub enregistrée --
     * permet à CsrdReportingService d'éviter un appel (et une exception) par
     * expédition pour une société qui n'a tout simplement rien configuré. */
    public boolean isConfigured(UUID companyId) {
        String apiKey = findApiKey(companyId);
        return apiKey != null && !apiKey.isBlank();
    }

    /** Résout la clé d'intégration fleet-hub de cette société -- jamais un
     * identifiant de société transmis tel quel : fleet-hub ne fait confiance
     * qu'à la clé elle-même pour déterminer qui appelle. */
    private String resolveApiKey(UUID companyId) {
        String apiKey = findApiKey(companyId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new FleetHubIntegrationException(
                    "Intégration fleet-hub non configurée pour cette entreprise (voir Paramètres > Intégrations)");
        }
        return apiKey;
    }

    private String findApiKey(UUID companyId) {
        return companyId != null
                ? companyRepository.findById(companyId).map(Company::getFleetHubApiKey).orElse(null)
                : null;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
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
