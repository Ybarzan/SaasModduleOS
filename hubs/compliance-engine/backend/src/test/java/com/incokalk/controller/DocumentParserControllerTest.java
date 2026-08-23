package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.ParsedDocument;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.service.DocumentParserService;
import com.incokalk.service.ocr.OcrService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentParserControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentParserService parserService;
    @MockBean
    private OcrService ocrService;
    @MockBean
    private CompanyRoleRepository companyRoleRepository;

    // Meme schema que FileControllerTest : httpReq.getAttribute("companyId") est
    // resolu par TenantFilter a partir d'une ligne CompanyRole existante.
    private void mockTenantFound() {
        when(companyRoleRepository.findByCompanyIdAndUserId(companyId, userId))
            .thenReturn(Optional.of(CompanyRole.builder().role(CompanyRole.Role.OWNER).build()));
    }

    @Test
    @DisplayName("POST /v1/document-parser/parse/image → 200, OCR reussi puis parse")
    void parseImage_success() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "facture.jpg", "image/jpeg", "content".getBytes());
        when(ocrService.extractText(any())).thenReturn(Optional.of("Invoice text extrait"));

        ParsedDocument doc = ParsedDocument.builder()
            .id(UUID.randomUUID())
            .documentType(ParsedDocument.DocumentType.COMMERCIAL_INVOICE)
            .confidence(BigDecimal.valueOf(0.8))
            .build();
        when(parserService.parseFromText(eq("Invoice text extrait"),
            eq(ParsedDocument.DocumentType.COMMERCIAL_INVOICE), eq("facture.jpg"), eq(companyId)))
            .thenReturn(doc);

        mockMvc.perform(multipart("/v1/document-parser/parse/image")
                .file(file)
                .param("documentType", "COMMERCIAL_INVOICE")
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentType").value("COMMERCIAL_INVOICE"));
    }

    @Test
    @DisplayName("POST /v1/document-parser/parse/image → 400 si l'OCR ne trouve aucun texte")
    void parseImage_ocrEmpty_returnsBadRequest() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "flou.jpg", "image/jpeg", "content".getBytes());
        when(ocrService.extractText(any())).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/v1/document-parser/parse/image")
                .file(file)
                .param("documentType", "PACKING_LIST")
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "Impossible d'extraire du texte de cette image. Réessayez avec un cadrage plus net."));
    }

    @Test
    @DisplayName("POST /v1/document-parser/parse/image → 400 si l'OCR renvoie du texte vide/blanc")
    void parseImage_ocrBlank_returnsBadRequest() throws Exception {
        mockTenantFound();
        MockMultipartFile file = new MockMultipartFile("file", "blanc.jpg", "image/jpeg", "content".getBytes());
        when(ocrService.extractText(any())).thenReturn(Optional.of("   "));

        mockMvc.perform(multipart("/v1/document-parser/parse/image")
                .file(file)
                .param("documentType", "BILL_OF_LADING")
                .header("Authorization", authHeader())
                .header("X-Tenant-ID", companyId.toString()))
            .andExpect(status().isBadRequest());
    }
}
