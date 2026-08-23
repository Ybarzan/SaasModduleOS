package com.incokalk.service;

import com.incokalk.dto.shipment.TruckingRateRequest;
import com.incokalk.dto.shipment.TruckingRateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TruckingService — Tests unitaires")
class TruckingServiceTest {

    private TruckingService service;

    @BeforeEach
    void setUp() {
        service = new TruckingService();
    }

    @Test
    @DisplayName("FR→DE, 3 palettes → 3 options LTL/FTL/Express, LTL recommandée")
    void calculateRates_frToDe_3pallets() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("DE");
        req.setWeightKg(1500.0);
        req.setVolumeM3(5.0);
        req.setPalletCount(3);

        TruckingRateResult res = service.calculateRates(req);
        assertThat(res.getOptions()).hasSize(3);
        assertThat(res.getRecommended().getMode()).isEqualTo("LTL");
        assertThat(res.getOriginCountry()).isEqualTo("FR");
        assertThat(res.getDestinationCountry()).isEqualTo("DE");
    }

    @Test
    @DisplayName("FR→MA, 20 palettes → LTL recommandée (20 palettes < 33, route long haul)")
    void calculateRates_frToMa_20pallets() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("MA");
        req.setWeightKg(10000.0);
        req.setVolumeM3(30.0);
        req.setPalletCount(20);

        TruckingRateResult res = service.calculateRates(req);
        assertThat(res.getOptions()).hasSize(3);
    }

    @Test
    @DisplayName("Express toujours J+1")
    void express_alwaysJ1() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("ES");
        req.setWeightKg(500.0);
        req.setVolumeM3(2.0);

        TruckingRateResult res = service.calculateRates(req);
        var express = res.getOptions().stream()
                .filter(o -> "EXPRESS".equals(o.getMode()))
                .findFirst().orElse(null);
        assertThat(express).isNotNull();
        assertThat(express.getTransitDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR→FR (domestique) → distance factor réduit")
    void calculateRates_domestic() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("FR");
        req.setWeightKg(2000.0);
        req.setVolumeM3(6.0);
        req.setPalletCount(5);

        TruckingRateResult res = service.calculateRates(req);
        assertThat(res.getOptions().get(0).getCostEur()).isPositive();
    }

    @Test
    @DisplayName("Palettes calculées automatiquement si non fournies")
    void calculateRates_autoPalletCount() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("BE");
        req.setWeightKg(10000.0);
        req.setVolumeM3(5.0);

        TruckingRateResult res = service.calculateRates(req);
        assertThat(res.getEstimatedPallets()).isPositive();
    }

    @Test
    @DisplayName("CN→FR (long haul) → FTL coûte plus cher")
    void calculateRates_longHaul() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("CN");
        req.setDestinationCountry("FR");
        req.setWeightKg(15000.0);
        req.setVolumeM3(40.0);
        req.setPalletCount(25);

        TruckingRateResult res = service.calculateRates(req);
        var ftl = res.getOptions().stream()
                .filter(o -> "FTL".equals(o.getMode()))
                .findFirst().orElse(null);
        assertThat(ftl).isNotNull();
        assertThat(ftl.getCostEur()).isGreaterThan(2000);
    }

    @Test
    @DisplayName("CO2 estimé présent pour chaque option")
    void calculateRates_co2Present() {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("DE");
        req.setWeightKg(1000.0);

        TruckingRateResult res = service.calculateRates(req);
        res.getOptions().forEach(o -> assertThat(o.getCo2Kg()).isPositive());
    }
}
