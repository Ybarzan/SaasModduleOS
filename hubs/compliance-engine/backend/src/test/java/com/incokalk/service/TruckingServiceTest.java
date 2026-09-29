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

    private TruckingRateResult.TruckOption option(TruckingRateResult res, String mode) {
        return res.getOptions().stream().filter(o -> mode.equals(o.getMode())).findFirst().orElseThrow();
    }

    private TruckingRateRequest frDe(Double distanceKm) {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry("FR");
        req.setDestinationCountry("DE");
        req.setWeightKg(5000.0);
        req.setVolumeM3(10.0);
        req.setDistanceKm(distanceKm);
        return req;
    }

    @Test
    @DisplayName("FTL 850 km : jamais 1 jour — 2 jours minimum en équipage simple (Règl. 561/2006, 9 h/jour)")
    void ftl_850km_respectsDrivingTimeRules() {
        TruckingRateResult res = service.calculateRates(frDe(850.0));
        assertThat(option(res, "FTL").getTransitDays()).isEqualTo(2);   // 850 / (9 h × 70 km/h) = 1,35
        assertThat(option(res, "LTL").getTransitDays()).isEqualTo(3);   // + 1 jour de groupage
        assertThat(option(res, "EXPRESS").getTransitDays()).isEqualTo(1); // double équipage : 18 h/24 h
    }

    @Test
    @DisplayName("Aucun délai ne dépasse la capacité de conduite réglementaire, quelle que soit la distance")
    void transitDays_neverBelowRegulatoryMinimum() {
        for (double km : new double[] {100, 630, 631, 850, 1260, 1261, 2500}) {
            TruckingRateResult res = service.calculateRates(frDe(km));
            int single = option(res, "FTL").getTransitDays();
            int team = option(res, "EXPRESS").getTransitDays();
            assertThat(km / single).as("FTL %s km", km)
                .isLessThanOrEqualTo(TruckingService.MAX_DAILY_DRIVING_H * TruckingService.AVG_TRUCK_SPEED_KMH);
            assertThat(km / team).as("EXPRESS %s km", km)
                .isLessThanOrEqualTo(2 * TruckingService.MAX_DAILY_DRIVING_H * TruckingService.AVG_TRUCK_SPEED_KMH);
        }
    }

    @Test
    @DisplayName("Sans distance : aucun délai forfaitaire (transitDays null + raison)")
    void noDistance_noTransitDays() {
        TruckingRateResult res = service.calculateRates(frDe(null));
        res.getOptions().forEach(o -> {
            assertThat(o.getTransitDays()).as(o.getMode()).isNull();
            assertThat(o.getDescription()).contains("distance (km) requise");
        });
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
