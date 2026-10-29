package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.LandedCost;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.LandedCostRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentSummary;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LandedCostService — Tests calculate + whatIf + share")
class LandedCostServiceTest {

    @Mock LandedCostRepository landedCostRepo;
    @Mock CompanyRepository companyRepo;
    @Mock CustomsDutyService customsDutyService;
    @Mock VatService vatService;
    @Mock FleetHubIntegrationClient fleetHubIntegrationClient;
    @InjectMocks LandedCostService service;

    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private LandedCost buildInput() {
        return LandedCost.builder()
                .originCountry("CN")
                .destinationCountry("FR")
                .incoterm("CIF")
                .hsCode("620443")
                .transportMode("SEA")
                .productValue(new BigDecimal("10000.00"))
                .currency("EUR")
                .freightCost(new BigDecimal("1200.00"))
                .insuranceCost(new BigDecimal("150.00"))
                .portCharges(new BigDecimal("300.00"))
                .customsFees(new BigDecimal("50.00"))
                .handlingFees(new BigDecimal("80.00"))
                .lastMileCost(new BigDecimal("200.00"))
                .unitCount(10)
                .sellingPrice(new BigDecimal("2500.00"))
                .build();
    }

    @Test
    @DisplayName("Calculate — duty + VAT + totals computed correctly")
    void calculate_computesAll() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(customsDutyService.calculateDetailed("620443", "CN", "FR", 10000.0, 1200.0, 150.0))
                .thenReturn(new CustomsDutyService.DutyResult(1200.0, 12.0, "MFN", false, null, null, 12.0, 0.0, null));
        when(vatService.calculate("CN", "FR", 10000.0, 1200.0, 150.0, "CIF", true))
                .thenReturn(new VatService.VatResult(2070.0, 20.0, "STANDARD", "TVA import", false, false, null));
        when(landedCostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LandedCost result = service.calculate(buildInput());

        assertThat(result.getDutyRate()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(result.getDutyAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(result.getVatRate()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.getVatAmount()).isEqualByComparingTo(new BigDecimal("2070.00"));

        BigDecimal expectedTotal = new BigDecimal("11350.00")
                .add(new BigDecimal("1200.00"))
                .add(new BigDecimal("2070.00"))
                .add(new BigDecimal("300.00"))
                .add(new BigDecimal("50.00"))
                .add(new BigDecimal("80.00"))
                .add(new BigDecimal("200.00"));
        assertThat(result.getTotalLandedCost()).isEqualByComparingTo(expectedTotal);
        assertThat(result.getTotalLandedCostPerUnit()).isEqualByComparingTo(expectedTotal.divide(BigDecimal.valueOf(10), 2, java.math.RoundingMode.HALF_UP));

        verify(landedCostRepo).save(any());
    }

    @Test
    @DisplayName("Calculate with margin — margin and marginPercent set")
    void calculate_withMargin() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(customsDutyService.calculateDetailed(any(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new CustomsDutyService.DutyResult(0.0, 0.0, "NONE", false, null, null, 0.0, 0.0, "EU"));
        when(vatService.calculate(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new VatService.VatResult(0.0, 0.0, "NONE", "intra", false, true, null));
        when(landedCostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LandedCost input = buildInput();
        input.setUnitCount(1);
        input.setProductValue(new BigDecimal("10000.00"));
        input.setSellingPrice(new BigDecimal("15000.00"));

        LandedCost result = service.calculate(input);

        assertThat(result.getMargin()).isNotNull();
        assertThat(result.getMarginPercent()).isNotNull();
        assertThat(result.getMargin().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Calculate EU→EU — no duty, no VAT")
    void calculate_euToEu() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(customsDutyService.calculateDetailed(any(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new CustomsDutyService.DutyResult(0.0, 0.0, "NONE", false, null, null, 0.0, 0.0, "Intra-EU"));
        when(vatService.calculate(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new VatService.VatResult(0.0, 0.0, "NONE", "intra", false, true, "Intra-EU"));
        when(landedCostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LandedCost input = buildInput();
        input.setOriginCountry("DE");
        input.setDestinationCountry("FR");

        LandedCost result = service.calculate(input);

        assertThat(result.getDutyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getVatAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("WhatIf — returns calculated result without saving")
    void whatIf_doesNotSave() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(customsDutyService.calculateDetailed(any(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new CustomsDutyService.DutyResult(500.0, 5.0, "MFN", false, null, null, 5.0, 0.0, null));
        when(vatService.calculate(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new VatService.VatResult(2100.0, 20.0, "STANDARD", "import", false, false, null));

        LandedCost result = service.whatIf(buildInput());

        assertThat(result.getDutyRate()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(result.getTotalLandedCost()).isNotNull();
        verifyNoInteractions(landedCostRepo);
    }

    @Test
    @DisplayName("CompareScenarios — returns list of results without saving")
    void compareScenarios_returnsList() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(customsDutyService.calculateDetailed(any(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new CustomsDutyService.DutyResult(500.0, 5.0, "MFN", false, null, null, 5.0, 0.0, null));
        when(vatService.calculate(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean()))
                .thenReturn(new VatService.VatResult(2100.0, 20.0, "STANDARD", "import", false, false, null));

        List<LandedCost> scenarios = List.of(buildInput(), buildInput());
        List<Map<String, Object>> results = service.compareScenarios(scenarios);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsKey("totalLandedCost");
        assertThat(results.get(1)).containsKey("dutyRate");
        verifyNoInteractions(landedCostRepo);
    }

    @Test
    @DisplayName("GenerateShareToken — creates unique token")
    void generateShareToken_createsToken() {
        LandedCost existing = buildInput();
        existing.setId(UUID.randomUUID());
        when(landedCostRepo.findByCompanyIdAndId(companyId, existing.getId()))
                .thenReturn(Optional.of(existing));
        when(landedCostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String token = service.generateShareToken(existing.getId());

        assertThat(token).isNotBlank();
        assertThat(token.length()).isGreaterThan(20);
        assertThat(existing.getShareToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("GetByShareToken — returns matching landed cost")
    void getByShareToken_returnsResult() {
        LandedCost lc = buildInput();
        lc.setShareToken("abc123");
        when(landedCostRepo.findByShareToken("abc123")).thenReturn(Optional.of(lc));

        LandedCost result = service.getByShareToken("abc123");

        assertThat(result).isNotNull();
        assertThat(result.getOriginCountry()).isEqualTo("CN");
    }

    @Test
    @DisplayName("GetByShareToken — invalid token throws")
    void getByShareToken_invalidToken_throws() {
        when(landedCostRepo.findByShareToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByShareToken("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GetStats — returns correct counts")
    void getStats_returnsCounts() {
        LandedCost lc = buildInput();
        lc.setId(UUID.randomUUID());
        lc.setTotalLandedCost(new BigDecimal("15000.00"));
        lc.setMargin(new BigDecimal("3000.00"));
        when(landedCostRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(lc));

        Map<String, Object> stats = service.getStats();

        assertThat(stats.get("total")).isEqualTo(1);
        assertThat(stats.get("avgTotalLandedCost")).isNotNull();
        assertThat(stats.get("avgMargin")).isNotNull();
    }

    private ShipmentSummary.Item item(String hsCode) {
        return ShipmentSummary.Item.builder().hsCode(hsCode).build();
    }

    @Test
    @DisplayName("createFromShipment — codes pays ISO-2 valides → préremplis, jamais calculé ni sauvegardé")
    void createFromShipment_validIso2Codes_prefillsWithoutCalculating() {
        UUID shipmentId = UUID.randomUUID();
        ShipmentSummary summary = ShipmentSummary.builder()
                .orderNumber("SHP-42")
                .shipperCountry("cn").consigneeCountry(" FR ")
                .incotermCode("CIF").currency("USD").transportMode("AIR")
                .goodsValue(5000.0)
                .items(List.of(item("620443")))
                .build();
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(summary);

        LandedCost result = service.createFromShipment(shipmentId);

        assertThat(result.getShipmentId()).isEqualTo(shipmentId);
        assertThat(result.getCalculationName()).isEqualTo("Expédition SHP-42");
        assertThat(result.getOriginCountry()).isEqualTo("CN");
        assertThat(result.getDestinationCountry()).isEqualTo("FR");
        assertThat(result.getIncoterm()).isEqualTo("CIF");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getTransportMode()).isEqualTo("AIR");
        assertThat(result.getProductValue()).isEqualByComparingTo(new BigDecimal("5000.0"));
        assertThat(result.getHsCode()).isEqualTo("620443");
        verifyNoInteractions(landedCostRepo, customsDutyService, vatService);
    }

    @Test
    @DisplayName("createFromShipment — pays en texte libre (pas un code ISO-2) → laissé vide, jamais deviné")
    void createFromShipment_freeTextCountry_leftBlank() {
        UUID shipmentId = UUID.randomUUID();
        ShipmentSummary summary = ShipmentSummary.builder()
                .shipperCountry("Chine").consigneeCountry("France")
                .build();
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(summary);

        LandedCost result = service.createFromShipment(shipmentId);

        assertThat(result.getOriginCountry()).isNull();
        assertThat(result.getDestinationCountry()).isNull();
    }

    @Test
    @DisplayName("createFromShipment — plusieurs articles → hsCode ambigu, laissé vide")
    void createFromShipment_multipleItems_hsCodeLeftBlank() {
        UUID shipmentId = UUID.randomUUID();
        ShipmentSummary summary = ShipmentSummary.builder()
                .shipperCountry("CN").consigneeCountry("FR")
                .items(List.of(item("620443"), item("640411")))
                .build();
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId)).thenReturn(summary);

        LandedCost result = service.createFromShipment(shipmentId);

        assertThat(result.getHsCode()).isNull();
    }

    @Test
    @DisplayName("createFromShipment — expédition introuvable côté fleet-hub → erreur explicite")
    void createFromShipment_fleetHubUnavailable_throws() {
        UUID shipmentId = UUID.randomUUID();
        when(fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("introuvable"));

        assertThatThrownBy(() -> service.createFromShipment(shipmentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("introuvable");
    }
}
