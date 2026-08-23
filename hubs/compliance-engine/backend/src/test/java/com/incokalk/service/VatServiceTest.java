package com.incokalk.service;

import com.incokalk.model.VatRate;
import com.incokalk.repository.VatRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("VatService — Tests unitaires")
class VatServiceTest {

    VatService service;
    VatRateRepository vatRateRepo;

    @BeforeEach
    void setUp() {
        vatRateRepo = mock(VatRateRepository.class);
        service = new VatService(vatRateRepo);
    }

    @Test
    @DisplayName("France domestique → TVA nationale 20%")
    void calculate_domesticFR() {
        var r = service.calculate("FR", "FR", 1000, 0, 0, "EXW", true);
        assertThat(r.vatAmount()).isEqualByComparingTo(200.0);
        assertThat(r.regime()).isEqualTo("DOMESTIC");
        assertThat(r.reverseCharge()).isFalse();
    }

    @Test
    @DisplayName("Intracom B2B FR→DE → Reverse charge")
    void calculate_intracomB2B() {
        var r = service.calculate("FR", "DE", 1000, 200, 50, "CIF", true);
        assertThat(r.reverseCharge()).isTrue();
        assertThat(r.regime()).isEqualTo("IC_B2B_REVERSE_CHARGE");
    }

    @Test
    @DisplayName("Intracom B2C FR→DE → OSS")
    void calculate_intracomB2C() {
        var r = service.calculate("FR", "DE", 1000, 200, 50, "CIF", false);
        assertThat(r.regime()).isEqualTo("IC_B2C_OSS");
        assertThat(r.reverseCharge()).isFalse();
    }

    @Test
    @DisplayName("Import B2B US→FR → TAI reverse charge")
    void calculate_importB2B() {
        var r = service.calculate("US", "FR", 5000, 1000, 200, "CIF", true);
        assertThat(r.reverseCharge()).isTrue();
        assertThat(r.regime()).isEqualTo("IMPORT_TAI_REVERSE_CHARGE");
    }

    @Test
    @DisplayName("Import B2C US→FR → TAI standard")
    void calculate_importB2C() {
        var r = service.calculate("US", "FR", 5000, 1000, 200, "CIF", false);
        assertThat(r.regime()).isEqualTo("IMPORT_TAI");
        assertThat(r.reverseCharge()).isFalse();
    }

    @Test
    @DisplayName("Export FR→US → exonéré")
    void calculate_export() {
        var r = service.calculate("FR", "US", 5000, 0, 0, "EXW", true);
        assertThat(r.isExempt()).isTrue();
        assertThat(r.vatAmount()).isEqualByComparingTo(0.0);
    }

    @Test
    @DisplayName("Hors UE → hors UE → exonéré")
    void calculate_exportOutsideEU() {
        var r = service.calculate("CN", "US", 5000, 0, 0, "EXW", true);
        assertThat(r.isExempt()).isTrue();
    }

    @Test
    @DisplayName("Marges B2B → reverse charge")
    void calculateMarginScheme_b2b() {
        var r = service.calculateMarginScheme("FR", 2000, 800, true);
        assertThat(r.isExempt()).isFalse();
        assertThat(r.reverseCharge()).isTrue();
        assertThat(r.regime()).isEqualTo("MARGIN_B2B_REVERSE_CHARGE");
    }

    @Test
    @DisplayName("Marges B2C → TVA sur marge")
    void calculateMarginScheme_b2c() {
        var r = service.calculateMarginScheme("FR", 2000, 800, false);
        assertThat(r.reverseCharge()).isFalse();
        assertThat(r.regime()).isEqualTo("MARGIN_B2C");
        assertThat(r.vatAmount()).isPositive();
    }

    @Test
    @DisplayName("getStandardRate → BDD puis fallback")
    void getStandardRate() {
        when(vatRateRepo.findFirstByCountryCodeAndRateTypeAndIsActiveTrueOrderByValidFromDesc("DE", VatRate.RateType.STANDARD))
                .thenReturn(Optional.empty());
        double rate = service.getStandardRate("DE");
        assertThat(rate).isEqualByComparingTo(19.0);
    }

    @Test
    @DisplayName("getStandardRate → depuis BDD")
    void getStandardRate_fromDb() {
        VatRate dbRate = new VatRate();
        dbRate.setRate(21.0);
        when(vatRateRepo.findFirstByCountryCodeAndRateTypeAndIsActiveTrueOrderByValidFromDesc("ES", VatRate.RateType.STANDARD))
                .thenReturn(Optional.of(dbRate));
        assertThat(service.getStandardRate("ES")).isEqualTo(21.0);
    }

    @Test
    @DisplayName("isEU → true pour FR, false pour US")
    void isEU() {
        assertThat(service.isEU("FR")).isTrue();
        assertThat(service.isEU("US")).isFalse();
    }

    @Test
    @DisplayName("validateVies → format invalide")
    void validateVies_invalidFormat() {
        var r = service.validateVies("123");
        assertThat(r.valid()).isFalse();
        assertThat(r.formatValid()).isFalse();
    }

    @Test
    @DisplayName("validateVies → format valide, pays non UE")
    void validateVies_nonEUCountry() {
        var r = service.validateVies("US123456789");
        assertThat(r.valid()).isFalse();
        assertThat(r.formatValid()).isTrue();
    }

    @Test
    @DisplayName("validateVies → format valide, pays UE")
    void validateVies_valid() {
        var r = service.validateVies("FR12345678901");
        assertThat(r.valid()).isTrue();
        assertThat(r.formatValid()).isTrue();
        assertThat(r.country()).isEqualTo("FR");
    }

    @Test
    @DisplayName("validateVies → null → invalide")
    void validateVies_null() {
        var r = service.validateVies(null);
        assertThat(r.valid()).isFalse();
    }

    @Test
    @DisplayName("validateVies → rejet en ligne VIES → invalide")
    void validateVies_onlineRejected() {
        ViesClient viesClient = mock(ViesClient.class);
        when(viesClient.checkVat("FR12345678901"))
            .thenReturn(new ViesClient.ViesCheck(false, null, null, null));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "viesClient", viesClient);

        var r = service.validateVies("FR12345678901");

        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("rejeté");
    }

    @Test
    @DisplayName("validateVies → validé en ligne VIES → holder renseigné")
    void validateVies_onlineValid() {
        ViesClient viesClient = mock(ViesClient.class);
        when(viesClient.checkVat("FR12345678901"))
            .thenReturn(new ViesClient.ViesCheck(true, "ACME SARL", "12 rue de la Paix, Paris", null));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "viesClient", viesClient);

        var r = service.validateVies("FR12345678901");

        assertThat(r.valid()).isTrue();
        assertThat(r.holderName()).isEqualTo("ACME SARL");
        assertThat(r.holderAddress()).isEqualTo("12 rue de la Paix, Paris");
        assertThat(r.message()).contains("valide");
    }
}
