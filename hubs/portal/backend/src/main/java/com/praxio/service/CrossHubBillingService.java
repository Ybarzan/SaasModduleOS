package com.praxio.service;

import com.praxio.config.HubUrlsProperties;
import com.praxio.dto.ComplianceEngineBillingSummaryDto;
import com.praxio.dto.CrossHubBillingDto;
import com.praxio.dto.FleetHubBillingSummaryDto;
import com.praxio.dto.HubStatus;
import com.praxio.model.DashboardConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Agrège les résumés de facturation des hubs pour la vue "facturation
 * groupée" de Praxio -- lecture seule, aucune donnée de paiement modifiée :
 * réutilise exactement les clés déjà stockées pour le tableau de bord
 * cross-hub (DashboardConfig), même dégradation gracieuse hub par hub que
 * {@link CrossHubDashboardService}. Logistix n'a pas de système de
 * facturation propre : NOT_APPLICABLE plutôt qu'un appel réseau inutile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossHubBillingService {

    private final RestTemplate restTemplate;
    private final DashboardConfigService dashboardConfigService;
    private final HubUrlsProperties hubUrls;

    public CrossHubBillingDto getBilling() {
        DashboardConfig config = dashboardConfigService.getConfigOrEmpty();

        HubStatus<ComplianceEngineBillingSummaryDto> complianceEngine = fetch(
                config.getComplianceEngineApiKey(),
                "X-API-Key",
                hubUrls.complianceEngineUrl() + "/api/v1/billing/summary",
                ComplianceEngineBillingSummaryDto.class,
                "compliance-engine");

        HubStatus<FleetHubBillingSummaryDto> fleetHub = fetch(
                config.getFleetHubApiKey(),
                "X-Internal-Integration-Key",
                hubUrls.fleetHubUrl() + "/api/internal/billing-summary",
                FleetHubBillingSummaryDto.class,
                "fleet-hub");

        return new CrossHubBillingDto(complianceEngine, fleetHub, HubStatus.notApplicable());
    }

    private <T> HubStatus<T> fetch(String apiKey, String headerName, String url, Class<T> responseType, String hubName) {
        if (apiKey == null || apiKey.isBlank()) {
            return HubStatus.notConfigured();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(headerName, apiKey);
            T body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType).getBody();
            return body != null ? HubStatus.ok(body) : HubStatus.unreachable();
        } catch (RestClientException e) {
            log.warn("[Billing] Appel {} indisponible ({}) : {}", hubName, url, e.getMessage());
            return HubStatus.unreachable();
        }
    }
}
