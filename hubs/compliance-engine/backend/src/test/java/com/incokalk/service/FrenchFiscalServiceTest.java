package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FrenchFiscalService — Tests unitaires")
class FrenchFiscalServiceTest {

    private FrenchFiscalService service;

    @BeforeEach
    void setUp() {
        service = new FrenchFiscalService();
    }

    @Test
    @DisplayName("Bières → TAI applicable")
    void calculateTAI_beer() {
        BigDecimal tai = service.calculateTAI("22030000", BigDecimal.valueOf(10000));
        assertThat(tai).isEqualByComparingTo(new BigDecimal("3176.00"));
    }

    @Test
    @DisplayName("Machines → pas de TAI")
    void calculateTAI_noTai() {
        BigDecimal tai = service.calculateTAI("84713000", BigDecimal.valueOf(10000));
        assertThat(tai).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Cigarettes → accises")
    void calculateAccises_cigarettes() {
        BigDecimal acc = service.calculateAccises("24022000", BigDecimal.valueOf(100));
        assertThat(acc).isEqualByComparingTo(new BigDecimal("5988.00"));
    }

    @Test
    @DisplayName("Acier → droit de sauvegarde")
    void calculateSafeguard_steel() {
        BigDecimal safeguard = service.calculateSafeguardDuty("7208", BigDecimal.valueOf(10000));
        assertThat(safeguard).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    @DisplayName("Acier → pas de sauvegarde (hors scope)")
    void calculateSafeguard_nonSteel() {
        BigDecimal safeguard = service.calculateSafeguardDuty("8471", BigDecimal.valueOf(10000));
        assertThat(safeguard).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Breakdown complet bière")
    void calculateFrenchDuties_beer() {
        var breakdown = service.calculateFrenchDuties(
                "22030000", BigDecimal.valueOf(10000), BigDecimal.valueOf(50),
                "US", "FR", BigDecimal.valueOf(5));

        assertThat(breakdown.customsDutyAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(breakdown.taiAmount()).isEqualByComparingTo(new BigDecimal("3176.00"));
        assertThat(breakdown.acciseAmount()).isEqualByComparingTo(new BigDecimal("1588.00"));
        assertThat(breakdown.safeguardDutyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(breakdown.totalDuties()).isPositive();
        assertThat(breakdown.notes()).contains("TAI", "Accises");
    }

    @Test
    @DisplayName("Breakdown acier avec sauvegarde")
    void calculateFrenchDuties_steel() {
        var breakdown = service.calculateFrenchDuties(
                "72085100", BigDecimal.valueOf(50000), null,
                "CN", "FR", BigDecimal.valueOf(3));

        assertThat(breakdown.safeguardDutyAmount()).isEqualByComparingTo(new BigDecimal("12500.00"));
        assertThat(breakdown.notes()).contains("sauvegarde");
    }

    @Test
    @DisplayName("hasTAI retourne true pour alcool")
    void hasTAI_alcohol() {
        assertThat(service.hasTAI("22030000")).isTrue();
        assertThat(service.hasTAI("84713000")).isFalse();
    }

    @Test
    @DisplayName("hasAccises retourne true pour tabac")
    void hasAccises_tobacco() {
        assertThat(service.hasAccises("24021000")).isTrue();
        assertThat(service.hasAccises("84713000")).isFalse();
    }

    @Test
    @DisplayName("hasSafeguardDuty retourne true pour acier")
    void hasSafeguard_steel() {
        assertThat(service.hasSafeguardDuty("7208")).isTrue();
        assertThat(service.hasSafeguardDuty("8471")).isFalse();
    }

    @Test
    @DisplayName("Régimes actifs contiennent 11 entrées")
    void getActiveRegimes() {
        List<String> regimes = service.getActiveRegimes();
        assertThat(regimes).hasSize(11);
        assertThat(regimes.get(0)).contains("1000");
    }

    @Test
    @DisplayName("isRegimePerfectionnementActif")
    void isRegimePerfectionnementActif() {
        assertThat(service.isRegimePerfectionnementActif("6100")).isTrue();
        assertThat(service.isRegimePerfectionnementActif("1000")).isFalse();
        assertThat(service.isRegimePerfectionnementActif(null)).isFalse();
    }
}
