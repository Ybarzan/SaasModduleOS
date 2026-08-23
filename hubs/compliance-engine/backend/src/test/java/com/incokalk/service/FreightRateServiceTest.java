package com.incokalk.service;

import com.incokalk.dto.shipment.SimulationRequest.TransportModeInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FreightRateService — Tests unitaires")
class FreightRateServiceTest {

    private FreightRateService service;

    @BeforeEach
    void setUp() {
        service = new FreightRateService();
    }

    // ── SEA ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fret maritime LCL — petit volume")
    void estimate_sea_lcl() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", TransportModeInput.SEA, 1000.0, 5.0, 20000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.cost()).isEqualTo(383.5);
        assertThat(result.days()).isEqualTo(35);
    }

    @Test
    @DisplayName("Fret maritime LCL — volume minimal")
    void estimate_sea_lcl_small() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", TransportModeInput.SEA, 100.0, 1.0, 5000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.cost()).isEqualTo(76.7);
        assertThat(result.days()).isEqualTo(35);
    }

    @Test
    @DisplayName("Fret maritime FCL — gros volume ≥ 25 m3")
    void estimate_sea_fcl() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", TransportModeInput.SEA, 5000.0, 30.0, 80000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.days()).isEqualTo(35);
        assertThat(result.cost()).isGreaterThan(2000.0);
    }

    // ── AIR ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fret aérien — charge volumétrale")
    void estimate_air_chargeable_weight() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", TransportModeInput.AIR, 50.0, 0.5, 30000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.cost()).isEqualTo(479.17);
        assertThat(result.days()).isEqualTo(7);
    }

    // ── ROAD ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fret routier — nearshore Maroc")
    void estimate_road_nearshore() {
        FreightRateService.FreightEstimate result =
                service.estimate("MA", "FR", TransportModeInput.ROAD, 500.0, 2.0, 10000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.cost()).isEqualTo(95.0);
        assertThat(result.days()).isEqualTo(7);
    }

    // ── AUTO-DETECT (mode = null) ──────────────────────────────────────

    @Test
    @DisplayName("Auto-détection → SEA pour faible valeur")
    void estimate_autoDetect_lowValue_sea() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", null, 100.0, 2.0, 5000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.days()).isEqualTo(35);
    }

    @Test
    @DisplayName("Auto-détection → AIR pour haute valeur")
    void estimate_autoDetect_highValue_air() {
        FreightRateService.FreightEstimate result =
                service.estimate("CN", "FR", null, 100.0, 2.0, 100000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.days()).isEqualTo(7);
    }

    @Test
    @DisplayName("Auto-détection → ROAD pour nearshore")
    void estimate_autoDetect_nearshore_road() {
        FreightRateService.FreightEstimate result =
                service.estimate("MA", "FR", null, 100.0, 2.0, 5000.0);

        assertThat(result.cost()).isPositive();
        assertThat(result.days()).isPositive();
        assertThat(result.days()).isEqualTo(7);
    }

    // ── INVARIANTS ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Coût toujours positif et jours > 0 pour tout mode")
    void estimate_alwaysPositiveCostAndDays() {
        for (TransportModeInput mode : TransportModeInput.values()) {
            FreightRateService.FreightEstimate r =
                    service.estimate("CN", "FR", mode, 100.0, 2.0, 5000.0);
            assertThat(r.cost()).as("Cost for %s", mode).isPositive();
            assertThat(r.days()).as("Days for %s", mode).isPositive();
        }
    }
}
