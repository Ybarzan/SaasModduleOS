package com.incokalk.controller.compliance;

import com.incokalk.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/import")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Import de données en CSV")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/preview")
    @Operation(summary = "Prévisualiser les premières lignes d'un CSV")
    public ResponseEntity<Map<String, Object>> previewCsv(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(importService.previewCsv(file));
    }
}
