package com.incokalk.service;

import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyBranding;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TrackingEvent;
import com.incokalk.tenant.TenantContext;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExportService {

    private final FileStorageService fileStorage;
    private final BrandingService brandingService;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(0, 51, 102));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0, 51, 102));
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 7, Font.BOLD, Color.DARK_GRAY);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font HIGHLIGHT_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0, 120, 0));

    private static final Color TABLE_HEADER_BG = new Color(0, 51, 102);
    private static final Color TABLE_ALT_BG = new Color(240, 245, 250);
    private static final Color HIGHLIGHT_BG = new Color(230, 255, 230);
    private static final Color SECTION_BORDER = new Color(200, 200, 200);

    public byte[] generateQuotePdf(List<QuoteResponseDTO> quotes, QuoteRequestDTO request) {
        CompanyBranding branding = resolveBranding(TenantContext.get());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        addHeader(document, "Devis de transport", resolveCompanyName(branding, null) + " — Comparateur de tarifs");

        addDateLine(document);

        addSectionTitle(document, "Détails de la demande");

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingBefore(6);
        infoTable.setSpacingAfter(12);

        addInfoRow(infoTable, "Origine", request.getOriginCountry());
        addInfoRow(infoTable, "Destination", request.getDestinationCountry());
        String mode = request.getTransportMode() != null && !request.getTransportMode().isEmpty()
                ? switch (request.getTransportMode()) {
                    case "SEA" -> "Maritime";
                    case "AIR" -> "Aérien";
                    case "ROAD" -> "Routier";
                    default -> request.getTransportMode();
                  }
                : "Tous";
        addInfoRow(infoTable, "Mode de transport", mode);
        addInfoRow(infoTable, "Poids", request.getWeightKg() + " kg");
        addInfoRow(infoTable, "Volume", request.getVolumeM3() + " m³");
        if (request.getGoodsValue() != null && request.getGoodsValue() > 0) {
            addInfoRow(infoTable, "Valeur marchandises",
                    String.format("%.2f %s", request.getGoodsValue(),
                            request.getCurrency() != null ? request.getCurrency() : "EUR"));
        }
        if (request.getHsCode() != null && !request.getHsCode().isEmpty()) {
            addInfoRow(infoTable, "Code SH", request.getHsCode());
        }
        document.add(infoTable);

        addSectionTitle(document, "Tarifs proposés");

        if (quotes.isEmpty()) {
            Paragraph none = new Paragraph("Aucun tarif trouvé pour cette recherche.", NORMAL_FONT);
            none.setSpacingAfter(12);
            document.add(none);
        } else {
            QuoteResponseDTO cheapest = quotes.stream()
                    .min(Comparator.comparingDouble(QuoteResponseDTO::getTotalCost))
                    .orElse(null);
            QuoteResponseDTO fastest = quotes.stream()
                    .filter(q -> q.getTransitDaysMin() != null)
                    .min(Comparator.comparingInt(QuoteResponseDTO::getTransitDaysMin))
                    .orElse(null);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            table.setSpacingAfter(12);
            table.setWidths(new float[]{20, 12, 13, 18, 15, 12});

            Color headerColor = resolveAccentColor(branding);
            String[] headers = {"Transporteur", "Mode", "Tarif de base", "Coût total", "Délai (j)", "CO₂ (kg)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(headerColor);
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (int i = 0; i < quotes.size(); i++) {
                QuoteResponseDTO q = quotes.get(i);
                boolean isCheapest = q.equals(cheapest);
                boolean isFastest = q.equals(fastest);

                PdfPCell cell;
                Font rowFont = isCheapest || isFastest ? HIGHLIGHT_FONT : NORMAL_FONT;
                Color bg = isCheapest || isFastest ? HIGHLIGHT_BG : (i % 2 == 0 ? TABLE_ALT_BG : Color.WHITE);

                StringBuilder carrierLabel = new StringBuilder(q.getCarrierName());
                if (isCheapest && isFastest) {
                    carrierLabel.append(" ★");
                } else if (isCheapest) {
                    carrierLabel.append(" (moins cher)");
                } else if (isFastest) {
                    carrierLabel.append(" (plus rapide)");
                }

                cell = new PdfPCell(new Phrase(carrierLabel.toString(), rowFont));
                cell.setBackgroundColor(bg);
                cell.setPadding(5);
                table.addCell(cell);

                String modeLabel = switch (q.getTransportMode()) {
                    case "SEA" -> "Maritime";
                    case "AIR" -> "Aérien";
                    case "ROAD" -> "Routier";
                    default -> q.getTransportMode();
                };
                cell = new PdfPCell(new Phrase(modeLabel, NORMAL_FONT));
                cell.setBackgroundColor(bg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase(String.format("%.2f %s", q.getBaseRate(), q.getCurrency()), NORMAL_FONT));
                cell.setBackgroundColor(bg);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase(String.format("%.2f %s", q.getTotalCost(), q.getCurrency()), rowFont));
                cell.setBackgroundColor(bg);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cell.setPadding(5);
                table.addCell(cell);

                String transit = q.getTransitDaysMin() != null && q.getTransitDaysMax() != null
                        ? q.getTransitDaysMin() + "-" + q.getTransitDaysMax()
                        : "—";
                cell = new PdfPCell(new Phrase(transit, NORMAL_FONT));
                cell.setBackgroundColor(bg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);

                cell = new PdfPCell(new Phrase(q.getCo2EstimateKg() != null
                        ? String.format("%.1f", q.getCo2EstimateKg()) : "—", NORMAL_FONT));
                cell.setBackgroundColor(bg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }
            document.add(table);

            if (cheapest != null) {
                Paragraph cheapestPara = new Paragraph();
                cheapestPara.add(new Chunk("✓ Meilleur tarif : ", HIGHLIGHT_FONT));
                cheapestPara.add(new Chunk(String.format("%s — %.2f %s",
                        cheapest.getCarrierName(), cheapest.getTotalCost(), cheapest.getCurrency()), NORMAL_FONT));
                cheapestPara.setSpacingAfter(4);
                document.add(cheapestPara);
            }
            if (fastest != null && !fastest.equals(cheapest)) {
                Paragraph fastestPara = new Paragraph();
                fastestPara.add(new Chunk("✓ Livraison la plus rapide : ", HIGHLIGHT_FONT));
                fastestPara.add(new Chunk(String.format("%s — %d-%d jours",
                        fastest.getCarrierName(), fastest.getTransitDaysMin(), fastest.getTransitDaysMax()), NORMAL_FONT));
                fastestPara.setSpacingAfter(4);
                document.add(fastestPara);
            }

            Paragraph sourcePara = new Paragraph("Sources des tarifs", HEADER_FONT);
            sourcePara.setSpacingBefore(8);
            sourcePara.setSpacingAfter(4);
            document.add(sourcePara);

            for (QuoteResponseDTO q : quotes) {
                String providerInfo = q.getProviderName() != null ? q.getProviderName()
                        : (q.getProviderType() != null ? q.getProviderType() : "Interne");
                Paragraph p = new Paragraph(String.format("• %s : %s", q.getCarrierName(), providerInfo), SMALL_FONT);
                p.setSpacingAfter(2);
                document.add(p);
            }
        }

        addFooter(document, branding);
        document.close();
        byte[] pdfBytes = baos.toByteArray();
        storePdf("quotes", "quote-" + UUID.randomUUID(), pdfBytes);
        return pdfBytes;
    }

    @Transactional(readOnly = true)
    public byte[] generateShippingLabelPdf(ShipmentOrder shipment) {
        CompanyBranding branding = resolveBranding(companyIdOf(shipment));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        Paragraph title = new Paragraph("ÉTIQUETTE D'EXPÉDITION / SHIPPING LABEL", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        document.add(title);

        Paragraph sub = new Paragraph(
                "N° " + shipment.getOrderNumber() + "  |  "
                        + shipment.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(16);
        document.add(sub);

        Paragraph statusLine = new Paragraph(
                "Statut : " + translateStatus(shipment.getStatus().name()), BOLD_FONT);
        statusLine.setAlignment(Element.ALIGN_CENTER);
        statusLine.setSpacingAfter(16);
        document.add(statusLine);

        PdfPTable addressTable = new PdfPTable(2);
        addressTable.setWidthPercentage(100);
        addressTable.setSpacingAfter(16);
        addressTable.setWidths(new float[]{50, 50});

        PdfPCell shipperCell = new PdfPCell();
        shipperCell.setBorder(Rectangle.BOX);
        shipperCell.setBorderColor(SECTION_BORDER);
        shipperCell.setPadding(10);
        shipperCell.addElement(new Paragraph("EXPÉDITEUR / SHIPPER", LABEL_FONT));
        shipperCell.addElement(new Paragraph(shipment.getShipperName() != null ? shipment.getShipperName() : "—", BOLD_FONT));
        shipperCell.addElement(new Paragraph(shipment.getShipperAddress() != null ? shipment.getShipperAddress() : "", NORMAL_FONT));
        String shipperLine = (shipment.getShipperPostalCode() != null ? shipment.getShipperPostalCode() + " " : "")
                + (shipment.getShipperCity() != null ? shipment.getShipperCity() : "");
        shipperCell.addElement(new Paragraph(shipperLine, NORMAL_FONT));
        shipperCell.addElement(new Paragraph(shipment.getShipperCountry() != null ? shipment.getShipperCountry() : "", NORMAL_FONT));
        addressTable.addCell(shipperCell);

        PdfPCell consigneeCell = new PdfPCell();
        consigneeCell.setBorder(Rectangle.BOX);
        consigneeCell.setBorderColor(SECTION_BORDER);
        consigneeCell.setPadding(10);
        consigneeCell.addElement(new Paragraph("DESTINATAIRE / CONSIGNEE", LABEL_FONT));
        consigneeCell.addElement(new Paragraph(shipment.getConsigneeName() != null ? shipment.getConsigneeName() : "—", BOLD_FONT));
        consigneeCell.addElement(new Paragraph(shipment.getConsigneeAddress() != null ? shipment.getConsigneeAddress() : "", NORMAL_FONT));
        String consigneeLine = (shipment.getConsigneePostalCode() != null ? shipment.getConsigneePostalCode() + " " : "")
                + (shipment.getConsigneeCity() != null ? shipment.getConsigneeCity() : "");
        consigneeCell.addElement(new Paragraph(consigneeLine, NORMAL_FONT));
        consigneeCell.addElement(new Paragraph(shipment.getConsigneeCountry() != null ? shipment.getConsigneeCountry() : "", NORMAL_FONT));
        addressTable.addCell(consigneeCell);

        document.add(addressTable);

        PdfPTable cargoTable = new PdfPTable(4);
        cargoTable.setWidthPercentage(100);
        cargoTable.setSpacingAfter(16);
        cargoTable.setWidths(new float[]{25, 25, 25, 25});

        addLabelCell(cargoTable, "Marchandises", shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "—");
        addLabelCell(cargoTable, "Poids", shipment.getWeightKg() != null ? shipment.getWeightKg() + " kg" : "—");
        addLabelCell(cargoTable, "Volume", shipment.getVolumeM3() != null ? shipment.getVolumeM3() + " m³" : "—");
        addLabelCell(cargoTable, "Colis", shipment.getPackagesCount() != null ? String.valueOf(shipment.getPackagesCount()) : "—");

        document.add(cargoTable);

        PdfPTable cargoTable2 = new PdfPTable(4);
        cargoTable2.setWidthPercentage(100);
        cargoTable2.setSpacingAfter(16);
        cargoTable2.setWidths(new float[]{25, 25, 25, 25});

        addLabelCell(cargoTable2, "Code SH", shipment.getHsCode() != null ? shipment.getHsCode() : "—");
        addLabelCell(cargoTable2, "Incoterm", shipment.getIncotermCode() != null ? shipment.getIncotermCode() : "—");
        addLabelCell(cargoTable2, "Dangereux", shipment.isDangerous() ? "OUI" : "Non");
        String costStr = shipment.getFinalCost() != null
                ? String.format("%.2f %s", shipment.getFinalCost(), shipment.getCostCurrency())
                : (shipment.getQuotedCost() != null
                   ? String.format("%.2f %s", shipment.getQuotedCost(), shipment.getCostCurrency())
                   : "—");
        addLabelCell(cargoTable2, "Coût", costStr);

        document.add(cargoTable2);

        if (shipment.getCarrier() != null) {
            PdfPTable carrierTable = new PdfPTable(2);
            carrierTable.setWidthPercentage(100);
            carrierTable.setSpacingAfter(16);
            carrierTable.setWidths(new float[]{30, 70});

            addInfoRow(carrierTable, "Transporteur", shipment.getCarrier().getName());
            if (shipment.getCarrier().getContactPhone() != null) {
                addInfoRow(carrierTable, "Téléphone", shipment.getCarrier().getContactPhone());
            }
            document.add(carrierTable);
        }

        Paragraph barcodeTitle = new Paragraph("N° de commande / Order Number", LABEL_FONT);
        barcodeTitle.setAlignment(Element.ALIGN_CENTER);
        barcodeTitle.setSpacingAfter(4);
        document.add(barcodeTitle);

        Image barcodeImage = buildBarcodeImage(writer, shipment.getOrderNumber());
        barcodeImage.setAlignment(Element.ALIGN_CENTER);
        barcodeImage.setSpacingAfter(16);
        document.add(barcodeImage);

        if (shipment.getTrackingEvents() != null && !shipment.getTrackingEvents().isEmpty()) {
            addSectionTitle(document, "Suivi / Tracking");

            List<TrackingEvent> sorted = shipment.getTrackingEvents().stream()
                    .sorted(Comparator.comparing(TrackingEvent::getEventTime))
                    .toList();

            PdfPTable timelineTable = new PdfPTable(3);
            timelineTable.setWidthPercentage(100);
            timelineTable.setSpacingAfter(12);
            timelineTable.setWidths(new float[]{25, 20, 55});

            for (String h : new String[]{"Date", "Statut", "Description"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
                c.setBackgroundColor(TABLE_HEADER_BG);
                c.setPadding(4);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                timelineTable.addCell(c);
            }

            for (TrackingEvent event : sorted) {
                PdfPCell c;

                c = new PdfPCell(new Phrase(
                        event.getEventTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), SMALL_FONT));
                c.setPadding(4);
                timelineTable.addCell(c);

                c = new PdfPCell(new Phrase(translateStatus(event.getStatus()), SMALL_FONT));
                c.setPadding(4);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                timelineTable.addCell(c);

                StringBuilder desc = new StringBuilder();
                if (event.getDescription() != null) desc.append(event.getDescription());
                if (event.getLocation() != null) {
                    if (!desc.isEmpty()) desc.append(" — ");
                    desc.append(event.getLocation());
                }
                c = new PdfPCell(new Phrase(desc.isEmpty() ? "—" : desc.toString(), SMALL_FONT));
                c.setPadding(4);
                timelineTable.addCell(c);
            }
            document.add(timelineTable);
        }

        addFooter(document, branding);
        document.close();
        byte[] pdfBytes = baos.toByteArray();
        storePdf("shipping-labels", shipment.getOrderNumber(), pdfBytes);
        return pdfBytes;
    }

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

    private void addFooter(Document document, CompanyBranding branding) throws DocumentException {
        Paragraph footer = new Paragraph(resolveFooterText(branding), FOOTER_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private UUID companyIdOf(ShipmentOrder shipment) {
        Company company = shipment.getCompany();
        return company != null ? company.getId() : null;
    }

    private CompanyBranding resolveBranding(UUID companyId) {
        if (companyId == null) return null;
        try {
            return brandingService.getBranding(companyId);
        } catch (Exception e) {
            log.warn("[PDF] Impossible de resoudre le branding pour {}: {}", companyId, e.getMessage());
            return null;
        }
    }

    private Color resolveAccentColor(CompanyBranding branding) {
        if (branding != null && branding.getPrimaryColor() != null) {
            try {
                return Color.decode(branding.getPrimaryColor());
            } catch (NumberFormatException e) {
                log.debug("[PDF] Couleur de branding invalide '{}', repli sur la couleur par défaut", branding.getPrimaryColor());
            }
        }
        return TABLE_HEADER_BG;
    }

    private String resolveCompanyName(CompanyBranding branding, String fallback) {
        if (branding != null && branding.getPortalTitle() != null && !branding.getPortalTitle().isBlank()) {
            return branding.getPortalTitle();
        }
        return fallback != null && !fallback.isBlank() ? fallback : "IncoKalk";
    }

    private String resolveFooterText(CompanyBranding branding) {
        if (branding != null && branding.getFooterText() != null && !branding.getFooterText().isBlank()) {
            return branding.getFooterText();
        }
        return "Généré par IncoKalk";
    }

    private Image buildBarcodeImage(PdfWriter writer, String code) throws DocumentException {
        Barcode128 barcode128 = new Barcode128();
        barcode128.setCode(code != null ? code : "");
        barcode128.setBarHeight(30f);
        barcode128.setSize(8f);
        barcode128.setTextAlignment(Element.ALIGN_CENTER);
        return barcode128.createImageWithBarcode(writer.getDirectContent(), null, null);
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

    private void addLabelCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(SECTION_BORDER);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, LABEL_FONT));
        cell.addElement(new Paragraph(value, BOLD_FONT));
        table.addCell(cell);
    }

    @Transactional(readOnly = true)
    public byte[] generateCmrPdf(ShipmentOrder shipment) {
        CompanyBranding branding = resolveBranding(companyIdOf(shipment));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font cmrSectionFont = new Font(Font.HELVETICA, 7, Font.BOLD, Color.DARK_GRAY);
        Font cmrValueFont = new Font(Font.HELVETICA, 9, Font.NORMAL);

        Paragraph title = new Paragraph(
                "CONVENTION RELATIF AU CONTRAT DE TRANSPORT INTERNATIONAL\nDE MARCHANDISES PAR ROUTE (CMR)",
                new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0, 51, 102)));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        document.add(title);

        Paragraph orderLine = new Paragraph(
                "N° " + shipment.getOrderNumber() + "  |  Date: "
                        + shipment.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY));
        orderLine.setAlignment(Element.ALIGN_CENTER);
        orderLine.setSpacingAfter(12);
        document.add(orderLine);

        PdfPTable cmrTable = new PdfPTable(2);
        cmrTable.setWidthPercentage(100);
        cmrTable.setWidths(new float[]{50, 50});
        cmrTable.setSpacingBefore(4);
        cmrTable.setSpacingAfter(4);

        addCmrSection(cmrTable, "1. Expéditeur (Shipper)",
                formatAddress(shipment.getShipperName(), shipment.getShipperAddress(),
                        shipment.getShipperCity(), shipment.getShipperPostalCode(), shipment.getShipperCountry()));

        addCmrSection(cmrTable, "2. Destinataire (Consignee)",
                formatAddress(shipment.getConsigneeName(), shipment.getConsigneeAddress(),
                        shipment.getConsigneeCity(), shipment.getConsigneePostalCode(), shipment.getConsigneeCountry()));

        addCmrSection(cmrTable, "3. Lieu et pays de remise",
                (shipment.getConsigneeCity() != null ? shipment.getConsigneeCity() : "—") + ", "
                        + (shipment.getConsigneeCountry() != null ? shipment.getConsigneeCountry() : "—"));

        addCmrSection(cmrTable, "4. Lieu et pays de prise en charge",
                (shipment.getShipperCity() != null ? shipment.getShipperCity() : "—") + ", "
                        + (shipment.getShipperCountry() != null ? shipment.getShipperCountry() : "—"));

        addCmrSection(cmrTable, "5. Documents joints",
                "Certificat d'origine, Facture commerciale"
                        + (shipment.isDangerous() ? ", Déclaration de marchandises dangereuses" : ""));

        addCmrSection(cmrTable, "6. Marques et numéros", shipment.getOrderNumber());

        addCmrSection(cmrTable, "7. Nombre de colis",
                shipment.getPackagesCount() != null ? String.valueOf(shipment.getPackagesCount()) : "—");

        addCmrSection(cmrTable, "8. Mode d'emballage", "Standard");

        addCmrSection(cmrTable, "9. Nature de la marchandise",
                shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "—");

        addCmrSection(cmrTable, "10. Poids brut (kg)",
                shipment.getWeightKg() != null ? String.format("%.2f", shipment.getWeightKg()) : "—");

        addCmrSection(cmrTable, "11. Volume (m³)",
                shipment.getVolumeM3() != null ? String.format("%.3f", shipment.getVolumeM3()) : "—");

        addCmrSection(cmrTable, "12. Instructions particulières",
                (shipment.getIncotermCode() != null ? "Incoterm: " + shipment.getIncotermCode() : "")
                        + (shipment.getHsCode() != null ? " | Code SH: " + shipment.getHsCode() : ""));

        addCmrSection(cmrTable, "13. Délai de livraison",
                shipment.getEstimatedDeliveryDate() != null
                        ? shipment.getEstimatedDeliveryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "—");

        addCmrSection(cmrTable, "14. Valeur déclarée",
                (shipment.getGoodsValue() != null
                        ? String.format("%.2f %s", shipment.getGoodsValue(),
                                shipment.getCurrency() != null ? shipment.getCurrency() : "EUR")
                        : "—"));

        document.add(cmrTable);

        String carrierInfo = "—";
        if (shipment.getCarrier() != null) {
            carrierInfo = shipment.getCarrier().getName();
            if (shipment.getCarrier().getContactPhone() != null) {
                carrierInfo += " | Tél: " + shipment.getCarrier().getContactPhone();
            }
        }

        PdfPTable carrierTable = new PdfPTable(1);
        carrierTable.setWidthPercentage(100);
        carrierTable.setSpacingBefore(6);
        carrierTable.setSpacingAfter(6);

        PdfPCell carrierLabelCell = new PdfPCell(new Paragraph("15. Transporteur", cmrSectionFont));
        carrierLabelCell.setBorder(Rectangle.BOX);
        carrierLabelCell.setBorderColor(SECTION_BORDER);
        carrierLabelCell.setPadding(4);
        carrierLabelCell.setBackgroundColor(TABLE_ALT_BG);
        carrierTable.addCell(carrierLabelCell);

        PdfPCell carrierValueCell = new PdfPCell(new Paragraph(carrierInfo, cmrValueFont));
        carrierValueCell.setBorder(Rectangle.BOX);
        carrierValueCell.setBorderColor(SECTION_BORDER);
        carrierValueCell.setPadding(4);
        carrierTable.addCell(carrierValueCell);

        document.add(carrierTable);

        PdfPTable sigTable = new PdfPTable(2);
        sigTable.setWidthPercentage(100);
        sigTable.setSpacingBefore(30);
        sigTable.setWidths(new float[]{50, 50});

        PdfPCell sig1 = new PdfPCell();
        sig1.setBorder(Rectangle.NO_BORDER);
        sig1.setPadding(10);
        Paragraph sigLine1 = new Paragraph("_________________________________", NORMAL_FONT);
        sigLine1.setAlignment(Element.ALIGN_CENTER);
        sig1.addElement(sigLine1);
        Paragraph sigLabel1 = new Paragraph("L'expéditeur", cmrSectionFont);
        sigLabel1.setAlignment(Element.ALIGN_CENTER);
        sig1.addElement(sigLabel1);
        sigTable.addCell(sig1);

        PdfPCell sig2 = new PdfPCell();
        sig2.setBorder(Rectangle.NO_BORDER);
        sig2.setPadding(10);
        Paragraph sigLine2 = new Paragraph("_________________________________", NORMAL_FONT);
        sigLine2.setAlignment(Element.ALIGN_CENTER);
        sig2.addElement(sigLine2);
        Paragraph sigLabel2 = new Paragraph("Le transporteur", cmrSectionFont);
        sigLabel2.setAlignment(Element.ALIGN_CENTER);
        sig2.addElement(sigLabel2);
        sigTable.addCell(sig2);

        document.add(sigTable);

        addFooter(document, branding);

        document.close();
        byte[] pdfBytes = baos.toByteArray();
        storePdf("cmr", shipment.getOrderNumber(), pdfBytes);
        return pdfBytes;
    }

    public byte[] generateDgdPdf(ShipmentOrder shipment) {
        CompanyBranding branding = resolveBranding(companyIdOf(shipment));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font dgdTitleFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(153, 0, 0));
        Font dgdSectionFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.DARK_GRAY);
        Font dgdValueFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font dgdWarningFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(204, 0, 0));

        Paragraph title = new Paragraph(
                "DÉCLARATION DE MARCHANDISES DANGEREUSES\nDANGEROUS GOODS DECLARATION",
                dgdTitleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        document.add(title);

        Paragraph orderLine = new Paragraph(
                "N° " + shipment.getOrderNumber() + "  |  Date: "
                        + shipment.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY));
        orderLine.setAlignment(Element.ALIGN_CENTER);
        orderLine.setSpacingAfter(12);
        document.add(orderLine);

        if (!shipment.isDangerous()) {
            Paragraph warning = new Paragraph(
                    "⚠ Cette expédition ne contient pas de marchandises dangereuses.\n"
                            + "This shipment does not contain dangerous goods.",
                    dgdWarningFont);
            warning.setAlignment(Element.ALIGN_CENTER);
            warning.setSpacingBefore(40);
            warning.setSpacingAfter(40);
            document.add(warning);

            addFooter(document, branding);
            document.close();
            byte[] earlyPdfBytes = baos.toByteArray();
            storePdf("dgd", shipment.getOrderNumber(), earlyPdfBytes);
            return earlyPdfBytes;
        }

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{50, 50});

        addCmrSection(infoTable, "Expéditeur (Shipper)",
                formatAddress(shipment.getShipperName(), shipment.getShipperAddress(),
                        shipment.getShipperCity(), shipment.getShipperPostalCode(), shipment.getShipperCountry()));

        addCmrSection(infoTable, "Destinataire (Consignee)",
                formatAddress(shipment.getConsigneeName(), shipment.getConsigneeAddress(),
                        shipment.getConsigneeCity(), shipment.getConsigneePostalCode(), shipment.getConsigneeCountry()));

        document.add(infoTable);

        addSectionTitle(document, "Description des marchandises dangereuses");

        PdfPTable goodsTable = new PdfPTable(2);
        goodsTable.setWidthPercentage(100);
        goodsTable.setSpacingBefore(6);

        addCmrSection(goodsTable, "Nature des marchandises",
                shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "—");
        addCmrSection(goodsTable, "Numéro UN", "À COMPLÉTER");
        addCmrSection(goodsTable, "Nom correct de transport",
                shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "—");
        addCmrSection(goodsTable, "Classe", "À COMPLÉTER");
        addCmrSection(goodsTable, "Groupe d'emballage", "À COMPLÉTER");

        String quantity = "—";
        if (shipment.getWeightKg() != null && shipment.getPackagesCount() != null) {
            quantity = shipment.getPackagesCount() + " colis × "
                    + String.format("%.2f", shipment.getWeightKg()) + " kg = "
                    + String.format("%.2f", shipment.getPackagesCount() * shipment.getWeightKg()) + " kg";
        }
        addCmrSection(goodsTable, "Quantité totale", quantity);
        addCmrSection(goodsTable, "Contact d'urgence", "À COMPLÉTER");

        document.add(goodsTable);

        addSectionTitle(document, "Déclaration du transporteur");

        Paragraph declaration = new Paragraph(
                "Le soussigné déclare que les marchandises décrites ci-dessus ont été emballées, "
                        + "marquées et étiquetées conformément aux dispositions applicables.",
                dgdValueFont);
        declaration.setSpacingBefore(6);
        declaration.setSpacingAfter(20);
        document.add(declaration);

        PdfPTable sigTable = new PdfPTable(2);
        sigTable.setWidthPercentage(100);
        sigTable.setSpacingBefore(20);
        sigTable.setWidths(new float[]{50, 50});

        PdfPCell sig1 = new PdfPCell();
        sig1.setBorder(Rectangle.NO_BORDER);
        sig1.setPadding(10);
        Paragraph sigLine1 = new Paragraph("_________________________________", NORMAL_FONT);
        sigLine1.setAlignment(Element.ALIGN_CENTER);
        sig1.addElement(sigLine1);
        Paragraph sigLabel1 = new Paragraph("L'expéditeur", dgdSectionFont);
        sigLabel1.setAlignment(Element.ALIGN_CENTER);
        sig1.addElement(sigLabel1);
        sigTable.addCell(sig1);

        PdfPCell sig2 = new PdfPCell();
        sig2.setBorder(Rectangle.NO_BORDER);
        sig2.setPadding(10);
        Paragraph sigLine2 = new Paragraph("_________________________________", NORMAL_FONT);
        sigLine2.setAlignment(Element.ALIGN_CENTER);
        sig2.addElement(sigLine2);
        Paragraph sigLabel2 = new Paragraph("Le transporteur", dgdSectionFont);
        sigLabel2.setAlignment(Element.ALIGN_CENTER);
        sig2.addElement(sigLabel2);
        sigTable.addCell(sig2);

        document.add(sigTable);

        addFooter(document, branding);

        document.close();
        byte[] pdfBytes = baos.toByteArray();
        storePdf("dgd", shipment.getOrderNumber(), pdfBytes);
        return pdfBytes;
    }

    @Transactional(readOnly = true)
    public byte[] generateCertificateOfOriginPdf(ShipmentOrder shipment) {
        CompanyBranding branding = resolveBranding(companyIdOf(shipment));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font certTitleFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 102, 51));
        Font certSectionFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.DARK_GRAY);
        Font certValueFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font certStampFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 102, 51));

        Paragraph title = new Paragraph(
                "CERTIFICAT D'ORIGINE\nCERTIFICATE OF ORIGIN",
                certTitleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        document.add(title);

        Paragraph orderLine = new Paragraph(
                "N° " + shipment.getOrderNumber() + "  |  Date: "
                        + shipment.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY));
        orderLine.setAlignment(Element.ALIGN_CENTER);
        orderLine.setSpacingAfter(12);
        document.add(orderLine);

        PdfPTable mainTable = new PdfPTable(2);
        mainTable.setWidthPercentage(100);
        mainTable.setWidths(new float[]{50, 50});

        addCmrSection(mainTable, "1. Exportateur (Shipper)",
                formatAddress(shipment.getShipperName(), shipment.getShipperAddress(),
                        shipment.getShipperCity(), shipment.getShipperPostalCode(), shipment.getShipperCountry()));

        addCmrSection(mainTable, "2. Destinataire (Consignee)",
                formatAddress(shipment.getConsigneeName(), shipment.getConsigneeAddress(),
                        shipment.getConsigneeCity(), shipment.getConsigneePostalCode(), shipment.getConsigneeCountry()));

        addCmrSection(mainTable, "3. Pays d'origine",
                shipment.getShipperCountry() != null ? shipment.getShipperCountry() : "—");

        addCmrSection(mainTable, "4. Pays de destination",
                shipment.getConsigneeCountry() != null ? shipment.getConsigneeCountry() : "—");

        String transportInfo = "—";
        if (shipment.getCarrier() != null) {
            transportInfo = shipment.getCarrier().getName();
            if (shipment.getCarrier().getTransportModes() != null) {
                transportInfo = shipment.getCarrier().getTransportModes() + " — " + transportInfo;
            }
        }
        addCmrSection(mainTable, "5. Transport", transportInfo);

        document.add(mainTable);

        addSectionTitle(document, "6. Marchandises");

        PdfPTable goodsTable = new PdfPTable(2);
        goodsTable.setWidthPercentage(100);
        goodsTable.setSpacingBefore(6);

        addCmrSection(goodsTable, "Description",
                shipment.getGoodsDescription() != null ? shipment.getGoodsDescription() : "—");
        addCmrSection(goodsTable, "Code SH",
                shipment.getHsCode() != null ? shipment.getHsCode() : "—");
        addCmrSection(goodsTable, "Valeur",
                (shipment.getGoodsValue() != null
                        ? String.format("%.2f %s", shipment.getGoodsValue(),
                                shipment.getCurrency() != null ? shipment.getCurrency() : "EUR")
                        : "—"));
        addCmrSection(goodsTable, "Poids (kg)",
                shipment.getWeightKg() != null ? String.format("%.2f", shipment.getWeightKg()) : "—");
        addCmrSection(goodsTable, "Nombre de colis",
                shipment.getPackagesCount() != null ? String.valueOf(shipment.getPackagesCount()) : "—");
        addCmrSection(goodsTable, "Incoterm",
                shipment.getIncotermCode() != null ? shipment.getIncotermCode() : "—");
        addCmrSection(goodsTable, "Date d'exportation",
                shipment.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        document.add(goodsTable);

        addSectionTitle(document, "Certification");

        Paragraph certText = new Paragraph(
                "Le soussigné certifie que les marchandises mentionnées dans le présent document "
                        + "ont leur origine dans le pays indiqué.",
                certValueFont);
        certText.setSpacingBefore(6);
        certText.setSpacingAfter(16);
        document.add(certText);

        PdfPTable stampTable = new PdfPTable(2);
        stampTable.setWidthPercentage(100);
        stampTable.setWidths(new float[]{50, 50});
        stampTable.setSpacingBefore(10);

        PdfPCell stampCell = new PdfPCell();
        stampCell.setBorder(Rectangle.NO_BORDER);
        stampCell.setPadding(10);

        PdfPTable stampCircle = new PdfPTable(1);
        stampCircle.setWidthPercentage(60);
        stampCircle.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell circleCell = new PdfPCell(new Phrase("CERTIFIÉ", certStampFont));
        circleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        circleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        circleCell.setFixedHeight(60);
        circleCell.setBorder(Rectangle.BOX);
        circleCell.setBorderColor(new Color(0, 102, 51));
        circleCell.setBorderWidth(2);
        circleCell.setBackgroundColor(new Color(230, 255, 230));
        stampCircle.addCell(circleCell);

        stampCell.addElement(stampCircle);
        stampTable.addCell(stampCell);

        PdfPCell signCell = new PdfPCell();
        signCell.setBorder(Rectangle.NO_BORDER);
        signCell.setPadding(10);
        Paragraph signLine = new Paragraph("\n\n\n_________________________________", NORMAL_FONT);
        signLine.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(signLine);
        Paragraph signLabel = new Paragraph("Signature et cachet", certSectionFont);
        signLabel.setAlignment(Element.ALIGN_CENTER);
        signCell.addElement(signLabel);
        stampTable.addCell(signCell);

        document.add(stampTable);

        addFooter(document, branding);

        document.close();
        byte[] pdfBytes = baos.toByteArray();
        storePdf("certificates", shipment.getOrderNumber(), pdfBytes);
        return pdfBytes;
    }

    private String formatAddress(String name, String address, String city, String postalCode, String country) {
        StringBuilder sb = new StringBuilder();
        if (name != null) sb.append(name);
        if (address != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(address);
        }
        String cityLine = (postalCode != null ? postalCode + " " : "") + (city != null ? city : "");
        if (!cityLine.trim().isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(cityLine);
        }
        if (country != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(country);
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private void addCmrSection(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(SECTION_BORDER);
        labelCell.setPadding(4);
        labelCell.setBackgroundColor(TABLE_ALT_BG);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value != null ? value : "—", NORMAL_FONT));
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(SECTION_BORDER);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    private String translateStatus(String status) {
        if (status == null) return "—";
        return switch (status.toUpperCase()) {
            case "DRAFT" -> "Brouillon";
            case "QUOTED" -> "Devisé";
            case "BOOKED" -> "Réservé";
            case "IN_TRANSIT" -> "En transit";
            case "DELIVERED" -> "Livré";
            case "CANCELLED" -> "Annulé";
            default -> status;
        };
    }

    private void storePdf(String category, String reference, byte[] pdfData) {
        try {
            fileStorage.uploadPdf(category, reference, pdfData);
        } catch (Exception e) {
            log.warn("[PDF] Impossible de stocker {}/{}: {}", category, reference, e.getMessage());
        }
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
