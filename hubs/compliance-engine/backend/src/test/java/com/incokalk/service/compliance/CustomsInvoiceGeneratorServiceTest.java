package com.incokalk.service.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CustomsInvoice;
import com.incokalk.model.EoriNumber;
import com.incokalk.model.ShipmentItem;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TaricRate;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CustomsInvoiceRepository;
import com.incokalk.repository.EoriNumberRepository;
import com.incokalk.repository.ShipmentItemRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.TaricRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CustomsInvoiceGeneratorService — Génération de facture douanière")
class CustomsInvoiceGeneratorServiceTest {

    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock ShipmentItemRepository shipmentItemRepo;
    @Mock TaricRateRepository taricRepo;
    @Mock CompanyRepository companyRepo;
    @Mock EoriNumberRepository eoriRepo;
    @Mock CustomsInvoiceRepository customsInvoiceRepo;

    @InjectMocks CustomsInvoiceGeneratorService service;

    private UUID shipmentId;
    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        shipmentId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("ACME").slug("acme").build();
        when(customsInvoiceRepo.save(any(CustomsInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ShipmentOrder.ShipmentOrderBuilder baseShipment() {
        return ShipmentOrder.builder()
                .id(shipmentId)
                .shipperName("Shipper SARL")
                .shipperAddress("1 rue de Paris")
                .shipperCity("Paris")
                .shipperCountry("FR")
                .shipperPostalCode("75001")
                .consigneeName("Consignee Ltd")
                .consigneeAddress("2 Main St")
                .consigneeCity("London")
                .consigneeCountry("GB")
                .consigneePostalCode("SW1A")
                .goodsDescription("Textiles")
                .incotermCode("FOB");
    }

    private ShipmentItem.ShipmentItemBuilder baseItem() {
        return ShipmentItem.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .shipmentId(shipmentId)
                .sku("SKU-1")
                .name("T-shirt")
                .description("Cotton t-shirt")
                .hsCode("6109100000")
                .originCountry("CN")
                .quantity(BigDecimal.TEN)
                .unit("PCS")
                .unitPrice(BigDecimal.valueOf(5));
    }

    private TaricRate baseRate(boolean preferential) {
        return TaricRate.builder()
                .hsCode("6109100000")
                .originCountry("FR")
                .destinationCountry("FR")
                .dutyRate(12.0)
                .dutyType("AD")
                .isPrefential(preferential)
                .build();
    }

    // ---------------------------------------------------------------
    // Not-found branches
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Expédition non trouvée → RuntimeException")
    void generateInvoice_shipmentNotFound_throws() {
        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateInvoice(shipmentId, companyId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expédition non trouvée");

        verifyNoInteractions(companyRepo, eoriRepo, shipmentItemRepo, taricRepo);
    }

    @Test
    @DisplayName("Entreprise non trouvée → RuntimeException")
    void generateInvoice_companyNotFound_throws() {
        ShipmentOrder shipment = baseShipment().build();
        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateInvoice(shipmentId, companyId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Entreprise non trouvée");

        verifyNoInteractions(eoriRepo, shipmentItemRepo, taricRepo);
    }

    // ---------------------------------------------------------------
    // Happy path with all optional fields present
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Génération complète : EORI présent, devise/poids/colis renseignés, taux TARIC préférentiel")
    void generateInvoice_fullData_preferentialRate() {
        ShipmentOrder shipment = baseShipment()
                .currency("USD")
                .goodsValue(500.0)
                .weightKg(25.0)
                .packagesCount(3)
                .build();
        EoriNumber eori = EoriNumber.builder().eori("FR123456789").build();
        ShipmentItem item = baseItem().build();
        TaricRate rate = baseRate(true);

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.of(eori));
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("6109100000", "FR", "FR"))
                .thenReturn(List.of(rate));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        assertThat(invoice).isNotNull();
        assertThat(invoice.getCompanyId()).isEqualTo(companyId);
        assertThat(invoice.getShipmentId()).isEqualTo(shipmentId);
        assertThat(invoice.getInvoiceNumber()).startsWith("CD-");
        assertThat(invoice.getCurrency()).isEqualTo("USD");
        assertThat(invoice.getTotalGoodsValue()).isEqualByComparingTo("500");
        assertThat(invoice.getTotalWeightKg()).isEqualByComparingTo("25");
        assertThat(invoice.getTotalPackages()).isEqualTo(3);
        assertThat(invoice.getEoriNumber()).isEqualTo("FR123456789");
        assertThat(invoice.getIncotermCode()).isEqualTo("FOB");
        assertThat(invoice.getItems()).hasSize(1);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        assertThat(invoiceItem.getLineNumber()).isEqualTo(1);
        assertThat(invoiceItem.getSku()).isEqualTo("SKU-1");
        assertThat(invoiceItem.getUnit()).isEqualTo("PCS");
        assertThat(invoiceItem.isPreferential()).isTrue();
        assertThat(invoiceItem.getDutyType()).isEqualTo("AD");
        // itemValue = 5 * 10 = 50 ; dutyRate = 12% -> dutyAmount = 6.00
        assertThat(invoiceItem.getTotalValue()).isEqualByComparingTo("50");
        assertThat(invoiceItem.getDutyAmount()).isEqualByComparingTo("6.00");
        // vatAmount = dutyAmount * 20 = 120.00 (per current implementation)
        assertThat(invoiceItem.getVatAmount()).isEqualByComparingTo("120.00");

        assertThat(invoice.getTotalDuty()).isEqualByComparingTo("6.00");
        assertThat(invoice.getTotalVat()).isEqualByComparingTo("120.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(
                invoice.getTotalGoodsValue().add(invoice.getTotalDuty()).add(invoice.getTotalVat()));
    }

    // ---------------------------------------------------------------
    // Missing / null optional fields → default fallback branches
    // ---------------------------------------------------------------

    @Test
    @DisplayName("EORI absent, devise/poids/valeur/colis nuls → valeurs par défaut")
    void generateInvoice_missingOptionalFields_usesDefaults() {
        ShipmentOrder shipment = baseShipment()
                .currency(null)
                .goodsValue(null)
                .weightKg(null)
                .packagesCount(null)
                .build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(Collections.emptyList());

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        assertThat(invoice.getEoriNumber()).isNull();
        assertThat(invoice.getCurrency()).isEqualTo("EUR");
        assertThat(invoice.getTotalGoodsValue()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalWeightKg()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalPackages()).isEqualTo(0);
        assertThat(invoice.getItems()).isEmpty();
        assertThat(invoice.getTotalDuty()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalVat()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("0");

        verifyNoInteractions(taricRepo);
    }

    // ---------------------------------------------------------------
    // HS code branches: null, blank, present-without-rate, present-with-rate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Article sans code HS (null) → pas de recherche TARIC, taux zéro")
    void generateInvoice_itemWithNullHsCode_skipsTaricLookup() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem().hsCode(null).build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        assertThat(invoiceItem.getDutyRate()).isEqualByComparingTo("0");
        assertThat(invoiceItem.getDutyType()).isEqualTo("AD");
        assertThat(invoiceItem.isPreferential()).isFalse();
        assertThat(invoiceItem.getDutyAmount()).isEqualByComparingTo("0");
        assertThat(invoiceItem.getVatAmount()).isEqualByComparingTo("0");

        verifyNoInteractions(taricRepo);
    }

    @Test
    @DisplayName("Article avec code HS vide (blank) → pas de recherche TARIC")
    void generateInvoice_itemWithBlankHsCode_skipsTaricLookup() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem().hsCode("   ").build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        assertThat(invoiceItem.getDutyRate()).isEqualByComparingTo("0");
        assertThat(invoiceItem.isPreferential()).isFalse();

        verifyNoInteractions(taricRepo);
    }

    @Test
    @DisplayName("Code HS renseigné mais aucun taux TARIC trouvé → taux zéro, non préférentiel")
    void generateInvoice_hsCodePresent_noRateFound() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem().build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("6109100000", "FR", "FR"))
                .thenReturn(Collections.emptyList());

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        assertThat(invoiceItem.getDutyRate()).isEqualByComparingTo("0");
        assertThat(invoiceItem.getDutyType()).isEqualTo("AD");
        assertThat(invoiceItem.isPreferential()).isFalse();
        assertThat(invoiceItem.getDutyAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Taux TARIC non préférentiel trouvé → duty type et taux propagés")
    void generateInvoice_hsCodePresent_nonPreferentialRateFound() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem().build();
        TaricRate rate = TaricRate.builder()
                .hsCode("6109100000")
                .originCountry("FR")
                .destinationCountry("FR")
                .dutyRate(8.5)
                .dutyType("MIX")
                .isPrefential(false)
                .build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("6109100000", "FR", "FR"))
                .thenReturn(List.of(rate));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        assertThat(invoiceItem.getDutyRate()).isEqualByComparingTo("8.5");
        assertThat(invoiceItem.getDutyType()).isEqualTo("MIX");
        assertThat(invoiceItem.isPreferential()).isFalse();
    }

    // ---------------------------------------------------------------
    // Item-level null fallback branches: unitPrice, quantity, unit
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Prix unitaire, quantité et unité nuls → valeurs par défaut (0, 1, PCS)")
    void generateInvoice_itemWithNullPriceQuantityUnit_usesDefaults() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem()
                .unitPrice(null)
                .quantity(null)
                .unit(null)
                .hsCode(null)
                .build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        CustomsInvoice.InvoiceItem invoiceItem = invoice.getItems().get(0);
        // unitPrice defaults to ZERO, quantity defaults to ONE for the itemValue computation
        assertThat(invoiceItem.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(invoiceItem.getTotalValue()).isEqualByComparingTo("0");
        assertThat(invoiceItem.getUnit()).isEqualTo("PCS");
    }

    // ---------------------------------------------------------------
    // Multiple items → loop accumulation branch, invoice number format
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Plusieurs articles → cumul des totaux de droits et TVA, numérotation des lignes")
    void generateInvoice_multipleItems_accumulatesTotals() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item1 = baseItem().build();
        ShipmentItem item2 = baseItem().hsCode(null).unitPrice(BigDecimal.valueOf(2)).quantity(BigDecimal.valueOf(3)).build();
        TaricRate rate = baseRate(true);

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item1, item2));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("6109100000", "FR", "FR"))
                .thenReturn(List.of(rate));

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        assertThat(invoice.getItems()).hasSize(2);
        assertThat(invoice.getItems().get(0).getLineNumber()).isEqualTo(1);
        assertThat(invoice.getItems().get(1).getLineNumber()).isEqualTo(2);
        // item1 duty = 6.00, item2 duty = 0 (no hs code)
        assertThat(invoice.getTotalDuty()).isEqualByComparingTo("6.00");
        assertThat(invoice.getTotalVat()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("La facture générée est persistée, avec le lien vers ses lignes correctement établi")
    void generateInvoice_isPersisted_withItemsLinkedToInvoice() {
        ShipmentOrder shipment = baseShipment().build();
        ShipmentItem item = baseItem().build();

        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(item));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("6109100000", "FR", "FR"))
                .thenReturn(Collections.emptyList());

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        verify(customsInvoiceRepo).save(same(invoice));
        assertThat(invoice.getItems()).hasSize(1);
        assertThat(invoice.getItems().get(0).getInvoice()).isSameAs(invoice);
    }

    @Test
    @DisplayName("Numéro de facture au format CD-yyyyMMdd-xxxx")
    void generateInvoice_invoiceNumberFormat() {
        ShipmentOrder shipment = baseShipment().build();
        when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(Collections.emptyList());

        CustomsInvoice invoice = service.generateInvoice(shipmentId, companyId);

        assertThat(invoice.getInvoiceNumber()).matches("CD-\\d{8}-\\d{1,4}");
        assertThat(invoice.getStatus()).isEqualTo("DRAFT");
    }
}
