package com.praxio.service;

import com.praxio.config.HubUrlsProperties;
import com.praxio.dto.ComplianceEngineSummaryDto;
import com.praxio.dto.CrossHubDashboardDto;
import com.praxio.dto.FinanceSummaryDto;
import com.praxio.dto.FleetHubSummaryDto;
import com.praxio.dto.HubStatus;
import com.praxio.dto.LogistixSummaryDto;
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
 * Agrège les résumés d'activité des 3 hubs pour le tableau de bord de
 * Praxio. Chaque hub est appelé indépendamment : l'échec ou l'absence de
 * configuration de l'un n'empêche jamais d'afficher les deux autres
 * (dégradation gracieuse -- jamais une erreur 500 globale).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossHubDashboardService {

    private final RestTemplate restTemplate;
    private final DashboardConfigService dashboardConfigService;
    private final HubUrlsProperties hubUrls;

    public CrossHubDashboardDto getDashboard() {
        DashboardConfig config = dashboardConfigService.getConfigOrEmpty();

        HubStatus<ComplianceEngineSummaryDto> complianceEngine = fetch(
                config.getComplianceEngineApiKey(),
                "X-API-Key",
                hubUrls.complianceEngineUrl() + "/api/v1/analytics/dashboard-summary",
                ComplianceEngineSummaryDto.class,
                "compliance-engine");

        HubStatus<FleetHubSummaryDto> fleetHub = fetch(
                config.getFleetHubApiKey(),
                "X-Internal-Integration-Key",
                hubUrls.fleetHubUrl() + "/api/internal/dashboard-summary",
                FleetHubSummaryDto.class,
                "fleet-hub");

        HubStatus<LogistixSummaryDto> logistix = fetch(
                config.getLogistixApiKey(),
                "X-Internal-Integration-Key",
                hubUrls.logistixUrl() + "/v1/internal/dashboard-summary",
                LogistixSummaryDto.class,
                "logistix");

        HubStatus<FinanceSummaryDto> finance = fetch(
                config.getFinanceApiKey(),
                "X-Internal-Integration-Key",
                hubUrls.financeUrl() + "/v1/internal/dashboard-summary",
                FinanceSummaryDto.class,
                "finance");

        return new CrossHubDashboardDto(complianceEngine, fleetHub, logistix, finance);
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
            log.warn("[Dashboard] Appel {} indisponible ({}) : {}", hubName, url, e.getMessage());
            return HubStatus.unreachable();
        }
    }
}
