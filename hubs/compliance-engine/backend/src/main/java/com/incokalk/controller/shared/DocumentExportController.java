package com.incokalk.controller.shared;

import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.DocumentExportService;
import com.incokalk.service.QuoteService;
import com.incokalk.service.ShipmentService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Export de documents PDF")
@RequiresPlan(Company.Plan.STARTER)
public class DocumentExportController {

    private final DocumentExportService documentExportService;
    private final QuoteService quoteService;
    private final ShipmentService shipmentService;

    @PostMapping("/quotes/pdf")
    @Operation(summary = "Exporter les devis en PDF")
    public ResponseEntity<byte[]> exportQuotesPdf(
            @Valid @RequestBody QuoteRequestDTO request,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        List<QuoteResponseDTO> quotes = quoteService.getQuotes(request, companyId);
        byte[] pdf = documentExportService.generateQuotePdf(quotes, request);

        String filename = "devis-incokalk-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/shipments/{id}/pdf")
    @Operation(summary = "Exporter l'étiquette d'expédition en PDF")
    public ResponseEntity<byte[]> exportShippingLabelPdf(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ShipmentOrder shipment = shipmentService.getShipmentWithTracking(id, companyId);
        byte[] pdf = documentExportService.generateShippingLabelPdf(shipment);

        String filename = "etiquette-" + shipment.getOrderNumber() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/shipments/{id}/cmr")
    @Operation(summary = "Exporter le CMR en PDF")
    public ResponseEntity<byte[]> exportCmrPdf(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ShipmentOrder shipment = shipmentService.getShipmentWithTracking(id, companyId);
        byte[] pdf = documentExportService.generateCmrPdf(shipment);

        String filename = "cmr-" + shipment.getOrderNumber() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/shipments/{id}/dgd")
    @Operation(summary = "Exporter la déclaration de marchandises dangereuses en PDF")
    public ResponseEntity<byte[]> exportDgdPdf(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ShipmentOrder shipment = shipmentService.getShipmentWithTracking(id, companyId);
        byte[] pdf = documentExportService.generateDgdPdf(shipment);

        String filename = "dgd-" + shipment.getOrderNumber() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/shipments/{id}/certificate-of-origin")
    @Operation(summary = "Exporter le certificat d'origine en PDF")
    public ResponseEntity<byte[]> exportCertificateOfOriginPdf(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        ShipmentOrder shipment = shipmentService.getShipmentWithTracking(id, companyId);
        byte[] pdf = documentExportService.generateCertificateOfOriginPdf(shipment);

        String filename = "certificat-origine-" + shipment.getOrderNumber() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
