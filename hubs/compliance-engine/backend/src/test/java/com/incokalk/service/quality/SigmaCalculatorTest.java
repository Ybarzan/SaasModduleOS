package com.incokalk.service.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SigmaCalculator — Tests unitaires")
class SigmaCalculatorTest {

    @Test
    @DisplayName("compute → zéro opportunité → résultat vide")
    void compute_zeroOpportunities_returnsEmpty() {
        SigmaCalculator.Result result = SigmaCalculator.compute(0, 0);

        assertThat(result.opportunities()).isZero();
        assertThat(result.defects()).isZero();
        assertThat(result.yieldPct()).isNaN();
        assertThat(result.dpmo()).isNaN();
        assertThat(result.sigma()).isNull();
    }

    @Test
    @DisplayName("compute → opportunités négatives → résultat vide")
    void compute_negativeOpportunities_returnsEmpty() {
        SigmaCalculator.Result result = SigmaCalculator.compute(-5, 2);

        assertThat(result.sigma()).isNull();
    }

    @Test
    @DisplayName("compute → défauts supérieurs aux opportunités → écrêté, rendement 0, sigma 0")
    void compute_defectsExceedOpportunities_clampedToZeroYield() {
        SigmaCalculator.Result result = SigmaCalculator.compute(100, 500);

        assertThat(result.defects()).isEqualTo(100);
        assertThat(result.yieldPct()).isEqualTo(0.0);
        assertThat(result.sigma()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("compute → aucun défaut → rendement 100%, sigma plafonné à 6")
    void compute_zeroDefects_perfectYield_cappedSigma() {
        SigmaCalculator.Result result = SigmaCalculator.compute(1000, 0);

        assertThat(result.yieldPct()).isEqualTo(100.0);
        assertThat(result.dpmo()).isEqualTo(0.0);
        assertThat(result.sigma()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("compute → rendement médian (p entre pLow et pHigh) → sigma positif plausible")
    void compute_midRangeYield_computesPlausibleSigma() {
        SigmaCalculator.Result result = SigmaCalculator.compute(1000, 50);

        assertThat(result.yieldPct()).isEqualTo(95.0);
        assertThat(result.sigma()).isNotNull();
        assertThat(result.sigma()).isBetween(2.0, 6.0);
    }

    @Test
    @DisplayName("compute → rendement très faible (p < pLow) → branche extrême basse de l'approximation")
    void compute_veryLowYield_hitsLowTailApproximation() {
        // yieldRatio = 0.01, bien en dessous du seuil pLow = 0.02425
        SigmaCalculator.Result result = SigmaCalculator.compute(1_000_000, 990_000);

        assertThat(result.yieldPct()).isCloseTo(1.0, within(0.001));
        // A 1% yield is far worse than "0 sigma" on the shifted long-term scale — the
        // formula has no lower clamp (only the upper MAX_DISPLAY_SIGMA one), so this is
        // legitimately negative.
        assertThat(result.sigma()).isNotNull();
        assertThat(result.sigma()).isLessThan(0.0);
    }

    @Test
    @DisplayName("compute → rendement quasi parfait (p > pHigh) → branche extrême haute de l'approximation")
    void compute_nearPerfectYield_hitsHighTailApproximation() {
        // yieldRatio = 0.999, bien au-dessus du seuil pHigh = 0.97575
        SigmaCalculator.Result result = SigmaCalculator.compute(1_000_000, 1_000);

        assertThat(result.yieldPct()).isCloseTo(99.9, within(0.01));
        assertThat(result.sigma()).isNotNull();
        assertThat(result.sigma()).isBetween(3.0, 6.0);
    }

    @Test
    @DisplayName("Result.empty → tous les champs à leur valeur neutre")
    void resultEmpty_hasNeutralFields() {
        SigmaCalculator.Result empty = SigmaCalculator.Result.empty();

        assertThat(empty.opportunities()).isZero();
        assertThat(empty.defects()).isZero();
        assertThat(empty.sigma()).isNull();
    }
}
