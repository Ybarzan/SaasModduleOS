package com.incokalk.controller;

import com.incokalk.dto.financial.CargoInsuranceResult;
import com.incokalk.dto.shipment.PackagingResult;
import com.incokalk.dto.shipment.RouteOptimizationResult;
import com.incokalk.dto.shipment.TruckingRateResult;
import com.incokalk.service.CargoInsuranceService;
import com.incokalk.service.CurrencyExchangeService;
import com.incokalk.service.CustomsDutyService;
import com.incokalk.service.PackagingService;
import com.incokalk.service.RouteOptimizationService;
import com.incokalk.service.TruckingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogisticsControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private PackagingService packagingService;
    @MockBean
    private TruckingService truckingService;
    @MockBean
    private CustomsDutyService customsDutyService;
    @MockBean
    private CargoInsuranceService cargoInsuranceService;
    @MockBean
    private RouteOptimizationService routeOptimizationService;
    @MockBean
    private CurrencyExchangeService currencyExchangeService;

    @Test
    @DisplayName("POST /v1/logistics/packaging → 200")
    void calculatePackaging_success() throws Exception {
        when(packagingService.calculatePackaging(any())).thenReturn(
            PackagingResult.builder()
                .totalBoxes(1)
                .totalVolumeM3(0.5)
                .totalWeightKg(10)
                .utilizationPercent(80)
                .totalPackageVolumeM3(0.4)
                .build());

        mockMvc.perform(post("/v1/logistics/packaging")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"sku":"SKU1","lengthCm":30,"widthCm":20,"heightCm":15,"weightKg":5,"quantity":2}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBoxes").value(1));
    }

    @Test
    @DisplayName("POST /v1/logistics/packaging → 400 si items manquants (validation)")
    void calculatePackaging_validationError() throws Exception {
        mockMvc.perform(post("/v1/logistics/packaging")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/logistics/trucking → 200")
    void calculateTruckingRates_success() throws Exception {
        when(truckingService.calculateRates(any())).thenReturn(
            TruckingRateResult.builder()
                .originCountry("FR")
                .destinationCountry("DE")
                .estimatedPallets(2)
                .totalWeightKg(500)
                .totalVolumeM3(2.5)
                .build());

        mockMvc.perform(post("/v1/logistics/trucking")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"FR","destinationCountry":"DE","weightKg":500,"volumeM3":2.5}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originCountry").value("FR"));
    }

    private static com.incokalk.service.CustomsDutyService.DutyResult duty(double amount, double rate,
                                                                           boolean pref, String agreement) {
        return new com.incokalk.service.CustomsDutyService.DutyResult(amount, rate, "AD", pref,
            pref ? "CODE" : null, agreement, rate, 0.0, pref ? "Droit préférentiel" : "Droit MFN standard appliqué");
    }

    @Test
    @DisplayName("POST /v1/logistics/customs-duty → 200, EUR : taux, montant, devise et base déclarés")
    void calculateCustomsDuty_defaultCurrency_noAgreement() throws Exception {
        when(customsDutyService.calculateDetailed(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(duty(50.0, 5.0, false, null));

        mockMvc.perform(post("/v1/logistics/customs-duty")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hsCode":"85235110","originCountry":"CN","destinationCountry":"FR","goodsValue":1000}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originCountry").value("CN"))
            .andExpect(jsonPath("$.destinationCountry").value("FR"))
            .andExpect(jsonPath("$.rateAvailable").value(true))
            .andExpect(jsonPath("$.dutyRate").value(5.0))
            .andExpect(jsonPath("$.dutyAmount").value(50.0))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.basisType").value("CIF_EU"))
            .andExpect(jsonPath("$.fxRate").value(1.0))
            .andExpect(jsonPath("$.agreement").doesNotExist());
    }

    @Test
    @DisplayName("POST /v1/logistics/customs-duty → 200, devise étrangère → montant converti, taux de change exposé")
    void calculateCustomsDuty_foreignCurrency_positiveDuty_converts() throws Exception {
        when(customsDutyService.calculateDetailed(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(duty(100.0, 10.0, false, null));
        when(currencyExchangeService.convert(eq(100.0), eq("EUR"), eq("USD"))).thenReturn(110.0);
        when(currencyExchangeService.getRate(eq("EUR"), eq("USD"))).thenReturn(1.1);

        mockMvc.perform(post("/v1/logistics/customs-duty")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hsCode":"85235110","originCountry":"CN","destinationCountry":"FR",
                     "goodsValue":1000,"freightCost":50,"insuranceCost":10,"currency":"USD"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyAmount").value(110.0))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.fxRate").value(1.1))
            .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("converti de EUR en USD (taux: 1.1)")));
    }

    @Test
    @DisplayName("POST /v1/logistics/customs-duty → agreement = accord réellement appliqué, pas un accord existant de l'origine")
    void calculateCustomsDuty_agreementOnlyWhenApplied() throws Exception {
        when(customsDutyService.calculateDetailed(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(duty(0.0, 0.0, true, "Accord UE-Vietnam"));

        mockMvc.perform(post("/v1/logistics/customs-duty")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hsCode":"85235110","originCountry":"VN","destinationCountry":"FR","goodsValue":1000}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dutyAmount").value(0.0))
            .andExpect(jsonPath("$.agreement").value("Accord UE-Vietnam"));
        verify(customsDutyService, never()).getEUAgreement(anyString());
    }

    @Test
    @DisplayName("POST /v1/logistics/customs-duty → taux indisponible : rateAvailable=false, ni taux ni montant")
    void calculateCustomsDuty_unavailable_returnsNulls() throws Exception {
        when(customsDutyService.calculateDetailed(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(com.incokalk.service.CustomsDutyService.DutyResult.unavailable("Aucun référentiel pour US."));

        mockMvc.perform(post("/v1/logistics/customs-duty")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hsCode":"847130","originCountry":"FR","destinationCountry":"US","goodsValue":10000,"currency":"USD"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rateAvailable").value(false))
            .andExpect(jsonPath("$.dutyRate").doesNotExist())
            .andExpect(jsonPath("$.dutyAmount").doesNotExist())
            .andExpect(jsonPath("$.basisType").doesNotExist())
            .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("non disponibles")));
        verifyNoInteractions(currencyExchangeService);
    }

    @Test
    @DisplayName("POST /v1/logistics/insurance → 200")
    void calculateInsurance_success() throws Exception {
        when(cargoInsuranceService.calculate(any())).thenReturn(
            CargoInsuranceResult.builder()
                .goodsValue(10000)
                .premiumRate(0.01)
                .premiumAmount(100)
                .coverageAmount(11000)
                .coverageType("ALL_RISKS")
                .transportMode("SEA")
                .build());

        mockMvc.perform(post("/v1/logistics/insurance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"goodsValue":10000,"weightKg":500,"transportMode":"SEA",
                     "originCountry":"CN","destinationCountry":"FR"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.premiumAmount").value(100));
    }

    @Test
    @DisplayName("POST /v1/logistics/route-optimization → 200")
    void optimizeRoute_success() throws Exception {
        when(routeOptimizationService.optimize(any())).thenReturn(
            RouteOptimizationResult.builder()
                .totalDistanceKm(1200)
                .totalStops(2)
                .estimatedHours(14)
                .estimatedFuelLiters(300)
                .estimatedFuelCost(450)
                .estimatedTollCost(80)
                .recommendation("Itinéraire optimisé")
                .build());

        mockMvc.perform(post("/v1/logistics/route-optimization")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"FR","destinationCountry":"DE",
                     "stops":[{"city":"Paris","country":"FR"},{"city":"Berlin","country":"DE"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalStops").value(2));
    }
}
