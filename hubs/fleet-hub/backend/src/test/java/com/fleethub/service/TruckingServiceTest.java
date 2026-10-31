package com.fleethub.service;

import com.fleethub.dto.TruckingRateRequest;
import com.fleethub.dto.TruckingRateResult;
import com.fleethub.dto.TruckingRateResult.TruckOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couvre applyRegulatoryTransit()/drivingDays() : avant, transitDays était un
 * délai forfaitaire par zone géographique, jamais vérifié contre la distance
 * réelle (ex. FTL Europe = 1 jour quel que soit le trajet, y compris 850 km,
 * infaisable en une journée de conduite réglementaire) -- corrigé en portant
 * le correctif équivalent de compliance-engine (avant le retrait du domaine
 * Transport, voir PR #1 "correctifs P0 de l'audit du 2026-09-29").
 */
@DisplayName("TruckingService — Tests unitaires")
class TruckingServiceTest {

    private TruckingService service;

    @BeforeEach
    void setUp() {
        service = new TruckingService();
    }

    private TruckingRateRequest request(String origin, String dest, Double distanceKm) {
        TruckingRateRequest req = new TruckingRateRequest();
        req.setOriginCountry(origin);
        req.setDestinationCountry(dest);
        req.setWeightKg(500.0);
        req.setVolumeM3(2.0);
        req.setDistanceKm(distanceKm);
        return req;
    }

    private Map<String, TruckOption> byMode(TruckingRateResult result) {
        return result.getOptions().stream().collect(Collectors.toMap(TruckOption::getMode, o -> o));
    }

    @Test
    @DisplayName("FTL sur 850 km → au moins 2 jours de conduite (9h/jour à 70 km/h, un seul équipage)")
    void ftl_850km_respectsDrivingTimeRules() {
        TruckingRateResult result = service.calculateRates(request("FR", "DE", 850.0));

        TruckOption ftl = byMode(result).get("FTL");
        assertThat(ftl.getTransitDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("Express (double équipage) sur 850 km → conduite deux fois plus rapide que FTL")
    void express_850km_doubleCrewFasterThanFtl() {
        TruckingRateResult result = service.calculateRates(request("FR", "DE", 850.0));

        Map<String, TruckOption> options = byMode(result);
        assertThat(options.get("EXPRESS").getTransitDays()).isEqualTo(1);
        assertThat(options.get("FTL").getTransitDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("LTL ajoute un jour de groupage/dégroupage par rapport à la conduite pure")
    void ltl_addsCrossdockDay() {
        TruckingRateResult result = service.calculateRates(request("FR", "DE", 850.0));

        Map<String, TruckOption> options = byMode(result);
        assertThat(options.get("LTL").getTransitDays()).isEqualTo(options.get("FTL").getTransitDays() + 1);
    }

    @Test
    @DisplayName("Sans distance renseignée → transitDays non calculé (jamais de délai forfaitaire inventé)")
    void noDistance_transitDaysNullWithExplanation() {
        TruckingRateResult result = service.calculateRates(request("FR", "DE", null));

        for (TruckOption option : result.getOptions()) {
            assertThat(option.getTransitDays()).isNull();
            assertThat(option.getDescription()).contains("distance (km) requise");
        }
    }
}
