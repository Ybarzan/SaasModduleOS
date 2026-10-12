package com.praxio.service;

import com.praxio.config.HubUrlsProperties;
import com.praxio.dto.ComplianceEngineSummaryDto;
import com.praxio.dto.CrossHubDashboardDto;
import com.praxio.dto.FleetHubSummaryDto;
import com.praxio.dto.HubStatus;
import com.praxio.dto.LogistixSummaryDto;
import com.praxio.model.DashboardConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CrossHubDashboardService — Tests unitaires")
class CrossHubDashboardServiceTest {

    RestTemplate restTemplate;
    DashboardConfigService dashboardConfigService;
    HubUrlsProperties hubUrls;
    CrossHubDashboardService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        dashboardConfigService = mock(DashboardConfigService.class);
        hubUrls = new HubUrlsProperties("http://ce.test", "http://fh.test", "http://lx.test");
        service = new CrossHubDashboardService(restTemplate, dashboardConfigService, hubUrls);
    }

    @Test
    @DisplayName("getDashboard → NOT_CONFIGURED pour chaque hub sans clé, aucun appel réseau")
    void getDashboard_noKeysConfigured_allNotConfigured_noNetworkCalls() {
        when(dashboardConfigService.getConfigOrEmpty())
                .thenReturn(DashboardConfig.builder().id(1L).build());

        CrossHubDashboardDto dto = service.getDashboard();

        assertThat(dto.complianceEngine().status()).isEqualTo(HubStatus.Status.NOT_CONFIGURED);
        assertThat(dto.fleetHub().status()).isEqualTo(HubStatus.Status.NOT_CONFIGURED);
        assertThat(dto.logistix().status()).isEqualTo(HubStatus.Status.NOT_CONFIGURED);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getDashboard → OK avec les données renvoyées quand la clé est configurée et l'appel réussit")
    void getDashboard_keyConfigured_callSucceeds_ok() {
        when(dashboardConfigService.getConfigOrEmpty())
                .thenReturn(DashboardConfig.builder().id(1L).complianceEngineApiKey("ic_live_abc").build());
        ComplianceEngineSummaryDto summary = new ComplianceEngineSummaryDto(3, 2, 1, BigDecimal.TEN);
        when(restTemplate.exchange(eq("http://ce.test/api/v1/analytics/dashboard-summary"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ComplianceEngineSummaryDto.class)))
                .thenReturn(ResponseEntity.ok(summary));

        CrossHubDashboardDto dto = service.getDashboard();

        assertThat(dto.complianceEngine().status()).isEqualTo(HubStatus.Status.OK);
        assertThat(dto.complianceEngine().data()).isEqualTo(summary);
        assertThat(dto.fleetHub().status()).isEqualTo(HubStatus.Status.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("getDashboard → UNREACHABLE quand l'appel échoue, sans faire échouer les autres hubs")
    void getDashboard_callFails_unreachable_doesNotAffectOtherHubs() {
        when(dashboardConfigService.getConfigOrEmpty())
                .thenReturn(DashboardConfig.builder().id(1L)
                        .fleetHubApiKey("fh-key")
                        .logistixApiKey("lx-key")
                        .build());
        when(restTemplate.exchange(eq("http://fh.test/api/internal/dashboard-summary"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(FleetHubSummaryDto.class)))
                .thenThrow(new RestClientException("boom"));
        LogistixSummaryDto logistixSummary = new LogistixSummaryDto(2, 1, 0);
        when(restTemplate.exchange(eq("http://lx.test/v1/internal/dashboard-summary"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(LogistixSummaryDto.class)))
                .thenReturn(ResponseEntity.ok(logistixSummary));

        CrossHubDashboardDto dto = service.getDashboard();

        assertThat(dto.fleetHub().status()).isEqualTo(HubStatus.Status.UNREACHABLE);
        assertThat(dto.fleetHub().data()).isNull();
        assertThat(dto.logistix().status()).isEqualTo(HubStatus.Status.OK);
        assertThat(dto.logistix().data()).isEqualTo(logistixSummary);
    }
}
