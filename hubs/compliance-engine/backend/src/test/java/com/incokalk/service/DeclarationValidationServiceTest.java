package com.incokalk.service;

import com.incokalk.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DeclarationValidationService — Tests unitaires")
class DeclarationValidationServiceTest {

    private DeclarationValidationService service;

    @BeforeEach
    void setUp() {
        service = new DeclarationValidationService();
    }

    @Test
    @DisplayName("validateDau: déclaration valide → aucune alerte")
    void validateDau_valid() {
        CustomsDeclaration d = new CustomsDeclaration();
        d.setHsCode("84713000");
        d.setDeclaredValue(BigDecimal.valueOf(5000));
        d.setNetWeight(BigDecimal.valueOf(100));
        d.setGrossWeight(BigDecimal.valueOf(120));
        d.setOriginCountry("FR");
        d.setDestinationCountry("DE");

        List<DeclarationValidationService.Alert> alerts = service.validateDau(d);
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("validateDau: code SH manquant → WARNING")
    void validateDau_missingHsCode() {
        CustomsDeclaration d = new CustomsDeclaration();
        d.setDeclaredValue(BigDecimal.valueOf(5000));

        List<DeclarationValidationService.Alert> alerts = service.validateDau(d);
        assertThat(alerts).anyMatch(a -> "HS_CODE_MISSING".equals(a.code()));
    }

    @Test
    @DisplayName("validateDau: valeur nulle → ERROR")
    void validateDau_nullValue() {
        CustomsDeclaration d = new CustomsDeclaration();
        d.setHsCode("84713000");

        List<DeclarationValidationService.Alert> alerts = service.validateDau(d);
        assertThat(alerts).anyMatch(a -> "VALUE_MISSING".equals(a.code()));
    }

    @Test
    @DisplayName("validateDau: poids net > brut → WARNING")
    void validateDau_weightIncoherent() {
        CustomsDeclaration d = new CustomsDeclaration();
        d.setHsCode("84713000");
        d.setDeclaredValue(BigDecimal.valueOf(5000));
        d.setNetWeight(BigDecimal.valueOf(150));
        d.setGrossWeight(BigDecimal.valueOf(100));

        List<DeclarationValidationService.Alert> alerts = service.validateDau(d);
        assertThat(alerts).anyMatch(a -> "WEIGHT_INCOHERENT".equals(a.code()));
    }

    @Test
    @DisplayName("validateDeb: déclaration valide")
    void validateDeb_valid() {
        DebDeclaration d = new DebDeclaration();
        d.setHsCode8("84713000");
        d.setPartnerCountry("DE");
        d.setStatisticalValue(BigDecimal.valueOf(10000));
        d.setNetMass(BigDecimal.valueOf(500));

        List<DeclarationValidationService.Alert> alerts = service.validateDeb(d);
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("validateDeb: code SH pas 8 chiffres → ERROR")
    void validateDeb_hsCodeLength() {
        DebDeclaration d = new DebDeclaration();
        d.setHsCode8("8471");
        d.setPartnerCountry("DE");
        d.setStatisticalValue(BigDecimal.valueOf(10000));

        List<DeclarationValidationService.Alert> alerts = service.validateDeb(d);
        assertThat(alerts).anyMatch(a -> "HS_CODE_LENGTH".equals(a.code()));
    }

    @Test
    @DisplayName("validateDeb: pays partenaire invalide → ERROR")
    void validateDeb_invalidPartner() {
        DebDeclaration d = new DebDeclaration();
        d.setHsCode8("84713000");
        d.setPartnerCountry("DEU");

        List<DeclarationValidationService.Alert> alerts = service.validateDeb(d);
        assertThat(alerts).anyMatch(a -> "PARTNER_INVALID".equals(a.code()));
    }

    @Test
    @DisplayName("validateIcs2: déclaration valide")
    void validateIcs2_valid() {
        Ics2Declaration d = new Ics2Declaration();
        d.setSenderEori("FR1234567890");
        d.setReceiverEori("DE9876543210");
        d.setHsCode6("847130");
        d.setGrossWeight(BigDecimal.valueOf(2000));

        List<DeclarationValidationService.Alert> alerts = service.validateIcs2(d);
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("validateIcs2: EORI invalide → ERROR")
    void validateIcs2_invalidEori() {
        Ics2Declaration d = new Ics2Declaration();
        d.setSenderEori("INVALID");
        d.setReceiverEori("DE9876543210");
        d.setHsCode6("847130");
        d.setGrossWeight(BigDecimal.valueOf(2000));

        List<DeclarationValidationService.Alert> alerts = service.validateIcs2(d);
        assertThat(alerts).anyMatch(a -> "SENDER_EORI_INVALID".equals(a.code()));
    }

    @Test
    @DisplayName("validateIcs2: code SH pas 6 → ERROR")
    void validateIcs2_hsCodeLength() {
        Ics2Declaration d = new Ics2Declaration();
        d.setSenderEori("FR1234567890");
        d.setReceiverEori("DE9876543210");
        d.setHsCode6("84713000");
        d.setGrossWeight(BigDecimal.valueOf(2000));

        List<DeclarationValidationService.Alert> alerts = service.validateIcs2(d);
        assertThat(alerts).anyMatch(a -> "HS_CODE_LENGTH".equals(a.code()));
    }

    @Test
    @DisplayName("validateExport: déclaration valide")
    void validateExport_valid() {
        ExportDeclaration d = new ExportDeclaration();
        d.setExporterEori("FR1234567890");
        d.setHsCode("847130");
        d.setDestinationCountry("US");
        d.setDeclaredValue(BigDecimal.valueOf(15000));
        d.setNetWeight(BigDecimal.valueOf(200));
        d.setGrossWeight(BigDecimal.valueOf(220));

        List<DeclarationValidationService.Alert> alerts = service.validateExport(d);
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("validateExport: code SH trop court → ERROR")
    void validateExport_hsCodeTooShort() {
        ExportDeclaration d = new ExportDeclaration();
        d.setExporterEori("FR1234567890");
        d.setHsCode("8471");
        d.setDestinationCountry("US");
        d.setDeclaredValue(BigDecimal.valueOf(15000));

        List<DeclarationValidationService.Alert> alerts = service.validateExport(d);
        assertThat(alerts).anyMatch(a -> "HS_CODE_SHORT".equals(a.code()));
    }

    @Test
    @DisplayName("validateExport: destination manquante → ERROR")
    void validateExport_missingDestination() {
        ExportDeclaration d = new ExportDeclaration();
        d.setExporterEori("FR1234567890");
        d.setHsCode("847130");
        d.setDeclaredValue(BigDecimal.valueOf(15000));

        List<DeclarationValidationService.Alert> alerts = service.validateExport(d);
        assertThat(alerts).anyMatch(a -> "DESTINATION_MISSING".equals(a.code()));
    }
}
