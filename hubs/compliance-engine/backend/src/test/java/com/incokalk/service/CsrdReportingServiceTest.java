package com.incokalk.service;

import com.incokalk.model.CarbonOffset;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.tenant.TenantContext;
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

    @BeforeEach
    void setUp() {
        carbonOffsetRepo = mock(CarbonOffsetRepository.class);
        service = new CsrdReportingService(carbonOffsetRepo);
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
    @DisplayName("getCsrdReport → émissions avec données de transport")
    void getCsrdReport_withLaneData() {
        UUID companyId = TenantContext.get();

        ShipmentOrder shipment = new ShipmentOrder();
        shipment.setShipperCity("Paris");
        shipment.setConsigneeCity("Lyon");

        CarbonOffset offset = CarbonOffset.builder()
                .co2EmissionsKg(new BigDecimal("1000"))
                .shipment(shipment)
                .build();

        when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId))
                .thenReturn(new BigDecimal("1000"));
        when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(offset));
        when(carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);
        when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId))
                .thenReturn(BigDecimal.ZERO);

        Map<String, Object> report = service.getCsrdReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lanes = (List<Map<String, Object>>) report.get("emissionsByLane");
        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).get("lane")).isEqualTo("Paris → Lyon");
        assertThat(lanes.get(0).get("co2Tonnes")).isEqualTo(new BigDecimal("1.00"));
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
