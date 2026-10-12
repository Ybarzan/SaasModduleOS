package com.incokalk.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * generateQuotePdf/generateShippingLabelPdf/generateCmrPdf/generateDgdPdf/
 * generateCertificateOfOriginPdf ont ete retirees avec le domaine Transport
 * (ShipmentOrder, QuoteRequestDTO/QuoteResponseDTO, TrackingEvent, Carrier) --
 * leur seul appelant, DocumentExportController, a ete retire en meme temps
 * (tous ses endpoints etaient couples au domaine Transport). Seuls les exports
 * de declarations douanieres (DAU/DEB/ICS2/export), sans rapport avec le
 * domaine Transport, subsistent -- ils n'utilisaient ni le stockage de fichiers
 * (FileStorageService) ni le branding d'entreprise (BrandingService), retires
 * eux aussi avec les methodes qui s'en servaient.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExportService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(0, 51, 102));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0, 51, 102));
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);

    private void addHeader(Document document, String title, String subtitle) throws DocumentException {
        Paragraph titlePara = new Paragraph(title, TITLE_FONT);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titlePara.setSpacingAfter(2);
        document.add(titlePara);

        Paragraph subPara = new Paragraph(subtitle, new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY));
        subPara.setAlignment(Element.ALIGN_CENTER);
        subPara.setSpacingAfter(12);
        document.add(subPara);
    }

    private void addDateLine(Document document) throws DocumentException {
        Paragraph datePara = new Paragraph(
                "Généré le " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY));
        datePara.setAlignment(Element.ALIGN_RIGHT);
        datePara.setSpacingAfter(16);
        document.add(datePara);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph section = new Paragraph(title, HEADER_FONT);
        section.setSpacingBefore(12);
        section.setSpacingAfter(4);
        document.add(section);
    }

    private void addFooter(Document document, Object branding) throws DocumentException {
        Paragraph footer = new Paragraph("Généré par IncoKalk", FOOTER_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3);
        labelCell.setPaddingLeft(0);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    public byte[] generateDauPdf(com.incokalk.model.CustomsDeclaration d) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        addHeader(document, "DOCUMENT ADMINISTRATIF UNIQUE (DAU)", "Déclaration en douane");
        addDateLine(document);
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6); t.setSpacingAfter(12);
        addInfoRow(t, "Numéro", d.getDeclarationNumber() != null ? d.getDeclarationNumber() : "—");
        addInfoRow(t, "Type", d.getDeclarationType() != null ? d.getDeclarationType().name() : "—");
        addInfoRow(t, "Statut", d.getStatus() != null ? d.getStatus().name() : "—");
        addInfoRow(t, "Bureau", d.getCustomsOffice() != null ? d.getCustomsOffice() : "—");
        addInfoRow(t, "Régime", d.getCustomsRegime() != null ? d.getCustomsRegime() : "—");
        addInfoRow(t, "Code douanier", d.getCustomsCode() != null ? d.getCustomsCode() : "—");
        addInfoRow(t, "Origine", d.getOriginCountry() != null ? d.getOriginCountry() : "—");
        addInfoRow(t, "Destination", d.getDestinationCountry() != null ? d.getDestinationCountry() : "—");
        if (d.getDeclaredValue() != null) addInfoRow(t, "Valeur", String.format("%.2f %s", d.getDeclaredValue(), d.getCurrency() != null ? d.getCurrency() : "EUR"));
        addInfoRow(t, "Code SH", d.getHsCode() != null ? d.getHsCode() : "—");
        if (d.getNetWeight() != null) addInfoRow(t, "Poids net", String.format("%.2f kg", d.getNetWeight()));
        if (d.getGrossWeight() != null) addInfoRow(t, "Poids brut", String.format("%.2f kg", d.getGrossWeight()));
        if (d.getPackages() != null) addInfoRow(t, "Colis", String.valueOf(d.getPackages()));
        document.add(t);
        if (d.getGoodsDescription() != null && !d.getGoodsDescription().isBlank()) {
            addSectionTitle(document, "Marchandises");
            document.add(new Paragraph(d.getGoodsDescription(), NORMAL_FONT));
        }
        addFooter(document, null);
        document.close();
        return baos.toByteArray();
    }

    public byte[] generateDebPdf(com.incokalk.model.DebDeclaration d) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        addHeader(document, "DÉCLARATION D'ÉCHANGES DE BIENS (DEB)", "Intrastat / DEB");
        addDateLine(document);
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6); t.setSpacingAfter(12);
        addInfoRow(t, "Numéro", d.getDeclarationNumber() != null ? d.getDeclarationNumber() : "—");
        addInfoRow(t, "Type", d.getDeclarationType() != null ? d.getDeclarationType().name() : "—");
        addInfoRow(t, "Statut", d.getStatus() != null ? d.getStatus().name() : "—");
        addInfoRow(t, "Période", d.getPeriod() != null ? d.getPeriod() : "—");
        addInfoRow(t, "Pays partenaire", d.getPartnerCountry() != null ? d.getPartnerCountry() : "—");
        addInfoRow(t, "Nature", d.getNatureOfTransaction() != null ? d.getNatureOfTransaction() : "—");
        addInfoRow(t, "Transport", d.getModeOfTransport() != null ? d.getModeOfTransport() : "—");
        addInfoRow(t, "Code SH 8", d.getHsCode8() != null ? d.getHsCode8() : "—");
        if (d.getNetMass() != null) addInfoRow(t, "Masse nette", String.format("%.2f kg", d.getNetMass()));
        if (d.getStatisticalValue() != null) addInfoRow(t, "Valeur statistique", String.format("%.2f EUR", d.getStatisticalValue()));
        document.add(t);
        if (d.getGoodsDescription() != null && !d.getGoodsDescription().isBlank()) {
            addSectionTitle(document, "Marchandises");
            document.add(new Paragraph(d.getGoodsDescription(), NORMAL_FONT));
        }
        addFooter(document, null);
        document.close();
        return baos.toByteArray();
    }

    public byte[] generateIcs2Pdf(com.incokalk.model.Ics2Declaration d) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        addHeader(document, "ICS2 — PRÉ-DÉCLARATION DE SÉCURITÉ", "Import Control System 2");
        addDateLine(document);
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6); t.setSpacingAfter(12);
        addInfoRow(t, "Numéro", d.getDeclarationNumber() != null ? d.getDeclarationNumber() : "—");
        addInfoRow(t, "Statut", d.getStatus() != null ? d.getStatus().name() : "—");
        addInfoRow(t, "EORI expéditeur", d.getSenderEori() != null ? d.getSenderEori() : "—");
        addInfoRow(t, "EORI destinataire", d.getReceiverEori() != null ? d.getReceiverEori() : "—");
        addInfoRow(t, "Navire", d.getVesselName() != null ? d.getVesselName() : "—");
        addInfoRow(t, "Voyage", d.getVoyageNumber() != null ? d.getVoyageNumber() : "—");
        addInfoRow(t, "Conteneur", d.getContainerNumber() != null ? d.getContainerNumber() : "—");
        addInfoRow(t, "Code SH", d.getHsCode6() != null ? d.getHsCode6() : "—");
        if (d.getGrossWeight() != null) addInfoRow(t, "Poids brut", String.format("%.2f kg", d.getGrossWeight()));
        if (d.getPackagesCount() != null) addInfoRow(t, "Colis", String.valueOf(d.getPackagesCount()));
        document.add(t);
        if (d.getGoodsDescription() != null && !d.getGoodsDescription().isBlank()) {
            addSectionTitle(document, "Marchandises");
            document.add(new Paragraph(d.getGoodsDescription(), NORMAL_FONT));
        }
        addFooter(document, null);
        document.close();
        return baos.toByteArray();
    }

    public byte[] generateExportPdf(com.incokalk.model.ExportDeclaration d) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();
        addHeader(document, "DÉCLARATION D'EXPORTATION", d.getDeclarationType() != null ? d.getDeclarationType().name() : "AES/EXS");
        addDateLine(document);
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6); t.setSpacingAfter(12);
        addInfoRow(t, "Numéro", d.getDeclarationNumber() != null ? d.getDeclarationNumber() : "—");
        addInfoRow(t, "Type", d.getDeclarationType() != null ? d.getDeclarationType().name() : "—");
        addInfoRow(t, "Statut", d.getStatus() != null ? d.getStatus().name() : "—");
        addInfoRow(t, "EORI exportateur", d.getExporterEori() != null ? d.getExporterEori() : "—");
        addInfoRow(t, "Destination", d.getDestinationCountry() != null ? d.getDestinationCountry() : "—");
        addInfoRow(t, "Code SH", d.getHsCode() != null ? d.getHsCode() : "—");
        if (d.getDeclaredValue() != null) addInfoRow(t, "Valeur", String.format("%.2f %s", d.getDeclaredValue(), d.getCurrency() != null ? d.getCurrency() : "EUR"));
        if (d.getNetWeight() != null) addInfoRow(t, "Poids net", String.format("%.2f kg", d.getNetWeight()));
        if (d.getGrossWeight() != null) addInfoRow(t, "Poids brut", String.format("%.2f kg", d.getGrossWeight()));
        if (d.getPackagesCount() != null) addInfoRow(t, "Colis", String.valueOf(d.getPackagesCount()));
        document.add(t);
        if (d.getGoodsDescription() != null && !d.getGoodsDescription().isBlank()) {
            addSectionTitle(document, "Marchandises");
            document.add(new Paragraph(d.getGoodsDescription(), NORMAL_FONT));
        }
        addFooter(document, null);
        document.close();
        return baos.toByteArray();
    }

    public byte[] generateDauXml(com.incokalk.model.CustomsDeclaration d) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<DAUDeclaration xmlns=\"urn:incokalk:dau:1.0\">\n" +
            "  <DeclarationNumber>" + xmlEscape(d.getDeclarationNumber()) + "</DeclarationNumber>\n" +
            "  <DeclarationType>" + xmlEscape(d.getDeclarationType() != null ? d.getDeclarationType().name() : "") + "</DeclarationType>\n" +
            "  <Status>" + xmlEscape(d.getStatus() != null ? d.getStatus().name() : "") + "</Status>\n" +
            "  <CustomsOffice>" + xmlEscape(d.getCustomsOffice()) + "</CustomsOffice>\n" +
            "  <CustomsRegime>" + xmlEscape(d.getCustomsRegime()) + "</CustomsRegime>\n" +
            "  <CustomsCode>" + xmlEscape(d.getCustomsCode()) + "</CustomsCode>\n" +
            "  <OriginCountry>" + xmlEscape(d.getOriginCountry()) + "</OriginCountry>\n" +
            "  <DestinationCountry>" + xmlEscape(d.getDestinationCountry()) + "</DestinationCountry>\n" +
            "  <DeclaredValue>" + (d.getDeclaredValue() != null ? d.getDeclaredValue().toPlainString() : "") + "</DeclaredValue>\n" +
            "  <Currency>" + xmlEscape(d.getCurrency()) + "</Currency>\n" +
            "  <HSCode>" + xmlEscape(d.getHsCode()) + "</HSCode>\n" +
            "  <GoodsDescription>" + xmlEscape(d.getGoodsDescription()) + "</GoodsDescription>\n" +
            "  <NetWeight>" + (d.getNetWeight() != null ? d.getNetWeight().toPlainString() : "") + "</NetWeight>\n" +
            "  <GrossWeight>" + (d.getGrossWeight() != null ? d.getGrossWeight().toPlainString() : "") + "</GrossWeight>\n" +
            "  <Packages>" + (d.getPackages() != null ? d.getPackages() : "") + "</Packages>\n" +
            "</DAUDeclaration>\n";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String xmlEscape(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
