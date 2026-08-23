package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.EoriNumber;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EoriNumberRepository;
import com.incokalk.service.CustomsInvoiceService.CustomsInvoiceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CustomsInvoiceService — Tests unitaires")
class CustomsInvoiceServiceTest {

    @Mock CompanyRepository companyRepo;
    @Mock EoriNumberRepository eoriRepo;
    @InjectMocks CustomsInvoiceService service;

    UUID companyId;
    UUID shipmentId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
    }

    // --------------------------------------------------------------------
    // Helper builder for the CustomsInvoiceData record (27 positional args)
    // --------------------------------------------------------------------
    private static class DataBuilder {
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        String sellerName = "Acme Export SARL";
        String sellerAddress = "1 Rue de Commerce, Paris";
        String sellerCountry = "FR";
        String buyerName = "Global Import Ltd";
        String buyerAddress = "22 Main St, London";
        String buyerCountry = "GB";
        String consigneeName = "Warehouse Co";
        String consigneeAddress = "5 Dock Rd, Southampton";
        String consigneeCountry = "GB";
        String goodsDescription = "Textiles";
        String hsCode = "620443";
        String originCountry = "VN";
        double quantity = 100;
        String quantityUnit = "PCS";
        double unitPrice = 12.5;
        double totalPrice = 1250.0;
        String currency = "EUR";
        double grossWeight = 300.0;
        double netWeight = 250.0;
        String incoterm = "FOB";
        String transportMode = "Maritime";
        String countryOfOrigin = "VN";
        String invoiceNumber = "INV-2026-0001";
        String eoriNumber = "FR123456789";
        String loadingPort = "Le Havre";
        String unloadingPort = "Southampton";
        String finalDestinationCountry = "GB";
        String customsRegime = "4000";
        String transactionNature = "A";

        DataBuilder sellerName(String v) { this.sellerName = v; return this; }
        DataBuilder sellerAddress(String v) { this.sellerAddress = v; return this; }
        DataBuilder sellerCountry(String v) { this.sellerCountry = v; return this; }
        DataBuilder buyerName(String v) { this.buyerName = v; return this; }
        DataBuilder buyerAddress(String v) { this.buyerAddress = v; return this; }
        DataBuilder buyerCountry(String v) { this.buyerCountry = v; return this; }
        DataBuilder consigneeName(String v) { this.consigneeName = v; return this; }
        DataBuilder consigneeAddress(String v) { this.consigneeAddress = v; return this; }
        DataBuilder consigneeCountry(String v) { this.consigneeCountry = v; return this; }
        DataBuilder goodsDescription(String v) { this.goodsDescription = v; return this; }
        DataBuilder hsCode(String v) { this.hsCode = v; return this; }
        DataBuilder originCountry(String v) { this.originCountry = v; return this; }
        DataBuilder quantityUnit(String v) { this.quantityUnit = v; return this; }
        DataBuilder currency(String v) { this.currency = v; return this; }
        DataBuilder incoterm(String v) { this.incoterm = v; return this; }
        DataBuilder transportMode(String v) { this.transportMode = v; return this; }
        DataBuilder invoiceNumber(String v) { this.invoiceNumber = v; return this; }
        DataBuilder eoriNumber(String v) { this.eoriNumber = v; return this; }
        DataBuilder loadingPort(String v) { this.loadingPort = v; return this; }
        DataBuilder unloadingPort(String v) { this.unloadingPort = v; return this; }
        DataBuilder finalDestinationCountry(String v) { this.finalDestinationCountry = v; return this; }
        DataBuilder customsRegime(String v) { this.customsRegime = v; return this; }
        DataBuilder transactionNature(String v) { this.transactionNature = v; return this; }

        /** Nullifies every nullable String field that has a fallback in the service. */
        DataBuilder allOptionalStringsNull() {
            sellerName = null; sellerAddress = null; sellerCountry = null;
            buyerName = null; buyerAddress = null; buyerCountry = null;
            consigneeName = null; consigneeAddress = null; consigneeCountry = null;
            goodsDescription = null; hsCode = null; originCountry = null;
            quantityUnit = null; currency = null; incoterm = null; transportMode = null;
            invoiceNumber = null; eoriNumber = null;
            loadingPort = null; unloadingPort = null; finalDestinationCountry = null;
            customsRegime = null; transactionNature = null;
            return this;
        }

        CustomsInvoiceData build() {
            return new CustomsInvoiceData(
                    companyId, shipmentId,
                    sellerName, sellerAddress, sellerCountry,
                    buyerName, buyerAddress, buyerCountry,
                    consigneeName, consigneeAddress, consigneeCountry,
                    goodsDescription, hsCode, originCountry,
                    quantity, quantityUnit, unitPrice, totalPrice, currency,
                    grossWeight, netWeight,
                    incoterm, transportMode, countryOfOrigin,
                    invoiceNumber, eoriNumber,
                    loadingPort, unloadingPort, finalDestinationCountry,
                    customsRegime, transactionNature
            );
        }
    }

    private void assertValidPdf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    // --------------------------------------------------------------------
    // generatePdf — full data / all fallbacks combinations
    // --------------------------------------------------------------------

    @Test
    @DisplayName("generatePdf → full data, preferential duty with savings")
    void generatePdf_fullData_preferentialWithSavings() {
        CustomsInvoiceData data = new DataBuilder().build();
        Map<String, Object> dutyResult = new HashMap<>();
        dutyResult.put("appliedRate", 12.5);
        dutyResult.put("isPrefential", true);
        dutyResult.put("agreement", "EU-VN FTA");
        dutyResult.put("savings", 150.0);

        byte[] pdf = service.generatePdf(data, dutyResult);

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → all optional strings null, no duty result")
    void generatePdf_minimalData_nullFallbacks_noDutyResult() {
        CustomsInvoiceData data = new DataBuilder().allOptionalStringsNull().build();

        byte[] pdf = service.generatePdf(data, null);

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → blank EORI number skips EORI paragraph")
    void generatePdf_blankEori_skipsEoriParagraph() {
        CustomsInvoiceData data = new DataBuilder().eoriNumber("").build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → blank consignee name skips consignee block")
    void generatePdf_blankConsigneeName_skipsConsigneeBlock() {
        CustomsInvoiceData data = new DataBuilder().consigneeName("").build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → consignee name present but address/country null")
    void generatePdf_consigneeNamePresent_addressAndCountryNull() {
        CustomsInvoiceData data = new DataBuilder()
                .consigneeName("Warehouse Co")
                .consigneeAddress(null)
                .consigneeCountry(null)
                .build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → only loading port present among transport fields")
    void generatePdf_onlyLoadingPortPresent() {
        CustomsInvoiceData data = new DataBuilder()
                .loadingPort("Le Havre")
                .unloadingPort(null)
                .finalDestinationCountry(null)
                .build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → only unloading port present among transport fields")
    void generatePdf_onlyUnloadingPortPresent() {
        CustomsInvoiceData data = new DataBuilder()
                .loadingPort(null)
                .unloadingPort("Southampton")
                .finalDestinationCountry(null)
                .build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → only final destination country present among transport fields")
    void generatePdf_onlyFinalDestinationPresent() {
        CustomsInvoiceData data = new DataBuilder()
                .loadingPort(null)
                .unloadingPort(null)
                .finalDestinationCountry("GB")
                .build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → quantity unit null falls back to PCS")
    void generatePdf_quantityUnitNull_fallsBackToPcs() {
        CustomsInvoiceData data = new DataBuilder().quantityUnit(null).build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    // --------------------------------------------------------------------
    // generatePdf — dutyResult branch combinations
    // --------------------------------------------------------------------

    @Test
    @DisplayName("generatePdf → duty result present, non-preferential, no savings")
    void generatePdf_dutyResult_nonPreferential_noSavings() {
        CustomsInvoiceData data = new DataBuilder().build();
        Map<String, Object> dutyResult = new HashMap<>();
        dutyResult.put("appliedRate", 5.0);
        dutyResult.put("isPrefential", false);

        byte[] pdf = service.generatePdf(data, dutyResult);

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → duty result with missing keys (rate/isPrefential/savings absent)")
    void generatePdf_dutyResult_missingKeys() {
        CustomsInvoiceData data = new DataBuilder().build();

        byte[] pdf = service.generatePdf(data, Map.of());

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → duty result savings equal to zero is not shown")
    void generatePdf_dutyResult_savingsZero_notShown() {
        CustomsInvoiceData data = new DataBuilder().build();
        Map<String, Object> dutyResult = new HashMap<>();
        dutyResult.put("appliedRate", 8.0);
        dutyResult.put("isPrefential", true);
        dutyResult.put("agreement", "EU-VN FTA");
        dutyResult.put("savings", 0.0);

        byte[] pdf = service.generatePdf(data, dutyResult);

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → duty result savings negative is not shown")
    void generatePdf_dutyResult_savingsNegative_notShown() {
        CustomsInvoiceData data = new DataBuilder().build();
        Map<String, Object> dutyResult = new HashMap<>();
        dutyResult.put("appliedRate", 8.0);
        dutyResult.put("isPrefential", false);
        dutyResult.put("savings", -10.0);

        byte[] pdf = service.generatePdf(data, dutyResult);

        assertValidPdf(pdf);
    }

    @Test
    @DisplayName("generatePdf → exception during document generation is wrapped in RuntimeException")
    void generatePdf_nullData_throwsWrappedRuntimeException() {
        assertThatThrownBy(() -> service.generatePdf(null, Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Impossible de generer la facture douaniere");
    }

    // --------------------------------------------------------------------
    // generateFromShipment
    // --------------------------------------------------------------------

    @Test
    @DisplayName("generateFromShipment → eori found, all optional fields present")
    void generateFromShipment_eoriFound_fieldsPresent() {
        EoriNumber eori = EoriNumber.builder().eori("FR987654321").build();
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.of(eori));

        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId)
                .orderNumber("SO-2026-0001")
                .shipperName("Acme Export SARL")
                .shipperAddress("1 Rue de Commerce, Paris")
                .shipperCountry("FR")
                .consigneeName("Global Import Ltd")
                .consigneeAddress("22 Main St, London")
                .consigneeCountry("GB")
                .goodsDescription("Textiles")
                .hsCode("620443")
                .countryOfOrigin("VN")
                .goodsValue(1000.0)
                .weightKg(500.0)
                .incotermCode("FOB")
                .costCurrency("USD")
                .dutyAmount(50.0)
                .build();

        byte[] pdf = service.generateFromShipment(shipment, companyId);

        assertValidPdf(pdf);
        verify(eoriRepo).findByCompanyIdAndIsDefaultTrue(companyId);
    }

    @Test
    @DisplayName("generateFromShipment → no eori found, null optional fields fall back to defaults")
    void generateFromShipment_noEoriFound_nullFieldsFallBack() {
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());

        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId)
                .orderNumber("SO-2026-0002")
                .shipperName(null)
                .shipperAddress(null)
                .shipperCountry(null)
                .consigneeName(null)
                .consigneeAddress(null)
                .consigneeCountry(null)
                .goodsDescription(null)
                .hsCode(null)
                .countryOfOrigin(null)
                .goodsValue(null)
                .weightKg(null)
                .incotermCode(null)
                .costCurrency(null)
                .dutyAmount(null)
                .build();

        byte[] pdf = service.generateFromShipment(shipment, companyId);

        assertValidPdf(pdf);
        verify(eoriRepo).findByCompanyIdAndIsDefaultTrue(companyId);
    }
}
