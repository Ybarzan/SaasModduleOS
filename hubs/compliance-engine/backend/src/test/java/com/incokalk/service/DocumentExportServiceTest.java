package com.incokalk.service;

import com.incokalk.model.CustomsDeclaration;
import com.incokalk.model.DebDeclaration;
import com.incokalk.model.ExportDeclaration;
import com.incokalk.model.Ics2Declaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * generateQuotePdf/generateShippingLabelPdf/generateCmrPdf/generateDgdPdf/
 * generateCertificateOfOriginPdf ont été retirées avec le domaine Transport
 * (voir DocumentExportService). Seuls les exports de déclarations douanières
 * (DAU/DEB/ICS2/Export), sans rapport avec ce domaine, sont encore testés ici.
 */
@DisplayName("DocumentExportService — Tests PDF generation")
class DocumentExportServiceTest {

    @InjectMocks DocumentExportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Générer PDF DAU → bytes non vides et header PDF")
    void generateDauPdf_returnsValidPdf() {
        CustomsDeclaration d = CustomsDeclaration.builder()
                .declarationNumber("DAU-2026-0001")
                .declarationType(CustomsDeclaration.DeclarationType.DAU_IMPORT)
                .status(CustomsDeclaration.DeclarationStatus.DRAFT)
                .customsOffice("Paris CDG")
                .customsRegime("4000")
                .customsCode("1001")
                .originCountry("VN")
                .destinationCountry("FR")
                .declaredValue(new BigDecimal("15000.00"))
                .currency("EUR")
                .hsCode("620443")
                .goodsDescription("Robes en fibres synthétiques")
                .netWeight(new BigDecimal("250.00"))
                .grossWeight(new BigDecimal("300.00"))
                .packages(10)
                .build();

        byte[] pdf = service.generateDauPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer PDF DAU avec champs minimaux → pas d'exception")
    void generateDauPdf_minimalFields() {
        CustomsDeclaration d = CustomsDeclaration.builder()
                .declarationNumber("DAU-2026-0002")
                .build();

        byte[] pdf = service.generateDauPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Générer PDF DEB → bytes non vides et header PDF")
    void generateDebPdf_returnsValidPdf() {
        DebDeclaration d = DebDeclaration.builder()
                .declarationNumber("DEB-2026-07-001")
                .declarationType(DebDeclaration.DebType.DEB_INTRODUCTION)
                .status(DebDeclaration.DebStatus.DRAFT)
                .period("2026-07")
                .partnerCountry("DE")
                .natureOfTransaction("10")
                .modeOfTransport("3")
                .hsCode8("62044300")
                .netMass(new BigDecimal("120.50"))
                .statisticalValue(new BigDecimal("8500.00"))
                .goodsDescription("Vêtements importés d'Allemagne")
                .build();

        byte[] pdf = service.generateDebPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer PDF DEB avec champs minimaux → pas d'exception")
    void generateDebPdf_minimalFields() {
        DebDeclaration d = DebDeclaration.builder()
                .declarationNumber("DEB-2026-07-002")
                .build();

        byte[] pdf = service.generateDebPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Générer PDF ICS2 → bytes non vides et header PDF")
    void generateIcs2Pdf_returnsValidPdf() {
        Ics2Declaration d = Ics2Declaration.builder()
                .declarationNumber("ICS2-2026-0001")
                .status(Ics2Declaration.Ics2Status.DRAFT)
                .senderEori("FR123456789")
                .receiverEori("DE987654321")
                .vesselName("MSC Diana")
                .voyageNumber("MD2607")
                .containerNumber("MSKU1234567")
                .hsCode6("620443")
                .goodsDescription("Vêtements conteneur")
                .grossWeight(new BigDecimal("5000.00"))
                .packagesCount(20)
                .build();

        byte[] pdf = service.generateIcs2Pdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer PDF ICS2 avec champs minimaux → pas d'exception")
    void generateIcs2Pdf_minimalFields() {
        Ics2Declaration d = Ics2Declaration.builder()
                .declarationNumber("ICS2-2026-0002")
                .build();

        byte[] pdf = service.generateIcs2Pdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Générer PDF Export → bytes non vides et header PDF")
    void generateExportPdf_returnsValidPdf() {
        ExportDeclaration d = ExportDeclaration.builder()
                .declarationNumber("EXP-2026-0001")
                .declarationType(ExportDeclaration.ExportType.AES)
                .status(ExportDeclaration.ExportStatus.DRAFT)
                .exporterEori("FR123456789")
                .destinationCountry("US")
                .hsCode("620443")
                .declaredValue(new BigDecimal("25000.00"))
                .currency("EUR")
                .netWeight(new BigDecimal("400.00"))
                .grossWeight(new BigDecimal("500.00"))
                .packagesCount(15)
                .goodsDescription("Textile exporté vers USA")
                .build();

        byte[] pdf = service.generateExportPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer PDF Export avec champs minimaux → pas d'exception")
    void generateExportPdf_minimalFields() {
        ExportDeclaration d = ExportDeclaration.builder()
                .declarationNumber("EXP-2026-0002")
                .build();

        byte[] pdf = service.generateExportPdf(d);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Générer XML DAU → contient les champs clés")
    void generateDauXml_containsKeyFields() {
        CustomsDeclaration d = CustomsDeclaration.builder()
                .declarationNumber("DAU-2026-0003")
                .originCountry("VN")
                .destinationCountry("FR")
                .build();

        byte[] xml = service.generateDauXml(d);
        String xmlStr = new String(xml, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(xmlStr).contains("DAU-2026-0003").contains("VN").contains("FR");
    }
}
