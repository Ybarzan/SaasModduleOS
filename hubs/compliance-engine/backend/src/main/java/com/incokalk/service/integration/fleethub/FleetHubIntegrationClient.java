package com.incokalk.service.integration.fleethub;

import com.incokalk.dto.shipment.PackagingRequest;
import com.incokalk.dto.shipment.PackagingResult;
import com.incokalk.dto.shipment.SimulationRequest.TransportModeInput;
import com.incokalk.dto.shipment.TruckingRateRequest;
import com.incokalk.dto.shipment.TruckingRateResult;
import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * Client REST pour fleet-hub (service indépendant, sa propre base -- même
 * principe que l'ancien service/fleethub/FleetHubClient, retiré avec le reste
 * du domaine Transport). Ne se connecte jamais directement à la base de
 * fleet-hub, toujours via son API HTTP.
 *
 * <p>Remplace, côté compliance-engine, les deux capacités que le domaine
 * Transport fournissait localement avant son retrait :</p>
 * <ul>
 *   <li>l'estimation fret/packaging (ex-{@code FreightRateService}/{@code PackagingService}),
 *       utilisée par {@code SimulationService} et {@code LogisticsController} ;</li>
 *   <li>le résumé d'expédition par ID (ex-lecture directe de
 *       {@code ShipmentOrderRepository}/{@code ShipmentItemRepository}), utilisé par
 *       {@code CustomsInvoiceGeneratorService}.</li>
 * </ul>
 *
 * <p><b>Authentification inter-hub :</b> fleet-hub résout son tenant courant à
 * partir d'un JWT émis par lui-même (voir son {@code TenantFilter}) ; compliance-engine
 * n'en a pas. {@link #getShipmentSummary} s'authentifie donc via une clé propre
 * à chaque société (en-tête {@code X-Internal-Integration-Key}), générée côté
 * fleet-hub quand celle-ci active le partage ("Logistix & Compliance Engine"
 * dans ses paramètres) puis collée ici (voir {@code Company.fleetHubApiKey} /
 * paramètres entreprise). fleet-hub résout la société depuis cette clé, jamais
 * depuis un identifiant fourni par l'appel -- une société dont la clé n'est pas
 * configurée reçoit une erreur explicite plutôt qu'un appel anonyme qui
 * échouerait en 401 côté fleet-hub.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetHubIntegrationClient {

    /** Pays "nearshore" pour lesquels le mode routier est privilégié quand le mode
     * n'est pas précisé -- identique à l'ancien FreightRateService.guess(). */
    private static final Set<String> NEARSHORE = Set.of("MA", "TN", "DZ", "TR", "PL", "RO", "HU", "BG");

    private final RestTemplate restTemplate;
    private final CompanyRepository companyRepository;

    @Value("${incokalk.integration.fleethub.base-url:http://localhost:8090}")
    private String baseUrl;

    public record FreightEstimate(double cost, int days) {}

    /** Pour MultiBranchService (consolidation inter-sociétés). */
    public record FleetHubDashboardSummary(long activeShipments, long availableTrucks, long openCarrierBookings) {}

    /**
     * Remplace FreightRateService.estimate(...) : devine le mode de transport
     * côté compliance-engine quand il n'est pas fourni (même logique qu'avant le
     * retrait du domaine Transport), puis appelle l'endpoint de tarification
     * routière de fleet-hub pour obtenir coût/délai.
     */
    public FreightEstimate estimateFreight(String originCountry, String destinationCountry,
            TransportModeInput mode, Double weightKg, Double volumeM3, double goodsValueEur) {
        TransportModeInput resolvedMode = mode != null ? mode : guessMode(originCountry, goodsValueEur);

        TruckingRateRequest request = new TruckingRateRequest();
        request.setOriginCountry(originCountry);
        request.setDestinationCountry(destinationCountry);
        request.setWeightKg(weightKg);
        request.setVolumeM3(volumeM3);
        request.setGoodsValue(goodsValueEur);
        request.setCurrency("EUR");

        TruckingRateResult result = calculateTruckingRates(request);
        TruckingRateResult.TruckOption recommended = result.getRecommended();
        if (recommended == null) {
            throw new FleetHubIntegrationException(
                    "Réponse de fleet-hub sans option de transport recommandée pour " + resolvedMode);
        }
        return new FreightEstimate(recommended.getCostEur(), recommended.getTransitDays());
    }

    /** Devine le mode de transport quand il n'est pas fourni -- identique à
     * l'ancien FreightRateService.guess() : nearshore -> ROAD, > 50000€ -> AIR,
     * sinon SEA. */
    public TransportModeInput guessMode(String originCountry, double goodsValueEur) {
        String upper = originCountry != null ? originCountry.toUpperCase() : "";
        if (NEARSHORE.contains(upper)) return TransportModeInput.ROAD;
        if (goodsValueEur > 50000) return TransportModeInput.AIR;
        return TransportModeInput.SEA;
    }

    /** Appelle {@code POST /api/logistics/trucking} sur fleet-hub. */
    public TruckingRateResult calculateTruckingRates(TruckingRateRequest request) {
        String url = trimTrailingSlash(baseUrl) + "/api/logistics/trucking";
        HttpHeaders headers = jsonHeaders();
        try {
            ResponseEntity<TruckingRateResult> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), TruckingRateResult.class);
            TruckingRateResult body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FleetHubIntegrationException(
                        "Échec de l'appel tarification routière fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return body;
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException("Erreur d'appel à fleet-hub (/api/logistics/trucking) : " + e.getMessage(), e);
        }
    }

    /** Remplace PackagingService.calculatePackaging(...) : appelle
     * {@code POST /api/logistics/packaging} sur fleet-hub. */
    public PackagingResult calculatePackaging(PackagingRequest request) {
        String url = trimTrailingSlash(baseUrl) + "/api/logistics/packaging";
        HttpHeaders headers = jsonHeaders();
        try {
            ResponseEntity<PackagingResult> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), PackagingResult.class);
            PackagingResult body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FleetHubIntegrationException(
                        "Échec de l'appel packaging fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return body;
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException("Erreur d'appel à fleet-hub (/api/logistics/packaging) : " + e.getMessage(), e);
        }
    }

    /**
     * Pour MultiBranchService.getConsolidatedReport() (consolidation
     * inter-sociétés -- voir docs/03, retrait du domaine Transport) : appelle
     * {@code GET /api/internal/dashboard-summary} sur fleet-hub, le même
     * endpoint que celui déjà consommé côté Finance/Portal pour le tableau de
     * bord cross-hub Praxio.
     */
    public FleetHubDashboardSummary getDashboardSummary(UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/dashboard-summary";
        try {
            ResponseEntity<FleetHubDashboardSummary> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), FleetHubDashboardSummary.class);
            FleetHubDashboardSummary body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FleetHubIntegrationException(
                        "Échec de l'appel résumé fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return body;
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/dashboard-summary) : " + e.getMessage(), e);
        }
    }

    /**
     * Remplace la lecture directe de ShipmentOrderRepository/ShipmentItemRepository
     * dans CustomsInvoiceGeneratorService.generateInvoice() : appelle
     * {@code GET /api/internal/shipments/{id}} (en-tête) puis
     * {@code GET /api/internal/shipments/{id}/items} (lignes) sur fleet-hub,
     * authentifié par la clé d'intégration de cette société, et reconstitue
     * un {@link ShipmentSummary} local avec exactement les champs dont la
     * génération de facture douanière a besoin.
     */
    public ShipmentSummary getShipmentSummary(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String shipmentUrl = trimTrailingSlash(baseUrl) + "/api/internal/shipments/" + shipmentId;
        ShipmentSummary summary;
        try {
            ResponseEntity<ShipmentSummary> response = restTemplate.exchange(
                    shipmentUrl, HttpMethod.GET, new HttpEntity<>(headers), ShipmentSummary.class);
            summary = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || summary == null) {
                throw new FleetHubIntegrationException(
                        "Expédition introuvable sur fleet-hub (statut " + response.getStatusCode() + ")");
            }
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments/" + shipmentId + ") : " + e.getMessage(), e);
        }

        String itemsUrl = shipmentUrl + "/items";
        try {
            ResponseEntity<ShipmentSummary.Item[]> response = restTemplate.exchange(
                    itemsUrl, HttpMethod.GET, new HttpEntity<>(headers), ShipmentSummary.Item[].class);
            ShipmentSummary.Item[] items = response.getBody();
            summary.setItems(items != null ? java.util.List.of(items) : java.util.List.of());
        } catch (Exception e) {
            log.warn("[FleetHub] Impossible de récupérer les lignes de l'expédition {} : {}", shipmentId, e.getMessage());
            summary.setItems(java.util.List.of());
        }

        return summary;
    }

    /**
     * Appelle {@code POST /api/internal/shipments} sur fleet-hub (ADR-0002) pour
     * créer une expédition brouillon (toujours DRAFT côté fleet-hub). Nécessite
     * en plus que la société ait activé l'opt-in dédié "création externe" côté
     * fleet-hub (403 explicite sinon, voir FleetHubIntegrationException) --
     * séparé du partage en lecture dont {@link #getShipmentSummary} dépend.
     *
     * @return l'UUID fleet-hub de l'expédition créée.
     */
    public UUID createShipmentFromExternalSource(ExternalShipmentCreationRequest request, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments";
        try {
            ResponseEntity<CreatedShipmentRef> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), CreatedShipmentRef.class);
            CreatedShipmentRef body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.id() == null) {
                throw new FleetHubIntegrationException(
                        "Échec de la création d'expédition sur fleet-hub (statut " + response.getStatusCode() + ")");
            }
            return body.id();
        } catch (FleetHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (POST /api/internal/shipments) : " + e.getMessage(), e);
        }
    }

    /** Vue minimale de la réponse de création -- fleet-hub renvoie le
     * ShipmentOrder complet, seul l'id nous intéresse ici. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedShipmentRef(UUID id) {}

    /**
     * Restaure l'app mobile (GET /v1/shipments/{id}, voir ShipmentDetailController) :
     * appelle le même {@code GET /api/internal/shipments/{id}} que {@link #getShipmentSummary},
     * mais mappé vers {@link ShipmentDetailDto} (plus riche : coûts, douane, suivi --
     * nécessaires à l'écran de détail mobile, pas à la génération de facture douanière).
     */
    public ShipmentDetailDto getShipmentDetail(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments/" + shipmentId;
        try {
            ResponseEntity<ShipmentDetailDto> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ShipmentDetailDto.class);
            ShipmentDetailDto body = response.getBody();
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

    /** Restaure l'app mobile (GET /v1/shipments/{id}/items) -- même endpoint que
     * {@link #getShipmentSummary} utilise pour ses lignes, mappé vers {@link ShipmentItemDetailDto}
     * (id/name/description/originCountry en plus). */
    public List<ShipmentItemDetailDto> getShipmentItemsDetailed(UUID shipmentId, UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments/" + shipmentId + "/items";
        try {
            ResponseEntity<ShipmentItemDetailDto[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ShipmentItemDetailDto[].class);
            ShipmentItemDetailDto[] body = response.getBody();
            return body != null ? List.of(body) : List.of();
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments/" + shipmentId + "/items) : " + e.getMessage(), e);
        }
    }

    /**
     * Restaure l'app mobile (GET /v1/mobile/recent-shipments, voir
     * MobileDashboardService.getRecentShipments) : appelle le nouvel endpoint de liste
     * {@code GET /api/internal/shipments} (ADR-0002/chantier mobile, jusqu'à 50 expéditions
     * les plus récentes de cette société).
     */
    public List<RecentShipmentDto> listRecentShipments(UUID companyId) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + "/api/internal/shipments";
        try {
            ResponseEntity<RecentShipmentDto[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), RecentShipmentDto[].class);
            RecentShipmentDto[] body = response.getBody();
            return body != null ? List.of(body) : List.of();
        } catch (Exception e) {
            throw new FleetHubIntegrationException(
                    "Erreur d'appel à fleet-hub (/api/internal/shipments) : " + e.getMessage(), e);
        }
    }

    /**
     * Recherche en direct d'expéditions par texte (numéro de commande ou nom du
     * destinataire) -- appelle {@code GET /api/internal/shipments?q=...} sur
     * fleet-hub, pour l'autocomplétion (CustomsInvoices.tsx notamment, qui
     * n'avait jusque-là qu'une saisie manuelle d'UUID).
     */
    public List<RecentShipmentDto> searchShipments(UUID companyId, String query) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(baseUrl) + "/api/internal/shipments")
                .queryParam("q", query)
                .toUriString();
        try {
            ResponseEntity<RecentShipmentDto[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), RecentShipmentDto[].class);
            RecentShipmentDto[] body = response.getBody();
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
                    "Intégration fleet-hub non configurée pour cette entreprise (voir Paramètres > Intégrations)");
        }
        return apiKey;
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
