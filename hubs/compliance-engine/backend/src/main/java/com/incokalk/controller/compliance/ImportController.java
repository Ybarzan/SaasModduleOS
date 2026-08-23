package com.incokalk.controller.compliance;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ImportService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/import")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Import de données en CSV")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/carriers")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Importer des transporteurs depuis un CSV")
    public ResponseEntity<Map<String, Object>> importCarriers(
            @RequestParam("file") MultipartFile file) throws Exception {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(importService.importCarriersCsv(file, companyId));
    }

    @PostMapping("/preview")
    @Operation(summary = "Prévisualiser les premières lignes d'un CSV")
    public ResponseEntity<Map<String, Object>> previewCsv(
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(importService.previewCsv(file));
    }
}
