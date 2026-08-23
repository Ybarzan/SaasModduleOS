package com.incokalk.service;

import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.dto.shipment.SimulationResult;
import com.incokalk.model.Incoterm;
import com.incokalk.model.Simulation;
import com.incokalk.repository.SimulationRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;

import org.springframework.data.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SimulationService — Tests unitaires")
class SimulationServiceTest {

    @Mock FreightRateService freightSvc;
    @Mock CustomsDutyService dutySvc;
    @Mock CurrencyService currencySvc;
    @Mock SimulationRepository simRepo;
    @Mock UserRepository userRepo;
    @Mock ComplianceService complianceService;
    @Mock VatService vatService;

    @InjectMocks SimulationService service;

    private SimulationRequest req;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(currencySvc.toEur(anyDouble(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(freightSvc.estimate(any(),any(),any(),any(),any(),anyDouble())).thenReturn(new com.incokalk.service.FreightRateService.FreightEstimate(2800.0, 35));
        when(dutySvc.calculate(any(),any(),any(),anyDouble(),anyDouble(),anyDouble(),anyDouble(),any())).thenReturn(1500.0);
        when(vatService.getStandardRate(anyString())).thenReturn(20.0);
        when(vatService.calculate(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyBoolean()))
            .thenReturn(new VatService.VatResult(10000.0, 20.0, "STANDARD", "IMPORT_TAI_REVERSE_CHARGE", true, false,
                "Import B2B — TAI via reverse charge"));
        when(simRepo.save(any())).thenReturn(null);

        req = new SimulationRequest();
        req.setOriginCountry("CN");
        req.setDestinationCountry("FR");
        req.setGoodsValue(50000.0);
        req.setCurrency("EUR");
        req.setTransportMode(SimulationRequest.TransportModeInput.SEA);
        req.setInsuranceLevel(SimulationRequest.InsuranceLevel.STANDARD);
        req.setCompareWithOthers(false);
    }

    @Test @DisplayName("DDP → coût additionnel acheteur minimal")
    void ddp_lowestAdditionalBuyerCosts() {
        req.setIncoterm(Incoterm.DDP);
        SimulationResult r = service.simulate(req, null);
        assertThat(r.getBuyerCosts().getFreight()).isZero();
        assertThat(r.getBuyerCosts().getImportDuties()).isZero();
        assertThat(r.getBuyerCosts().getImportVat()).isZero();
    }

    @Test @DisplayName("EXW → acheteur paie tout")
    void exw_buyerPaysEverything() {
        req.setIncoterm(Incoterm.EXW);
        SimulationResult r = service.simulate(req, null);
        assertThat(r.getBuyerCosts().getExportCustoms()).isPositive();
        assertThat(r.getBuyerCosts().getFreight()).isPositive();
        assertThat(r.getBuyerCosts().getImportDuties()).isPositive();
        assertThat(r.getBuyerRiskScore()).isEqualTo(5);
    }

    @Test @DisplayName("FOB → vendeur gère export, acheteur paie fret")
    void fob_splitResponsibility() {
        req.setIncoterm(Incoterm.FOB);
        SimulationResult r = service.simulate(req, null);
        assertThat(r.getBuyerCosts().getExportCustoms()).isZero();
        assertThat(r.getBuyerCosts().getFreight()).isEqualTo(2800.0);
        assertThat(r.getResponsibilities().isSellerExportClearance()).isTrue();
        assertThat(r.getResponsibilities().isSellerMainFreight()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Incoterm.class)
    @DisplayName("Chaque Incoterm → coût total positif")
    void allIncoterms_positiveCost(Incoterm it) {
        req.setIncoterm(it);
        assertThat(service.simulate(req, null).getTotalBuyerCost()).isPositive();
    }

    @Test @DisplayName("Sans code HS → warning généré")
    void noHsCode_warningPresent() {
        req.setIncoterm(Incoterm.FOB);
        req.setHsCode(null);
        SimulationResult r = service.simulate(req, null);
        assertThat(r.getWarnings()).anyMatch(w -> w.contains("code HS"));
    }

    @Test @DisplayName("Total = somme des postes acheteur")
    void totalCost_equalsSum() {
        req.setIncoterm(Incoterm.FOB);
        SimulationResult r = service.simulate(req, null);
        var c = r.getBuyerCosts();
        double expected = c.getGoodsValue() + c.getExportCustoms() + c.getOriginHandling() + c.getOriginDocumentation()
            + c.getFreight() + c.getInsurance() + c.getDestinationHandling() + c.getDestinationDocumentation()
            + c.getImportDuties() + c.getImportVat() + c.getLastMileDelivery();
        assertThat(r.getTotalBuyerCost()).isCloseTo(expected, within(0.02));
    }

    @Test @DisplayName("getUserSimulations paginé retourne Page")
    void getUserSimulations_paged_returnsPage() {
        UUID userId = UUID.randomUUID();
        Page<Simulation> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(simRepo.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class))).thenReturn(mockPage);
        Page<Simulation> result = service.getUserSimulations(userId, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test @DisplayName("getUserSimulations non paginé retourne liste")
    void getUserSimulations_unpaged_returnsList() {
        UUID userId = UUID.randomUUID();
        Simulation sim = Simulation.builder().id(UUID.randomUUID()).incotermCode("FOB").build();
        Page<Simulation> mockPage = new PageImpl<>(List.of(sim), PageRequest.of(0, 50), 1);
        when(simRepo.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class))).thenReturn(mockPage);
        List<Simulation> result = service.getUserSimulations(userId);
        assertThat(result).hasSize(1);
    }
}
