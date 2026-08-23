package com.incokalk.service;

import com.incokalk.dto.financial.CargoInsuranceRequest;
import com.incokalk.dto.financial.CargoInsuranceResult;
import com.incokalk.repository.CargoInsuranceQuoteRepository;
import com.incokalk.service.insurance.CargoInsurerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CargoInsuranceService — Tests unitaires")
class CargoInsuranceServiceTest {

    @Mock
    private CargoInsurerClient insurerClient;
    @Mock
    private CargoInsuranceQuoteRepository quoteRepository;

    private CargoInsuranceService service;

    @BeforeEach
    void setUp() {
        service = new CargoInsuranceService(insurerClient, quoteRepository);
        when(insurerClient.fetchMarketRateFactor(org.mockito.ArgumentMatchers.any()))
            .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Assurance maritime standard")
    void calculate_seaStandard() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setTransportMode("SEA");
        req.setGoodsCategory("STANDARD");
        req.setGoodsValue(10000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getPremiumRate()).isEqualTo(0.003);
        assertThat(res.getPremiumAmount()).isPositive();
        assertThat(res.getCoverageAmount()).isEqualTo(11000.0);
        assertThat(res.getCoverageType()).contains("Institute Cargo Clauses");
    }

    @Test
    @DisplayName("Assurance aérienne haute valeur")
    void calculate_airHauteValeur() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setTransportMode("AIR");
        req.setGoodsCategory("HAUTE_VALEUR");
        req.setGoodsValue(50000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getPremiumRate()).isEqualTo(0.009);
        assertThat(res.getPremiumAmount()).isPositive();
        assertThat(res.getCoverageType()).contains("Tous risques");
    }

    @Test
    @DisplayName("Assurance routière dangereux")
    void calculate_roadDangerous() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setTransportMode("ROAD");
        req.setGoodsCategory("DANGEREUX");
        req.setGoodsValue(20000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getPremiumRate()).isEqualTo(0.008);
        assertThat(res.getPremiumAmount()).isPositive();
        assertThat(res.getCoverageType()).contains("responsabilité civile");
    }

    @Test
    @DisplayName("Assurance périssable")
    void calculate_perishable() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setTransportMode("ROAD");
        req.setGoodsCategory("PERISSABLE");
        req.setGoodsValue(8000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getPremiumRate()).isEqualTo(0.0064);
        assertThat(res.getCoverageType()).contains("Température contrôlée");
    }

    @Test
    @DisplayName("Assurance avec valeurs par défaut")
    void calculate_defaultValues() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setGoodsValue(5000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getTransportMode()).isEqualTo("SEA");
        assertThat(res.getPremiumRate()).isPositive();
    }

    @Test
    @DisplayName("Couverture = valeur x 1.1")
    void calculate_coverageEqualsValueTimes110Percent() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setTransportMode("AIR");
        req.setGoodsCategory("ELECTRONIQUE");
        req.setGoodsValue(25000.0);

        CargoInsuranceResult res = service.calculate(req);
        assertThat(res.getCoverageAmount()).isEqualTo(27500.0);
    }
}
