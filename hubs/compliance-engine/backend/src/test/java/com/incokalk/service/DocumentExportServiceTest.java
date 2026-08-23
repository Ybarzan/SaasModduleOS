package com.incokalk.service;

import com.incokalk.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DocumentExportService — Tests PDF generation")
class DocumentExportServiceTest {

    @Mock FileStorageService fileStorage;
    @Mock BrandingService brandingService;
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

    private ShipmentOrder sampleShipment(Company company) {
        return ShipmentOrder.builder()
                .orderNumber("SHP-2026-0042")
                .company(company)
                .status(ShipmentOrder.Status.IN_TRANSIT)
                .shipperName("Atlas Import Export")
                .shipperAddress("12 Quai des Docks")
                .shipperCity("Le Havre")
                .shipperPostalCode("76600")
                .shipperCountry("FR")
                .consigneeName("Vinh Logistics Co.")
                .consigneeAddress("45 Nguyen Hue")
                .consigneeCity("Ho Chi Minh City")
                .consigneePostalCode("70000")
                .consigneeCountry("VN")
                .goodsDescription("Pièces détachées automobiles")
                .goodsValue(15000.0)
                .currency("EUR")
                .weightKg(1200.0)
                .volumeM3(4.5)
                .packagesCount(30)
                .hsCode("870899")
                .incotermCode("FOB")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Générer étiquette d'expédition → bytes non vides, header PDF, sans branding configuré")
    void generateShippingLabelPdf_withoutBranding_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        ShipmentOrder shipment = sampleShipment(company);

        byte[] pdf = service.generateShippingLabelPdf(shipment);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer étiquette d'expédition avec branding configuré → applique le branding sans exception")
    void generateShippingLabelPdf_withBranding_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        ShipmentOrder shipment = sampleShipment(company);

        CompanyBranding branding = CompanyBranding.builder()
                .company(company)
                .primaryColor("#1B6B4F")
                .portalTitle("Atlas Import Export")
                .footerText("Atlas Import Export — Transitaire agréé")
                .build();
        when(brandingService.getBranding(company.getId())).thenReturn(branding);

        byte[] pdf = service.generateShippingLabelPdf(shipment);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer CMR → bytes non vides et header PDF")
    void generateCmrPdf_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        byte[] pdf = service.generateCmrPdf(sampleShipment(company));

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer DGD pour expédition non dangereuse → PDF d'avertissement valide")
    void generateDgdPdf_notDangerous_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        ShipmentOrder shipment = sampleShipment(company);
        shipment.setDangerous(false);

        byte[] pdf = service.generateDgdPdf(shipment);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer DGD pour expédition dangereuse → PDF valide")
    void generateDgdPdf_dangerous_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        ShipmentOrder shipment = sampleShipment(company);
        shipment.setDangerous(true);

        byte[] pdf = service.generateDgdPdf(shipment);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer certificat d'origine → bytes non vides et header PDF")
    void generateCertificateOfOriginPdf_returnsValidPdf() {
        Company company = Company.builder().id(UUID.randomUUID()).name("Atlas Import Export").build();
        byte[] pdf = service.generateCertificateOfOriginPdf(sampleShipment(company));

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("Générer devis → bytes non vides et header PDF")
    void generateQuotePdf_returnsValidPdf() {
        com.incokalk.dto.shipment.QuoteRequestDTO request = com.incokalk.dto.shipment.QuoteRequestDTO.builder()
                .originCountry("FR")
                .destinationCountry("VN")
                .transportMode("SEA")
                .weightKg(1200.0)
                .volumeM3(4.5)
                .goodsValue(15000.0)
                .currency("EUR")
                .hsCode("870899")
                .build();

        com.incokalk.dto.shipment.QuoteResponseDTO quote = com.incokalk.dto.shipment.QuoteResponseDTO.builder()
                .carrierName("CMA CGM")
                .transportMode("SEA")
                .baseRate(1800.0)
                .totalCost(2100.0)
                .currency("EUR")
                .transitDaysMin(28)
                .transitDaysMax(35)
                .co2EstimateKg(950.0)
                .providerName("CMA CGM Direct")
                .build();

        byte[] pdf = service.generateQuotePdf(java.util.List.of(quote), request);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
