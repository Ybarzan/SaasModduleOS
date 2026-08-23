package com.incokalk.controller;

import com.incokalk.model.CustomsDeclaration;
import com.incokalk.service.CustomsDeclarationService;
import com.incokalk.service.DeclarationValidationService;
import com.incokalk.service.DocumentExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomsDeclarationControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomsDeclarationService declarationService;
    @MockBean
    private DocumentExportService documentExportService;
    @MockBean
    private DeclarationValidationService validationService;

    private CustomsDeclaration sample() {
        return CustomsDeclaration.builder()
            .declarationType(CustomsDeclaration.DeclarationType.DAU_IMPORT)
            .customsOffice("FR001000")
            .customsRegime("40")
            .declaredValue(BigDecimal.valueOf(1000))
            .currency("EUR")
            .originCountry("CN")
            .destinationCountry("FR")
            .hsCode("85235110")
            .goodsDescription("Composants électroniques")
            .netWeight(BigDecimal.TEN)
            .grossWeight(BigDecimal.valueOf(12))
            .packages(3)
            .build();
    }

    @Test
    @DisplayName("GET /v1/customs-declarations → 200 liste complète (sans pagination)")
    void list_noPagination() throws Exception {
        when(declarationService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/customs-declarations").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].customsOffice").value("FR001000"));
    }

    @Test
    @DisplayName("GET /v1/customs-declarations?page=0&size=10 → 200 page")
    void list_withPagination() throws Exception {
        when(declarationService.getAll(any())).thenReturn(new PageImpl<>(List.of(sample())));

        mockMvc.perform(get("/v1/customs-declarations?page=0&size=10")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].customsOffice").value("FR001000"));
    }

    @Test
    @DisplayName("GET /v1/customs-declarations?size=0 → ignore la pagination (size non positif)")
    void list_zeroSizeIgnoresPagination() throws Exception {
        when(declarationService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/v1/customs-declarations?page=0&size=0")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id} → 200")
    void getById_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenReturn(sample());

        mockMvc.perform(get("/v1/customs-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode").value("85235110"));
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id} → 400 si introuvable")
    void getById_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenThrow(new IllegalArgumentException("Déclaration introuvable"));

        mockMvc.perform(get("/v1/customs-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Déclaration introuvable"));
    }

    @Test
    @DisplayName("POST /v1/customs-declarations → 201")
    void create_success() throws Exception {
        when(declarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/customs-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"declarationType":"DAU_IMPORT","customsOffice":"FR001000",
                     "declaredValue":1000,"originCountry":"CN","destinationCountry":"FR",
                     "hsCode":"85235110"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.hsCode").value("85235110"));
    }

    @Test
    @DisplayName("POST /v1/customs-declarations → 400 si le service refuse")
    void create_rejected() throws Exception {
        when(declarationService.create(any())).thenThrow(new IllegalArgumentException("HS code invalide"));

        mockMvc.perform(post("/v1/customs-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationType\":\"DAU_IMPORT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("HS code invalide"));
    }

    @Test
    @DisplayName("PUT /v1/customs-declarations/{id} → 200")
    void update_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/customs-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customsOffice\":\"FR001000\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/customs-declarations/{id} → 400 si introuvable")
    void update_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.update(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Déclaration introuvable"));

        mockMvc.perform(put("/v1/customs-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /v1/customs-declarations/{id}/status → 200")
    void updateStatus_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.updateStatus(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/customs-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUBMITTED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/customs-declarations/{id}/status → 400 si transition invalide")
    void updateStatus_invalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.updateStatus(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Transition invalide"));

        mockMvc.perform(put("/v1/customs-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLEARED\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /v1/customs-declarations/{id} → 200")
    void delete_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/customs-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Déclaration supprimée"));
    }

    @Test
    @DisplayName("DELETE /v1/customs-declarations/{id} → 400 si introuvable")
    void delete_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Déclaration introuvable"))
            .when(declarationService).delete(id);

        mockMvc.perform(delete("/v1/customs-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/pdf → 200")
    void exportPdf_success() throws Exception {
        UUID id = UUID.randomUUID();
        CustomsDeclaration d = sample();
        when(declarationService.getById(id)).thenReturn(d);
        when(documentExportService.generateDauPdf(d)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/pdf → 400 si introuvable")
    void exportPdf_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/xml → 200")
    void exportXml_success() throws Exception {
        UUID id = UUID.randomUUID();
        CustomsDeclaration d = sample();
        when(declarationService.getById(id)).thenReturn(d);
        when(documentExportService.generateDauXml(d)).thenReturn("<dau/>".getBytes());

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/xml").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/xml → 400 si introuvable")
    void exportXml_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/xml").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/validate → 200 sans alerte")
    void validate_noAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenReturn(sample());
        when(validationService.validateDau(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/validate → 200 avec alertes")
    void validate_withAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenReturn(sample());
        when(validationService.validateDau(any()))
            .thenReturn(List.of(new DeclarationValidationService.Alert("WARNING", "MISSING_HS", "HS code manquant")));

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/{id}/validate → 400 si introuvable")
    void validate_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(declarationService.getById(id)).thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/customs-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/customs-declarations/stats → 200")
    void stats() throws Exception {
        when(declarationService.getStats()).thenReturn(java.util.Map.of("total", 5));

        mockMvc.perform(get("/v1/customs-declarations/stats").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5));
    }
}
