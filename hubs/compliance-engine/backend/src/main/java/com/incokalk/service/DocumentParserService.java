package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.ParsedDocument;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ParsedDocumentRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final ParsedDocumentRepository parsedDocRepo;
    private final CompanyRepository companyRepo;

    @Transactional
    public ParsedDocument parseFromText(String text, ParsedDocument.DocumentType docType,
                                         String filename, UUID companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Map<String, Object> parsed = parseFields(normalizeText(text), docType);
        BigDecimal confidence = calculateConfidence(parsed, docType);

        ParsedDocument doc = ParsedDocument.builder()
                .company(company)
                .documentType(docType)
                .originalFilename(filename)
                .rawText(text)
                .parsedData(parsed)
                .confidence(confidence)
                .status(ParsedDocument.ParseStatus.PARSED)
                .build();

        parsedDocRepo.save(doc);
        log.info("Document parsed: type={}, filename={}, confidence={}", docType, filename, confidence);
        return doc;
    }

    @Transactional
    public ParsedDocument parseFromPdf(byte[] pdfBytes, ParsedDocument.DocumentType docType,
                                         String filename, UUID companyId) {
        String text = extractPdfText(pdfBytes);
        return parseFromText(text, docType, filename, companyId);
    }

    public List<ParsedDocument> getHistory() {
        UUID companyId = TenantContext.get();
        return parsedDocRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public ParsedDocument getById(UUID id) {
        UUID companyId = TenantContext.get();
        return parsedDocRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new IllegalArgumentException("Parsed document not found"));
    }

    public List<ParsedDocument> getByType(ParsedDocument.DocumentType docType) {
        UUID companyId = TenantContext.get();
        return parsedDocRepo.findByCompanyIdAndDocumentTypeOrderByCreatedAtDesc(companyId, docType);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", parsedDocRepo.countByCompanyId(companyId));
        stats.put("parsed", parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.PARSED));
        stats.put("verified", parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.VERIFIED));
        stats.put("rejected", parsedDocRepo.countByCompanyIdAndStatus(companyId, ParsedDocument.ParseStatus.REJECTED));
        return stats;
    }

    // ── PDF text extraction ──────────────────────────────────────────────

    public String extractPdfText(byte[] pdfBytes) {
        try (var doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            log.warn("Failed to extract PDF text: {}", e.getMessage());
            return "";
        }
    }

    // ── Field parsing by document type ──────────────────────────────────

    private Map<String, Object> parseFields(String text, ParsedDocument.DocumentType docType) {
        return switch (docType) {
            case COMMERCIAL_INVOICE -> parseCommercialInvoice(text);
            case BILL_OF_LADING -> parseBillOfLading(text);
            case CERTIFICATE_OF_ORIGIN -> parseCertificateOfOrigin(text);
            case PACKING_LIST -> parsePackingList(text);
        };
    }

    private Map<String, Object> parseCommercialInvoice(String text) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("seller", extractField(text,
                Pattern.compile("(?:seller|vendor|vendeur|exp[eé]diteur|fournisseur)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("buyer", extractField(text,
                Pattern.compile("(?:buyer|customer|acheteur|client)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("invoiceNumber", extractField(text,
                Pattern.compile("(?:invoice\\s*(?:no|number|#|n[°o])|facture\\s*(?:no|n[°o]))[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("invoiceDate", extractField(text,
                Pattern.compile("(?:invoice\\s*date|date\\s*facture|date)[\\s:]+(\\d{1,2}[\\s./\\-]\\w+[\\s./\\-]\\d{2,4}|\\d{4}[\\s./\\-]\\d{1,2}[\\s./\\-]\\d{1,2})", Pattern.CASE_INSENSITIVE)));
        data.put("currency", extractField(text,
                Pattern.compile("(?:currency|devise|monnaie)[\\s:]+(\\w{3})", Pattern.CASE_INSENSITIVE)));
        data.put("totalAmount", extractField(text,
                Pattern.compile("(?:total\\s*(?:amount|ht|ttc)?|montant\\s*(?:total|ht|ttc)?|grand\\s*total)[\\s:]+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE)));
        data.put("incoterm", extractField(text,
                Pattern.compile("(?:incoterm|incoterms?|terms?\\s*of\\s*delivery)[\\s:]+(\\w{3,4})", Pattern.CASE_INSENSITIVE)));
        data.put("countryOfOrigin", extractField(text,
                Pattern.compile("(?:country\\s*of\\s*origin|pays\\s*d[\\s']*origine|origine)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("itemDescription", extractField(text,
                Pattern.compile("(?:description|d[eé]signation|marchandise)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("quantity", extractField(text,
                Pattern.compile("(?:quantity|quantit[eé]|qty)[\\s:]+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE)));
        data.put("unitPrice", extractField(text,
                Pattern.compile("(?:unit\\s*price|prix\\s*unitaire|price)[\\s:]+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE)));
        data.put("hsCode", extractField(text,
                Pattern.compile("(?:hs\\s*(?:code|number)?|code\\s*sh|tarif\\s*(?:number|code))[\\s:]+(\\d{4,10})", Pattern.CASE_INSENSITIVE)));
        data.put("netWeight", extractField(text,
                Pattern.compile("(?:net\\s*weight|poids\\s*net|nw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        data.put("grossWeight", extractField(text,
                Pattern.compile("(?:gross\\s*weight|poids\\s*brut|gw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        return data;
    }

    private Map<String, Object> parseBillOfLading(String text) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("blNumber", extractField(text,
                Pattern.compile("(?:b/?l\\s*(?:no|number|#|n[°o])|bill\\s*of\\s*lading\\s*(?:no|#))[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("shipper", extractField(text,
                Pattern.compile("(?:shipper|exp[eé]diteur|chargeur)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("consignee", extractField(text,
                Pattern.compile("(?:consignee|destinataire|connaiss)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("notifyParty", extractField(text,
                Pattern.compile("(?:notify\\s*party|partie\\s*à\\s*notifier)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("portOfLoading", extractField(text,
                Pattern.compile("(?:port\\s*of\\s*loading|port\\s*d[\\s']*embarquement|pol)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("portOfDischarge", extractField(text,
                Pattern.compile("(?:port\\s*of\\s*discharge|port\\s*de\\s*d[eé]barquement|pod)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("vesselName", extractField(text,
                Pattern.compile("(?:vessel|navire|vaisseau|ship)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("voyageNumber", extractField(text,
                Pattern.compile("(?:voyage\\s*(?:no|#|number))[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("containerNumber", extractField(text,
                Pattern.compile("(?:container\\s*(?:no|#|number)|conteneur)[\\s:]+([A-Z]{4}\\d{7})", Pattern.CASE_INSENSITIVE)));
        data.put("sealNumber", extractField(text,
                Pattern.compile("(?:seal\\s*(?:no|#|number)|sceau)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("numberOfPackages", extractField(text,
                Pattern.compile("(?:number\\s*of\\s*packages?|nb\\s*colis|packages?)[\\s:]+(\\d+)", Pattern.CASE_INSENSITIVE)));
        data.put("grossWeight", extractField(text,
                Pattern.compile("(?:gross\\s*weight|poids\\s*brut|gw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        data.put("volume", extractField(text,
                Pattern.compile("(?:volume|volumen|m[c³3])[\\s:]+([\\d\\s.,]+\\s*(?:m[c³3]|cbm|ft3))", Pattern.CASE_INSENSITIVE)));
        data.put("freightTerms", extractField(text,
                Pattern.compile("(?:freight\\s*(?:terms?|payment|collect|prepaid)|port)[\\s:]+(\\w+)", Pattern.CASE_INSENSITIVE)));
        data.put("dateOfIssue", extractField(text,
                Pattern.compile("(?:date\\s*of\\s*issue|date\\s*d[\\s']*[eé]mission|dated?)[\\s:]+(\\d{1,2}[\\s./\\-]\\w+[\\s./\\-]\\d{2,4}|\\d{4}[\\s./\\-]\\d{1,2}[\\s./\\-]\\d{1,2})", Pattern.CASE_INSENSITIVE)));
        return data;
    }

    private Map<String, Object> parseCertificateOfOrigin(String text) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("certificateNumber", extractField(text,
                Pattern.compile("(?:certificate\\s*(?:no|#|number)|certificat\\s*(?:no|n[°o])|#ref)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("exporter", extractField(text,
                Pattern.compile("(?:exporter|exportateur|shippers?)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("consignee", extractField(text,
                Pattern.compile("(?:consignee|destinataire|acheteur)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("countryOfOrigin", extractField(text,
                Pattern.compile("(?:country\\s*of\\s*origin|pays\\s*d[\\s']*origine|origin)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("countryOfDestination", extractField(text,
                Pattern.compile("(?:country\\s*of\\s*destination|pays\\s*de\\s*destination|destination)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("itemDescription", extractField(text,
                Pattern.compile("(?:description|d[eé]signation|marchandise|goods)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("hsCode", extractField(text,
                Pattern.compile("(?:hs\\s*(?:code|number)?|code\\s*sh|tarif\\s*(?:number|code))[\\s:]+(\\d{4,10})", Pattern.CASE_INSENSITIVE)));
        data.put("quantity", extractField(text,
                Pattern.compile("(?:quantity|quantit[eé]|qty)[\\s:]+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE)));
        data.put("grossWeight", extractField(text,
                Pattern.compile("(?:gross\\s*weight|poids\\s*brut|gw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        data.put("dateOfIssue", extractField(text,
                Pattern.compile("(?:date\\s*of\\s*issue|date\\s*d[\\s']*[eé]mission|issued?)[\\s:]+(\\d{1,2}[\\s./\\-]\\w+[\\s./\\-]\\d{2,4}|\\d{4}[\\s./\\-]\\d{1,2}[\\s./\\-]\\d{1,2})", Pattern.CASE_INSENSITIVE)));
        data.put("issuingAuthority", extractField(text,
                Pattern.compile("(?:issuing\\s*authority|autorit[eé]\\s*d[eé]livrance|chambre\\s*de\\s*commerce)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        return data;
    }

    private Map<String, Object> parsePackingList(String text) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("packingListNumber", extractField(text,
                Pattern.compile("(?:packing\\s*list\\s*(?:no|#|number)|liste\\s*de\\s*colisage)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("shipper", extractField(text,
                Pattern.compile("(?:shipper|exp[eé]diteur|fournisseur)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("consignee", extractField(text,
                Pattern.compile("(?:consignee|destinataire|client)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("numberOfPackages", extractField(text,
                Pattern.compile("(?:number\\s*of\\s*packages?|nb\\s*colis|total\\s*colis|packages?)[\\s:]+(\\d+)", Pattern.CASE_INSENSITIVE)));
        data.put("totalNetWeight", extractField(text,
                Pattern.compile("(?:total\\s*net\\s*weight|poids\\s*net\\s*total|tnw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        data.put("totalGrossWeight", extractField(text,
                Pattern.compile("(?:total\\s*gross\\s*weight|poids\\s*brut\\s*total|tgw)[\\s:]+([\\d\\s.,]+\\s*(?:kg|kgs|lbs|tonnes?))", Pattern.CASE_INSENSITIVE)));
        data.put("totalVolume", extractField(text,
                Pattern.compile("(?:total\\s*volume|volume\\s*total|tv)[\\s:]+([\\d\\s.,]+\\s*(?:m[c³3]|cbm))", Pattern.CASE_INSENSITIVE)));
        data.put("itemDescription", extractField(text,
                Pattern.compile("(?:description|d[eé]signation|marchandise)[\\s:]+([^\\n]+)", Pattern.CASE_INSENSITIVE)));
        data.put("quantity", extractField(text,
                Pattern.compile("(?:quantity|quantit[eé]|qty)[\\s:]+([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE)));
        data.put("dimensions", extractField(text,
                Pattern.compile("(?:dimensions?|dimensions?\\s*l\\s*x\\s*l\\s*x\\s*h)[\\s:]+([\\d\\s.,x]+\\s*(?:cm|m|in|ft)?)", Pattern.CASE_INSENSITIVE)));
        data.put("dateOfIssue", extractField(text,
                Pattern.compile("(?:date\\s*of\\s*issue|date|d[eé]but)[\\s:]+(\\d{1,2}[\\s./\\-]\\w+[\\s./\\-]\\d{2,4}|\\d{4}[\\s./\\-]\\d{1,2}[\\s./\\-]\\d{1,2})", Pattern.CASE_INSENSITIVE)));
        return data;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String normalizeText(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n").replaceAll("[ \t]+", " ").trim();
    }

    private String extractField(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            return value.length() > 500 ? value.substring(0, 500) : value;
        }
        return null;
    }

    private BigDecimal calculateConfidence(Map<String, Object> parsed, ParsedDocument.DocumentType docType) {
        int totalFields = switch (docType) {
            case COMMERCIAL_INVOICE -> 13;
            case BILL_OF_LADING -> 15;
            case CERTIFICATE_OF_ORIGIN -> 11;
            case PACKING_LIST -> 11;
        };
        long filledFields = parsed.values().stream().filter(Objects::nonNull).count();
        if (totalFields == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(filledFields)
                .divide(BigDecimal.valueOf(totalFields), 3, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
