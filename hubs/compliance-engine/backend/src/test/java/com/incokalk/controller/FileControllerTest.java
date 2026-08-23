package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorage;
    @MockBean
    private CompanyRoleRepository companyRoleRepository;

    // La résolution du tenant (attribut de requête "companyId") passe par TenantFilter,
    // qui exige un header X-Tenant-ID + une ligne CompanyRole en base pour l'utilisateur.
    // On simule cette ligne pour les scénarios où la company doit être trouvée.
    private void mockTenantFound() {
        when(companyRoleRepository.findByCompanyIdAndUserId(companyId, userId))
            .thenReturn(Optional.of(CompanyRole.builder().role(CompanyRole.Role.OWNER).build()));
    }

    // ── POST /v1/files/upload/logo ──────────────────────────────────────

    @Test
    @DisplayName("POST /v1/files/upload/logo → 400 si company non trouvée (pas de tenant résolu)")
    void uploadLogo_companyNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "content".getBytes());

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Company non trouvée"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 400 si fichier vide")
    void uploadLogo_emptyFile() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Fichier vide"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 400 si logo trop volumineux")
    void uploadLogo_tooLarge() throws Exception {
        mockTenantFound();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", big);

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Logo trop volumineux (max 5 Mo)"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 400 si content-type absent")
    void uploadLogo_nullContentType() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", null, "content".getBytes());

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Type non supporté (images uniquement)"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 400 si type non-image")
    void uploadLogo_wrongContentType() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "logo.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Type non supporté (images uniquement)"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 200 succès")
    void uploadLogo_success() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "content".getBytes());
        when(fileStorage.uploadLogo(eq(companyId), any(), any(), any())).thenReturn("logos/key.png");
        when(fileStorage.getPublicUrl(any(), eq("logos/key.png"))).thenReturn("http://cdn/logos/key.png");

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("logos/key.png"))
            .andExpect(jsonPath("$.url").value("http://cdn/logos/key.png"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/logo → 500 si le stockage échoue")
    void uploadLogo_storageError() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "content".getBytes());
        when(fileStorage.uploadLogo(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("MinIO indisponible"));

        mockMvc.perform(multipart("/v1/files/upload/logo")
                .file(file)
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Erreur upload: MinIO indisponible"));
    }

    // ── POST /v1/files/upload/document ──────────────────────────────────

    @Test
    @DisplayName("POST /v1/files/upload/document → 400 si fichier vide")
    void uploadDocument_emptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Fichier vide"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 400 si document trop volumineux")
    void uploadDocument_tooLarge() throws Exception {
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", big);

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Document trop volumineux (max 20 Mo)"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 200 succès avec extension normale")
    void uploadDocument_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());
        when(fileStorage.uploadDocument(any(), any(), any())).thenReturn("documents/general/key.pdf");
        when(fileStorage.getPublicUrl(any(), eq("documents/general/key.pdf")))
            .thenReturn("http://cdn/documents/general/key.pdf");

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("documents/general/key.pdf"))
            .andExpect(jsonPath("$.url").value("http://cdn/documents/general/key.pdf"));
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 200 avec category personnalisée")
    void uploadDocument_customCategory() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());
        when(fileStorage.uploadDocument(any(), any(), any())).thenReturn("documents/invoices/key.pdf");
        when(fileStorage.getPublicUrl(any(), any())).thenReturn("http://cdn/x");

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .param("category", "invoices")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 200 nom de fichier sans extension")
    void uploadDocument_noExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "documentnoext", "application/octet-stream", "content".getBytes());
        when(fileStorage.uploadDocument(any(), any(), any())).thenReturn("documents/general/key.bin");
        when(fileStorage.getPublicUrl(any(), any())).thenReturn("http://cdn/x");

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 200 nom de fichier original absent")
    void uploadDocument_nullOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", null, "application/octet-stream", "content".getBytes());
        when(fileStorage.uploadDocument(any(), any(), any())).thenReturn("documents/general/key.bin");
        when(fileStorage.getPublicUrl(any(), any())).thenReturn("http://cdn/x");

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/files/upload/document → 500 si le stockage échoue")
    void uploadDocument_storageError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());
        when(fileStorage.uploadDocument(any(), any(), any()))
            .thenThrow(new RuntimeException("MinIO indisponible"));

        mockMvc.perform(multipart("/v1/files/upload/document")
                .file(file)
                .header("Authorization", authHeader()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Erreur upload: MinIO indisponible"));
    }

    // ── GET /v1/files/download/{bucket}/{key} ───────────────────────────

    @Test
    @DisplayName("GET /v1/files/download/documents/{key} → 302 redirige vers l'URL présignée (bucket documents)")
    void download_documentsBucket() throws Exception {
        when(fileStorage.getPresignedUrl(eq("test-documents"), eq("key.pdf"), any(Duration.class)))
            .thenReturn("http://presigned/documents/key.pdf");

        mockMvc.perform(get("/v1/files/download/documents/key.pdf")
                .header("Authorization", authHeader()))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://presigned/documents/key.pdf"));
    }

    @Test
    @DisplayName("GET /v1/files/download/logos/{key} → 302 redirige vers l'URL présignée (bucket logos)")
    void download_logosBucket() throws Exception {
        when(fileStorage.getPresignedUrl(eq("test-logos"), eq("key.png"), any(Duration.class)))
            .thenReturn("http://presigned/logos/key.png");

        mockMvc.perform(get("/v1/files/download/logos/key.png")
                .header("Authorization", authHeader()))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://presigned/logos/key.png"));
    }

    @Test
    @DisplayName("GET /v1/files/download/{bucket}/{key} → 302 bucket inconnu utilisé tel quel")
    void download_unknownBucket() throws Exception {
        when(fileStorage.getPresignedUrl(eq("other-bucket"), eq("key.txt"), any(Duration.class)))
            .thenReturn("http://presigned/other-bucket/key.txt");

        mockMvc.perform(get("/v1/files/download/other-bucket/key.txt")
                .header("Authorization", authHeader()))
            .andExpect(status().isFound())
            .andExpect(header().string(HttpHeaders.LOCATION, "http://presigned/other-bucket/key.txt"));
    }
}
