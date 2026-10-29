package com.finance.service;

import com.finance.model.CarbonOffset;
import com.finance.repository.CarbonOffsetRepository;
import com.finance.security.TenantContext;
import com.finance.service.integration.fleethub.FleetHubIntegrationClient;
import com.finance.service.integration.fleethub.ShipmentLaneSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CsrdReportingService — Tests unitaires")
class CsrdReportingServiceTest {

    CsrdReportingService service;
    CarbonOffsetRepository carbonOffsetRepo;
    FleetHubIntegrationClient fleetHubIntegrationClient;

    @BeforeEach
    void setUp() {
        carbonOffsetRepo = mock(CarbonOffsetRepository.class);
        fleetHubIntegrationClient = mock(FleetHubIntegrationClient.class);
        service = new CsrdReportingService(carbonOffsetRepo, fleetHubIntegrationClient);
        TenantContext.set(UUID.randomUUID());
    }

    @Test
    @DisplayName("getCsrdReport → structure complète du rapport")
    void getCsrdReport_fullStructure() {
        UUID companyId = TenantContext.get();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId))
                .thenReturn(new BigDecimal("50000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of());
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId))
                .thenReturn(new BigDecimal("100"));
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId))
                .thenReturn(new BigDecimal("50"));

        Map<String, Object> report = service.getCsrdReport();

        assertThat(report)
                .containsKey("reportPeriod")
                .containsKey("companyId")
                .containsKey("totalEmissionsCO2")
                .containsKey("emissionsByScope")
                .containsKey("emissionsByLane")
                .containsKey("offsetCreditsPurchased")
                .containsKey("offsetCreditsRetired")
                .containsKey("netEmissions")
                .containsKey("esrsE1Compliant")
                .containsKey("recommendations")
                .containsKey("reportGeneratedAt");

        assertThat(report.get("reportPeriod")).isEqualTo("2026-Q3");
        assertThat(report.get("companyId")).isEqualTo(companyId);
        assertThat(report.get("totalEmissionsCO2")).isEqualTo(new BigDecimal("50.00"));

        @SuppressWarnings("unchecked")
        Map<String, Object> byScope = (Map<String, Object>) report.get("emissionsByScope");
        assertThat(byScope)
                .containsKey("scope1")
                .containsKey("scope2")
                .containsKey("scope3");

        assertThat(report.get("offsetCreditsPurchased")).isEqualTo(new BigDecimal("100"));
        assertThat(report.get("offsetCreditsRetired")).isEqualTo(new BigDecimal("50"));
        assertThat(report.get("netEmissions")).isEqualTo(new BigDecimal("0.00"));
        assertThat(report.get("esrsE1Compliant")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) report.get("recommendations");
        assertThat(recommendations).isNotEmpty();
    }

    @Test
    @DisplayName("getCsrdReport → société sans clé fleet-hub : 'Unknown', aucun appel réseau")
    void getCsrdReport_fleetHubNotConfigured_groupedUnknown_noNetworkCall() {
        UUID companyId = TenantContext.get();

        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(new BigDecimal("1000"))
                .shipmentId(UUID.randomUUID())
                .build();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId))
                .thenReturn(new BigDecimal("1000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(offset));
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);
        when(fleetHubIntegrationClient.isConfigured(companyId)).thenReturn(false);

        Map<String, Object> report = service.getCsrdReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) report.get("emissionsByLane");
        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).get("lane")).isEqualTo("Unknown");
        assertThat(lanes.get(0).get("co2Tonnes")).isEqualTo(new BigDecimal("1.00"));
        verify(fleetHubIntegrationClient, never()).getShipmentSummary(any(), any());
    }

    @Test
    @DisplayName("getCsrdReport → clé configurée, expédition résolue : lane = ville shipper -> ville consignee")
    void getCsrdReport_fleetHubConfigured_resolvesLaneFromShipment() {
        UUID companyId = TenantContext.get();
        UUID shipmentId = UUID.randomUUID();

        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(new BigDecimal("1000"))
                .shipmentId(shipmentId)
                .build();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(new BigDecimal("1000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(offset));
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(fleetHubIntegrationClient.isConfigured(companyId)).thenReturn(true);
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(
                ShipmentLaneSummary.builder().shipperCity("Marseille").consigneeCity("Casablanca").build());

        Map<String, Object> report = service.getCsrdReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) report.get("emissionsByLane");
        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).get("lane")).isEqualTo("Marseille → Casablanca");
    }

    @Test
    @DisplayName("getCsrdReport → clé configurée mais expédition indisponible : seule cette ligne tombe sous 'Unknown'")
    void getCsrdReport_fleetHubConfigured_shipmentUnavailable_thatLaneFallsBackToUnknown() {
        UUID companyId = TenantContext.get();
        UUID shipmentId = UUID.randomUUID();

        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(new BigDecimal("1000"))
                .shipmentId(shipmentId)
                .build();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(new BigDecimal("1000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(offset));
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(fleetHubIntegrationClient.isConfigured(companyId)).thenReturn(true);
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("introuvable"));

        Map<String, Object> report = service.getCsrdReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) report.get("emissionsByLane");
        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).get("lane")).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("getCsrdReport → deux offsets sur la même expédition : un seul appel fleet-hub (mémoïsation)")
    void getCsrdReport_sameShipmentTwice_callsFleetHubOnce() {
        UUID companyId = TenantContext.get();
        UUID shipmentId = UUID.randomUUID();

        CarbonOffset offset1 = CarbonOffset.builder().co2EmissionsKg(new BigDecimal("500")).shipmentId(shipmentId).build();
        CarbonOffset offset2 = CarbonOffset.builder().co2EmissionsKg(new BigDecimal("500")).shipmentId(shipmentId).build();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(new BigDecimal("1000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(offset1, offset2));
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
        when(fleetHubIntegrationClient.isConfigured(companyId)).thenReturn(true);
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(
                ShipmentLaneSummary.builder().shipperCity("Marseille").consigneeCity("Casablanca").build());

        Map<String, Object> report = service.getCsrdReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) report.get("emissionsByLane");
        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).get("co2Tonnes")).isEqualTo(new BigDecimal("1.00"));
        verify(fleetHubIntegrationClient, times(1)).getShipmentSummary(shipmentId, companyId);
    }

    @Test
    @DisplayName("getCsrdReport → aucune émission")
    void getCsrdReport_zeroEmissions() {
        UUID companyId = TenantContext.get();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of());
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);

        Map<String, Object> report = service.getCsrdReport();

        assertThat(((BigDecimal) report.get("totalEmissionsCO2")).compareTo(BigDecimal.ZERO)).isZero();
        assertThat(report.get("esrsE1Compliant")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) report.get("recommendations");
        assertThat(recommendations).anyMatch(r -> r.contains("carbon credits"));
    }

    @Test
    @DisplayName("getCsrdReport → émissions nulles depuis le repo")
    void getCsrdReport_nullEmissions() {
        UUID companyId = TenantContext.get();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(null);
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId)).thenReturn(null);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId)).thenReturn(null);

        Map<String, Object> report = service.getCsrdReport();

        assertThat(((BigDecimal) report.get("totalEmissionsCO2")).compareTo(BigDecimal.ZERO)).isZero();
        assertThat(((BigDecimal) report.get("offsetCreditsPurchased")).compareTo(BigDecimal.ZERO)).isZero();
        assertThat(((BigDecimal) report.get("offsetCreditsRetired")).compareTo(BigDecimal.ZERO)).isZero();
    }
}
