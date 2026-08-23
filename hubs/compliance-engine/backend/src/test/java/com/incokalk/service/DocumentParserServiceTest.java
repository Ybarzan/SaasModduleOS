package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.ParsedDocument;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ParsedDocumentRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DocumentParserService — Tests parsing + stats")
class DocumentParserServiceTest {

    @Mock ParsedDocumentRepository parsedDocRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks DocumentParserService service;

    private UUID companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        TenantContext.set(companyId);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── parseFromText — COMMERCIAL_INVOICE ──────────────────────────────

    @Test
    @DisplayName("parse — commercial invoice extracts seller and buyer")
    void parseCommercialInvoice() {
        String text = """
                SELLER: ACME Corporation
                BUYER: Import SARL Paris
                Invoice No: INV-2026-001
                Total Amount: 15000.00 EUR
                HS Code: 610910
                Country of Origin: Morocco
                """;

        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "invoice.pdf", companyId);

        assertThat(result.getDocumentType()).isEqualTo(ParsedDocument.DocumentType.COMMERCIAL_INVOICE);
        assertThat(result.getOriginalFilename()).isEqualTo("invoice.pdf");
        assertThat(result.getParsedData()).containsKey("seller");
        assertThat(result.getParsedData().get("seller").toString()).containsIgnoringCase("ACME");
        assertThat(result.getParsedData()).containsKey("buyer");
        assertThat(result.getParsedData().get("buyer").toString()).containsIgnoringCase("Import SARL");
        assertThat(result.getParsedData()).containsEntry("invoiceNumber", "INV-2026-001");
        assertThat(result.getParsedData()).containsEntry("hsCode", "610910");
        assertThat(result.getConfidence()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getStatus()).isEqualTo(ParsedDocument.ParseStatus.PARSED);
        verify(parsedDocRepo).save(any());
    }

    // ── parseFromText — BILL_OF_LADING ──────────────────────────────────

    @Test
    @DisplayName("parse — bill of lading extracts key fields")
    void parseBillOfLading() {
        String text = """
                B/L No: MAEU123456789
                Shipper: Global Trading Co
                Consignee: European Imports GmbH
                Port of Loading: Casablanca
                Port of Discharge: Hamburg
                Container No: MSKU1234567
                Gross Weight: 25000 kg
                """;

        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.BILL_OF_LADING, "bol.pdf", companyId);

        assertThat(result.getParsedData()).containsEntry("blNumber", "MAEU123456789");
        assertThat(result.getParsedData().get("shipper").toString()).containsIgnoringCase("Global Trading");
        assertThat(result.getParsedData().get("consignee").toString()).containsIgnoringCase("European Imports");
        assertThat(result.getParsedData()).containsEntry("portOfLoading", "Casablanca");
        assertThat(result.getParsedData()).containsEntry("portOfDischarge", "Hamburg");
        assertThat(result.getParsedData()).containsEntry("containerNumber", "MSKU1234567");
        assertThat(result.getParsedData()).containsEntry("grossWeight", "25000 kg");
    }

    // ── parseFromText — CERTIFICATE_OF_ORIGIN ───────────────────────────

    @Test
    @DisplayName("parse — certificate of origin extracts fields")
    void parseCertificateOfOrigin() {
        String text = """
                Certificate No: CO-2026-0456
                Exporter: Moroccan Textiles SA
                Consignee: French Fashion SARL
                Country of Origin: Morocco
                Country of Destination: France
                HS Code: 620443
                """;

        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.CERTIFICATE_OF_ORIGIN, "cert.pdf", companyId);

        assertThat(result.getParsedData()).containsEntry("certificateNumber", "CO-2026-0456");
        assertThat(result.getParsedData().get("exporter").toString()).containsIgnoringCase("Moroccan Textiles");
        assertThat(result.getParsedData()).containsEntry("countryOfOrigin", "Morocco");
        assertThat(result.getParsedData()).containsEntry("countryOfDestination", "France");
        assertThat(result.getParsedData()).containsEntry("hsCode", "620443");
    }

    // ── parseFromText — PACKING_LIST ────────────────────────────────────

    @Test
    @DisplayName("parse — packing list extracts fields")
    void parsePackingList() {
        String text = """
                Packing List No: PL-2026-789
                Shipper: Textile Corp
                Consignee: Retail EU
                Number of Packages: 120
                Total Net Weight: 3500 kg
                Total Gross Weight: 4200 kg
                Total Volume: 12.5 m3
                """;

        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.PACKING_LIST, "packing.pdf", companyId);

        assertThat(result.getParsedData()).containsEntry("packingListNumber", "PL-2026-789");
        assertThat(result.getParsedData()).containsEntry("numberOfPackages", "120");
        assertThat(result.getParsedData()).containsEntry("totalNetWeight", "3500 kg");
        assertThat(result.getParsedData()).containsEntry("totalGrossWeight", "4200 kg");
        assertThat(result.getParsedData()).containsEntry("totalVolume", "12.5 m3");
    }

    // ── parseFromText — empty text ──────────────────────────────────────

    @Test
    @DisplayName("parse — empty text returns low confidence")
    void parseEmptyText() {
        ParsedDocument result = service.parseFromText("",
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "empty.pdf", companyId);

        assertThat(result.getParsedData()).isNotNull();
        assertThat(result.getConfidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── parseFromText — French keywords ─────────────────────────────────

    @Test
    @DisplayName("parse — French keywords extract fields")
    void parseFrenchKeywords() {
        String text = """
                Vendeur: Société Marocaine SA
                Acheteur: Import France SAS
                N° Facture: F-2026-001
                Pays d'origine: Maroc
                Code SH: 847130
                """;

        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "facture.pdf", companyId);

        assertThat(result.getParsedData().get("seller").toString()).containsIgnoringCase("Marocaine");
        assertThat(result.getParsedData().get("buyer").toString()).containsIgnoringCase("Import France");
        assertThat(result.getParsedData()).containsEntry("countryOfOrigin", "Maroc");
        assertThat(result.getParsedData()).containsEntry("hsCode", "847130");
    }

    // ── parseFromPdf ────────────────────────────────────────────────────

    @Test
    @DisplayName("parseFromPdf — handles invalid bytes gracefully")
    void parseFromPdfInvalidBytes() {
        ParsedDocument result = service.parseFromPdf(new byte[]{0, 1, 2, 3},
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "bad.pdf", companyId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ParsedDocument.ParseStatus.PARSED);
    }

    // ── company not found ──────────────────────────────────────────────

    @Test
    @DisplayName("parse — company not found throws")
    void parseCompanyNotFound() {
        UUID unknownId = UUID.randomUUID();
        TenantContext.set(unknownId);
        when(companyRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.parseFromText("text",
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "test.pdf", unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company not found");
    }

    // ── getHistory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory — returns company documents")
    void getHistory() {
        ParsedDocument doc = ParsedDocument.builder()
                .id(UUID.randomUUID())
                .company(company)
                .documentType(ParsedDocument.DocumentType.COMMERCIAL_INVOICE)
                .originalFilename("test.pdf")
                .build();
        when(parsedDocRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(doc));

        List<ParsedDocument> result = service.getHistory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalFilename()).isEqualTo("test.pdf");
    }

    // ── getById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById — returns document when found")
    void getByIdFound() {
        UUID docId = UUID.randomUUID();
        ParsedDocument doc = ParsedDocument.builder().id(docId).company(company).build();
        when(parsedDocRepo.findByCompanyIdAndId(companyId, docId))
                .thenReturn(Optional.of(doc));

        ParsedDocument result = service.getById(docId);
        assertThat(result.getId()).isEqualTo(docId);
    }

    @Test
    @DisplayName("getById — throws when not found")
    void getByIdNotFound() {
        UUID docId = UUID.randomUUID();
        when(parsedDocRepo.findByCompanyIdAndId(companyId, docId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(docId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parsed document not found");
    }

    // ── getStats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats — returns stats")
    void getStats() {
        when(parsedDocRepo.countByCompanyId(companyId)).thenReturn(20L);
        when(parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.PARSED)).thenReturn(15L);
        when(parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.VERIFIED)).thenReturn(3L);
        when(parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.REJECTED)).thenReturn(2L);

        Map<String, Object> stats = service.getStats();

        assertThat(stats).containsEntry("total", 20L);
        assertThat(stats).containsEntry("parsed", 15L);
        assertThat(stats).containsEntry("verified", 3L);
        assertThat(stats).containsEntry("rejected", 2L);
    }

    // ── getByType ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getByType — filters by document type")
    void getByType() {
        ParsedDocument doc = ParsedDocument.builder()
                .id(UUID.randomUUID())
                .company(company)
                .documentType(ParsedDocument.DocumentType.BILL_OF_LADING)
                .build();
        when(parsedDocRepo.findByCompanyIdAndDocumentTypeOrderByCreatedAtDesc(
                companyId, ParsedDocument.DocumentType.BILL_OF_LADING))
                .thenReturn(List.of(doc));

        List<ParsedDocument> result = service.getByType(ParsedDocument.DocumentType.BILL_OF_LADING);
        assertThat(result).hasSize(1);
    }

    // ── confidence calculation ──────────────────────────────────────────

    @Test
    @DisplayName("parse — confidence reflects field fill rate")
    void parseConfidence() {
        String text = "Seller: TestCo";
        ParsedDocument result = service.parseFromText(text,
                ParsedDocument.DocumentType.COMMERCIAL_INVOICE, "test.pdf", companyId);

        assertThat(result.getConfidence()).isBetween(BigDecimal.ZERO, new BigDecimal("100.000"));
        assertThat(result.getConfidence()).isGreaterThan(BigDecimal.ZERO);
    }
}
