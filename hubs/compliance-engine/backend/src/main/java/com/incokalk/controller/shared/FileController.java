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

/**
 * Stockage générique de documents (pas les logos -- voir BrandingController,
 * qui a sa propre logique d'upload de logo intégrée à la mise à jour du
 * branding, seule voie réellement utilisée par le produit aujourd'hui).
 * /download/{bucket}/{key} est délibérément public (SecurityConfig) : conçu
 * pour permettre un futur partage de document externe (facture, certificat...)
 * sans authentification, à la manière de /v1/shared/**.
 */
@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Upload / download de fichiers")
public class FileController {

    private final FileStorageService fileStorage;
    private final StorageConfig storageConfig;

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

    private static final long MAX_DOC_SIZE = 20 * 1024 * 1024;

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
}
