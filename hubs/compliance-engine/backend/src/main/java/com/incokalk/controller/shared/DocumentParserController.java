package com.incokalk.controller.shared;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.ParsedDocument;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.DocumentParserService;
import com.incokalk.service.ocr.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/document-parser")
@RequiredArgsConstructor
@Tag(name = "Document Parser", description = "Extraction de données depuis documents (facture, BoL, certificat, packing list)")
public class DocumentParserController {

    private final DocumentParserService parserService;
    private final OcrService ocrService;

    record ParseTextRequest(@NotBlank String text, ParsedDocument.DocumentType documentType) {}

    @PostMapping("/parse/text")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Parser un document à partir de texte brut")
    public ResponseEntity<ParsedDocument> parseText(@Valid @RequestBody ParseTextRequest req, HttpServletRequest httpReq) {
        UUID companyId = (UUID) httpReq.getAttribute("companyId");
        ParsedDocument doc = parserService.parseFromText(
                req.text(), req.documentType(), "text-input", companyId);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/parse/pdf")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Parser un document PDF")
    public ResponseEntity<ParsedDocument> parsePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") ParsedDocument.DocumentType docType,
            HttpServletRequest httpReq) throws IOException {
        UUID companyId = (UUID) httpReq.getAttribute("companyId");
        ParsedDocument doc = parserService.parseFromPdf(
                file.getBytes(), docType, file.getOriginalFilename(), companyId);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/parse/image")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Parser un document depuis une photo (scan mobile, OCR)")
    public ResponseEntity<?> parseImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") ParsedDocument.DocumentType docType,
            HttpServletRequest httpReq) {
        UUID companyId = (UUID) httpReq.getAttribute("companyId");
        Optional<String> text = ocrService.extractText(file);
        if (text.isEmpty() || text.get().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Impossible d'extraire du texte de cette image. Réessayez avec un cadrage plus net."));
        }
        ParsedDocument doc = parserService.parseFromText(
                text.get(), docType, file.getOriginalFilename(), companyId);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/history")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des documents parsés")
    public ResponseEntity<List<ParsedDocument>> history() {
        return ResponseEntity.ok(parserService.getHistory());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Détail d'un document parsé")
    public ResponseEntity<ParsedDocument> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(parserService.getById(id));
    }

    @GetMapping("/type/{docType}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Documents parsés par type")
    public ResponseEntity<List<ParsedDocument>> getByType(@PathVariable ParsedDocument.DocumentType docType) {
        return ResponseEntity.ok(parserService.getByType(docType));
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statistiques des documents parsés")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(parserService.getStats());
    }
}
