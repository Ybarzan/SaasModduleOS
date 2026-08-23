package com.incokalk.controller.shared;

import com.incokalk.config.StorageConfig;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Upload / download de fichiers")
public class FileController {

    private final FileStorageService fileStorage;
    private final StorageConfig storageConfig;

    @PostMapping("/upload/logo")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Upload un logo de company")
    public ResponseEntity<Map<String, String>> uploadCompanyLogo(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest req) {

        extractUserId(req); // ensures the caller is authenticated
        UUID companyId = extractCompanyId(req);
        if (companyId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Company non trouvée"));
        }

        validateImage(file);
        try {
            String key = fileStorage.uploadLogo(companyId, file.getOriginalFilename(),
                file.getBytes(), file.getContentType());

            String url = fileStorage.getPublicUrl(storageConfig.getBucketLogos(), key);
            return ResponseEntity.ok(Map.of("key", key, "url", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erreur upload: " + e.getMessage()));
        }
    }

    @PostMapping("/upload/document")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Upload un document")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category,
            HttpServletRequest req) {

        extractUserId(req);
        validateDocument(file);

        try {
            String fileName = category + "/" + UUID.randomUUID() + extractExtension(file.getOriginalFilename());
            String key = fileStorage.uploadDocument(fileName, file.getBytes(), file.getContentType());

            String url = fileStorage.getPublicUrl(storageConfig.getBucketDocuments(), key);
            return ResponseEntity.ok(Map.of("key", key, "url", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erreur upload: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{bucket}/{key:.*}")
    @Operation(summary = "Télécharger un fichier via URL présignée")
    public ResponseEntity<Void> getDownloadUrl(
            @PathVariable String bucket,
            @PathVariable String key) {

        String resolvedBucket = switch (bucket) {
            case "documents" -> storageConfig.getBucketDocuments();
            case "logos" -> storageConfig.getBucketLogos();
            default -> bucket;
        };

        String presignedUrl = fileStorage.getPresignedUrl(resolvedBucket, key, Duration.ofMinutes(15));
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, presignedUrl)
            .build();
    }

    // ── Validation ────────────────────────────────────────────────────

    private static final long MAX_LOGO_SIZE = 5 * 1024 * 1024;
    private static final long MAX_DOC_SIZE = 20 * 1024 * 1024;

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Fichier vide");
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Logo trop volumineux (max 5 Mo)");
        }
        String ct = file.getContentType();
        if (ct == null || (!ct.startsWith("image/"))) {
            throw new IllegalArgumentException("Type non supporté (images uniquement)");
        }
    }

    private void validateDocument(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Fichier vide");
        if (file.getSize() > MAX_DOC_SIZE) {
            throw new IllegalArgumentException("Document trop volumineux (max 20 Mo)");
        }
    }

    private String extractExtension(String name) {
        if (name == null) return ".bin";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : ".bin";
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) return null;
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }
}
