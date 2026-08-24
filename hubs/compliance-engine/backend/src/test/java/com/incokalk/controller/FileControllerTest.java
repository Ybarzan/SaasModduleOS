package com.incokalk.controller;

import com.incokalk.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

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
