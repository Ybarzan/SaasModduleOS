package com.incokalk.controller.config;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyBranding;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.BrandingService;
import com.incokalk.service.FileStorageService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/branding")
@RequiredArgsConstructor
@Tag(name = "Branding", description = "Personnalisation white-label du portail client")
@RequiresPlan(Company.Plan.STARTER)
public class BrandingController {

    private final BrandingService brandingService;
    private final FileStorageService fileStorageService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Obtenir la configuration branding de l entreprise")
    public ResponseEntity<?> getBranding() {
        UUID companyId = TenantContext.get();
        CompanyBranding branding = brandingService.getBranding(companyId);
        if (branding == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(branding);
    }

    @PutMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre a jour le branding (logo, couleurs, domaine)")
    public ResponseEntity<CompanyBranding> updateBranding(@RequestBody Map<String, Object> updates) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(brandingService.updateBranding(companyId, updates));
    }

    @PostMapping("/logo")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Televerser le logo de l'entreprise")
    public ResponseEntity<CompanyBranding> uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
        UUID companyId = TenantContext.get();
        String url = fileStorageService.uploadLogoAndGetUrl(
            companyId, file.getOriginalFilename(), file.getBytes(), file.getContentType());
        return ResponseEntity.ok(brandingService.updateBranding(companyId, Map.of("logoUrl", url)));
    }

    @GetMapping("/portal-config")
    @Operation(summary = "Configuration publique du portail (sans auth)")
    public ResponseEntity<Map<String, Object>> getPortalConfig(
            @RequestParam UUID companyId,
            @RequestParam(required = false, defaultValue = "FR") String lang) {
        return ResponseEntity.ok(brandingService.getPortalConfig(companyId, lang));
    }

    @GetMapping("/translations")
    @Operation(summary = "Traductions pour la langue demandee")
    public ResponseEntity<Map<String, String>> getTranslations(
            @RequestParam(required = false, defaultValue = "FR") String lang) {
        return ResponseEntity.ok(brandingService.getTranslations(lang));
    }

    @GetMapping("/languages")
    @Operation(summary = "Langues supportees")
    public ResponseEntity<List<String>> getLanguages() {
        return ResponseEntity.ok(brandingService.getSupportedLanguages());
    }
}
