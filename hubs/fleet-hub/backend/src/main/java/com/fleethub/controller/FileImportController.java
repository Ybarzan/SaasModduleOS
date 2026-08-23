package com.fleethub.controller;

import com.fleethub.dto.ImportResultDto;
import com.fleethub.model.ImportHistory;
import com.fleethub.security.TenantContext;
import com.fleethub.service.FileImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Import de fichiers tachygraphe et carburant")
public class FileImportController {

    private final FileImportService importService;

    @PostMapping(value = "/tachograph", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer un fichier CSV ou DDD tachygraphe",
            description = "Accepte les fichiers CSV (avec en-têtes) et les fichiers DDD binaires (export carte conducteur)")
    public ResponseEntity<ImportResultDto> importTachograph(
            @RequestParam("file") MultipartFile file) throws IOException {
        Long companyId = TenantContext.require().getId();
        ImportResultDto result = importService.importTachograph(file, companyId);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/fuel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer un fichier CSV/DSW carburant")
    public ResponseEntity<ImportResultDto> importFuel(
            @RequestParam("file") MultipartFile file) throws IOException {
        Long companyId = TenantContext.require().getId();
        ImportResultDto result = importService.importFuel(file, companyId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    @Operation(summary = "Historique des imports effectués")
    public ResponseEntity<List<ImportHistory>> getHistory() {
        Long companyId = TenantContext.require().getId();
        return ResponseEntity.ok(importService.getHistory(companyId));
    }
}
