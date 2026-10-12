package com.logistix.service.integration.fleethub;

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
 * <p><b>Limite connue (suivi documenté, identique côté compliance-engine) :</b>
 * fleet-hub résout son tenant courant à partir d'un JWT émis par fleet-hub
 * lui-même (voir son {@code TenantFilter}) ; aucun pont d'authentification
 * service-à-service inter-hub n'existe encore dans ce monorepo.
 * {@code companyId} est transmis tel quel (en-tête
 * {@code X-Internal-Company-Id}) à titre indicatif, mais cet appel échouera
 * en pratique (401, aucun JWT fleet-hub) jusqu'à ce qu'un tel pont existe --
 * {@link #getShipmentItems} propage l'échec comme
 * {@link FleetHubIntegrationException} plutôt que de le masquer, précisément
 * pour que l'appelant puisse retomber explicitement sur la saisie manuelle
 * des lignes (voir ReceivingService.createOrder) au lieu de silencieusement
 * ne rien faire. La sécurisation réelle de cet appel inter-hub est un suivi
 * séparé, hors scope de cette tâche.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetHubIntegrationClient {

    private final RestTemplate restTemplate;

    @Value("${app.integration.fleethub.base-url:http://localhost:8090}")
    private String baseUrl;

    /** Appelle {@code GET /api/shipments/{id}/items} sur fleet-hub. */
    public List<ShipmentItemSummary> getShipmentItems(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        if (companyId != null) {
            headers.set("X-Internal-Company-Id", companyId.toString());
        }

        String url = trimTrailingSlash(baseUrl) + "/api/shipments/" + shipmentId + "/items";
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
                    "Erreur d'appel à fleet-hub (/api/shipments/" + shipmentId + "/items) : " + e.getMessage(), e);
        }
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
