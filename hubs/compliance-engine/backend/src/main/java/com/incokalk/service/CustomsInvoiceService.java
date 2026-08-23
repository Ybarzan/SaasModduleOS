package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.EoriNumber;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EoriNumberRepository;
import com.incokalk.tenant.TenantContext;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomsInvoiceService {

    private final CompanyRepository companyRepo;
    private final EoriNumberRepository eoriRepo;

    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
    private static final Font SUBHEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font TABLE_BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font EORI_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(0, 80, 160));

    public record CustomsInvoiceData(
        UUID companyId,
        UUID shipmentId,
        String sellerName, String sellerAddress, String sellerCountry,
        String buyerName, String buyerAddress, String buyerCountry,
        String consigneeName, String consigneeAddress, String consigneeCountry,
        String goodsDescription,
        String hsCode,
        String originCountry,
        double quantity,
        String quantityUnit,
        double unitPrice,
        double totalPrice,
        String currency,
        double grossWeight,
        double netWeight,
        String incoterm,
        String transportMode,
        String countryOfOrigin,
        String invoiceNumber,
        String eoriNumber,
        String loadingPort,
        String unloadingPort,
        String finalDestinationCountry,
        String customsRegime,
        String transactionNature
    ) {}

    public byte[] generatePdf(CustomsInvoiceData data, Map<String, Object> dutyResult) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);

        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            addHeader(doc, data);
            doc.add(Chunk.NEWLINE);

            addParties(doc, data);
            doc.add(Chunk.NEWLINE);

            addGoodsTable(doc, data);
            doc.add(Chunk.NEWLINE);

            addDutyInfo(doc, data, dutyResult);
            doc.add(Chunk.NEWLINE);

            addDeclaration(doc, data);

            doc.close();
        } catch (Exception e) {
            log.error("Erreur generation facture douaniere", e);
            throw new RuntimeException("Impossible de generer la facture douaniere", e);
        }

        return baos.toByteArray();
    }

    public byte[] generateFromShipment(ShipmentOrder shipment, UUID companyId) {
        String eoriNumber = null;
        Optional<EoriNumber> eori = eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId);
        if (eori.isPresent()) {
            eoriNumber = eori.get().getEori();
        }

        String currency = shipment.getCostCurrency() != null ? shipment.getCostCurrency() : "EUR";

        CustomsInvoiceData data = new CustomsInvoiceData(
            companyId,
            shipment.getId(),
            shipment.getShipperName(),
            shipment.getShipperAddress(),
            shipment.getShipperCountry(),
            shipment.getConsigneeName(),
            shipment.getConsigneeAddress(),
            shipment.getConsigneeCountry(),
            null, null, null,
            shipment.getGoodsDescription(),
            shipment.getHsCode(),
            shipment.getCountryOfOrigin(),
            1,
            "PCS",
            shipment.getGoodsValue() != null ? shipment.getGoodsValue() : 0.0,
            shipment.getGoodsValue() != null ? shipment.getGoodsValue() : 0.0,
            currency,
            shipment.getWeightKg() != null ? shipment.getWeightKg() : 0.0,
            shipment.getWeightKg() != null ? shipment.getWeightKg() : 0.0,
            shipment.getIncotermCode(),
            null,
            shipment.getCountryOfOrigin(),
            shipment.getOrderNumber(),
            eoriNumber,
            null,
            null,
            shipment.getConsigneeCountry(),
            "4000",
            "A"
        );

        Map<String, Object> dutyResult = Map.of(
            "appliedRate", shipment.getDutyAmount() != null ? shipment.getDutyAmount() : 0.0,
            "isPrefential", false,
            "agreement", "N/A"
        );

        return generatePdf(data, dutyResult);
    }

    private void addHeader(Document doc, CustomsInvoiceData data) throws DocumentException {
        Paragraph title = new Paragraph("FACTURE DOUANIERE / CUSTOMS INVOICE", HEADER_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{50, 50});

        String invoiceNum = data.invoiceNumber() != null ? data.invoiceNumber() : "INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        addCell(headerTable, "Facture N / Invoice No:", SUBHEADER_FONT);
        addCell(headerTable, invoiceNum, BODY_FONT);
        addCell(headerTable, "Date:", SUBHEADER_FONT);
        addCell(headerTable, LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), BODY_FONT);
        addCell(headerTable, "Incoterm:", SUBHEADER_FONT);
        addCell(headerTable, data.incoterm() != null ? data.incoterm() : "FOB", BODY_FONT);
        addCell(headerTable, "Mode de transport:", SUBHEADER_FONT);
        addCell(headerTable, data.transportMode() != null ? data.transportMode() : "Maritime", BODY_FONT);
        addCell(headerTable, "Monnaie de facturation:", SUBHEADER_FONT);
        addCell(headerTable, data.currency() != null ? data.currency() : "EUR", BODY_FONT);
        addCell(headerTable, "Regime douanier:", SUBHEADER_FONT);
        addCell(headerTable, data.customsRegime() != null ? data.customsRegime() : "4000 (Importation definitive)", BODY_FONT);
        addCell(headerTable, "Nature de la transaction:", SUBHEADER_FONT);
        addCell(headerTable, data.transactionNature() != null ? data.transactionNature() : "A (Commercial)", BODY_FONT);

        doc.add(headerTable);

        if (data.eoriNumber() != null && !data.eoriNumber().isBlank()) {
            doc.add(Chunk.NEWLINE);
            Paragraph eoriPara = new Paragraph("N EORI de l'exportateur: " + data.eoriNumber(), EORI_FONT);
            eoriPara.setAlignment(Element.ALIGN_LEFT);
            doc.add(eoriPara);
        }
    }

    private void addParties(Document doc, CustomsInvoiceData data) throws DocumentException {
        PdfPTable partiesTable = new PdfPTable(2);
        partiesTable.setWidthPercentage(100);
        partiesTable.setWidths(new float[]{50, 50});

        PdfPCell sellerHeader = new PdfPCell(new Phrase("EXPEDITEUR / VENDEUR (Shipper)", TABLE_HEADER_FONT));
        sellerHeader.setBackgroundColor(Color.DARK_GRAY);
        sellerHeader.setPadding(5);
        partiesTable.addCell(sellerHeader);

        PdfPCell buyerHeader = new PdfPCell(new Phrase("ACHETEUR / DESTINATAIRE (Consignee)", TABLE_HEADER_FONT));
        buyerHeader.setBackgroundColor(Color.DARK_GRAY);
        buyerHeader.setPadding(5);
        partiesTable.addCell(buyerHeader);

        addCell(partiesTable, data.sellerName() != null ? data.sellerName() : "", BODY_FONT);
        addCell(partiesTable, data.buyerName() != null ? data.buyerName() : "", BODY_FONT);
        addCell(partiesTable, data.sellerAddress() != null ? data.sellerAddress() : "", BODY_FONT);
        addCell(partiesTable, data.buyerAddress() != null ? data.buyerAddress() : "", BODY_FONT);
        addCell(partiesTable, data.sellerCountry() != null ? data.sellerCountry() : "", BODY_FONT);
        addCell(partiesTable, data.buyerCountry() != null ? data.buyerCountry() : "", BODY_FONT);

        if (data.consigneeName() != null && !data.consigneeName().isBlank()) {
            PdfPCell consigneeHeader = new PdfPCell(new Phrase("CONSIGNATAIRE", TABLE_HEADER_FONT));
            consigneeHeader.setBackgroundColor(Color.DARK_GRAY);
            consigneeHeader.setPadding(5);
            partiesTable.addCell(consigneeHeader);

            PdfPCell empty = new PdfPCell(new Phrase("", BODY_FONT));
            partiesTable.addCell(empty);

            addCell(partiesTable, data.consigneeName(), BODY_FONT);
            addCell(partiesTable, "", BODY_FONT);
            if (data.consigneeAddress() != null) {
                addCell(partiesTable, data.consigneeAddress(), BODY_FONT);
                addCell(partiesTable, "", BODY_FONT);
            }
            if (data.consigneeCountry() != null) {
                addCell(partiesTable, data.consigneeCountry(), BODY_FONT);
                addCell(partiesTable, "", BODY_FONT);
            }
        }

        doc.add(partiesTable);
    }

    private void addGoodsTable(Document doc, CustomsInvoiceData data) throws DocumentException {
        Paragraph goodsTitle = new Paragraph("DESCRIPTION DES MARCHANDISES / GOODS DESCRIPTION", SUBHEADER_FONT);
        doc.add(goodsTitle);

        PdfPTable goodsTable = new PdfPTable(6);
        goodsTable.setWidthPercentage(100);
        goodsTable.setWidths(new float[]{30, 15, 15, 15, 15, 10});

        String[] headers = {"Description", "Code SH", "Quantite", "Prix Unitaire", "Poids Brut (kg)", "Poids Net (kg)"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setPadding(5);
            goodsTable.addCell(cell);
        }

        addCell(goodsTable, data.goodsDescription() != null ? data.goodsDescription() : "", TABLE_BODY_FONT);
        addCell(goodsTable, data.hsCode() != null ? data.hsCode() : "", TABLE_BODY_FONT);
        addCell(goodsTable, data.quantity() + " " + (data.quantityUnit() != null ? data.quantityUnit() : "PCS"), TABLE_BODY_FONT);
        addCell(goodsTable, String.format("%.2f %s", data.unitPrice(), data.currency() != null ? data.currency() : "EUR"), TABLE_BODY_FONT);
        addCell(goodsTable, String.format("%.2f", data.grossWeight()), TABLE_BODY_FONT);
        addCell(goodsTable, String.format("%.2f", data.netWeight()), TABLE_BODY_FONT);

        PdfPCell totalLabel = new PdfPCell(new Phrase("Valeur CIF / CIF Value:", TABLE_HEADER_FONT));
        totalLabel.setBackgroundColor(Color.DARK_GRAY);
        totalLabel.setPadding(5);
        goodsTable.addCell(totalLabel);

        PdfPCell empty1 = new PdfPCell(new Phrase("", TABLE_BODY_FONT));
        goodsTable.addCell(empty1);
        PdfPCell empty2 = new PdfPCell(new Phrase("", TABLE_BODY_FONT));
        goodsTable.addCell(empty2);
        PdfPCell empty3 = new PdfPCell(new Phrase("", TABLE_BODY_FONT));
        goodsTable.addCell(empty3);

        PdfPCell totalValue = new PdfPCell(new Phrase(String.format("%.2f %s", data.totalPrice(), data.currency() != null ? data.currency() : "EUR"), SUBHEADER_FONT));
        totalValue.setPadding(5);
        goodsTable.addCell(totalValue);

        PdfPCell empty4 = new PdfPCell(new Phrase("", TABLE_BODY_FONT));
        goodsTable.addCell(empty4);

        doc.add(goodsTable);

        if (data.loadingPort() != null || data.unloadingPort() != null || data.finalDestinationCountry() != null) {
            doc.add(Chunk.NEWLINE);
            PdfPTable transportTable = new PdfPTable(2);
            transportTable.setWidthPercentage(100);
            transportTable.setWidths(new float[]{50, 50});

            if (data.loadingPort() != null) {
                addCell(transportTable, "Port de chargement / Loading port:", SUBHEADER_FONT);
                addCell(transportTable, data.loadingPort(), BODY_FONT);
            }
            if (data.unloadingPort() != null) {
                addCell(transportTable, "Port de dechargement / Unloading port:", SUBHEADER_FONT);
                addCell(transportTable, data.unloadingPort(), BODY_FONT);
            }
            if (data.finalDestinationCountry() != null) {
                addCell(transportTable, "Pays de destination finale / Final destination:", SUBHEADER_FONT);
                addCell(transportTable, data.finalDestinationCountry(), BODY_FONT);
            }
            doc.add(transportTable);
        }
    }

    private void addDutyInfo(Document doc, CustomsInvoiceData data, Map<String, Object> dutyResult) throws DocumentException {
        Paragraph dutyTitle = new Paragraph("INFORMATIONS DOUANIERES / CUSTOMS INFORMATION", SUBHEADER_FONT);
        doc.add(dutyTitle);

        PdfPTable dutyTable = new PdfPTable(2);
        dutyTable.setWidthPercentage(100);
        dutyTable.setWidths(new float[]{50, 50});

        addCell(dutyTable, "Pays d'origine / Country of origin:", SUBHEADER_FONT);
        addCell(dutyTable, data.originCountry() != null ? data.originCountry() : "", BODY_FONT);

        addCell(dutyTable, "Pays de destination / Destination country:", SUBHEADER_FONT);
        addCell(dutyTable, data.buyerCountry() != null ? data.buyerCountry() : "", BODY_FONT);

        if (dutyResult != null) {
            addCell(dutyTable, "Taux de droit applique / Applied duty rate:", SUBHEADER_FONT);
            Object rate = dutyResult.get("appliedRate");
            addCell(dutyTable, rate != null ? String.format("%.2f%%", ((Number) rate).doubleValue()) : "N/A", BODY_FONT);

            addCell(dutyTable, "Type de droit / Duty type:", SUBHEADER_FONT);
            Object isPref = dutyResult.get("isPrefential");
            addCell(dutyTable, Boolean.TRUE.equals(isPref) ? "Preferentiel (APE)" : "MFN standard", BODY_FONT);

            if (Boolean.TRUE.equals(isPref)) {
                addCell(dutyTable, "Accord applicable / Agreement:", SUBHEADER_FONT);
                addCell(dutyTable, String.valueOf(dutyResult.getOrDefault("agreement", "")), BODY_FONT);
            }

            Object savings = dutyResult.get("savings");
            if (savings != null && ((Number) savings).doubleValue() > 0) {
                addCell(dutyTable, "Economie vs MFN / Savings vs MFN:", SUBHEADER_FONT);
                addCell(dutyTable, String.format("%.2f EUR", ((Number) savings).doubleValue()), BODY_FONT);
            }
        }

        doc.add(dutyTable);
    }

    private void addDeclaration(Document doc, CustomsInvoiceData data) throws DocumentException {
        doc.add(Chunk.NEWLINE);

        Paragraph decl = new Paragraph("DECLARATION / CERTIFICATION", SUBHEADER_FONT);
        doc.add(decl);

        String declarationText =
            "Je soussigne(e), certifie que les renseignements donnes dans la presente declaration sont exacts " +
            "et que cette marchandise est d'origine " + (data.originCountry() != null ? data.originCountry() : "________") + " " +
            "au sens des dispositions en vigueur.\n\n" +
            "I, the undersigned, certify that the information given in this declaration is true and correct " +
            "and that the goods described herein originate in " + (data.originCountry() != null ? data.originCountry() : "________") + " " +
            "within the meaning of the relevant provisions in force.";

        Paragraph declBody = new Paragraph(declarationText, BODY_FONT);
        doc.add(declBody);

        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);

        PdfPTable signTable = new PdfPTable(3);
        signTable.setWidthPercentage(100);

        addCell(signTable, "Lieu et date / Place and date:", BODY_FONT);
        addCell(signTable, "Signature:", BODY_FONT);
        addCell(signTable, "Cachet de l'entreprise / Company stamp:", BODY_FONT);

        addCell(signTable, "_______________________", BODY_FONT);
        addCell(signTable, "_______________________", BODY_FONT);
        addCell(signTable, "_______________________", BODY_FONT);

        doc.add(signTable);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }
}
