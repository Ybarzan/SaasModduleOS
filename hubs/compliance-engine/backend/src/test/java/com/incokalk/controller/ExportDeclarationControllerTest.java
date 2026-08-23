package com.incokalk.controller;

import com.incokalk.model.ExportDeclaration;
import com.incokalk.service.DeclarationValidationService;
import com.incokalk.service.DocumentExportService;
import com.incokalk.service.ExportDeclarationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
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

class ExportDeclarationControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportDeclarationService exportDeclarationService;
    @MockBean
    private DocumentExportService documentExportService;
    @MockBean
    private DeclarationValidationService validationService;

    private ExportDeclaration sample() {
        return ExportDeclaration.builder()
            .declarationNumber("EXP-2026-001")
            .declarationType(ExportDeclaration.ExportType.AES)
            .exporterEori("FR12345678900001")
            .destinationCountry("US")
            .goodsDescription("Pièces mécaniques")
            .hsCode("84821010")
            .declaredValue(BigDecimal.valueOf(2500))
            .currency("EUR")
            .netWeight(BigDecimal.valueOf(50))
            .grossWeight(BigDecimal.valueOf(55))
            .packagesCount(2)
            .build();
    }

    @Test
    @DisplayName("GET /v1/export-declarations → 200 liste")
    void list_success() throws Exception {
        when(exportDeclarationService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/export-declarations").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hsCode").value("84821010"));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id} → 200")
    void getById_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id)).thenReturn(sample());

        mockMvc.perform(get("/v1/export-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exporterEori").value("FR12345678900001"));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id} → 400 si introuvable")
    void getById_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("Déclaration export introuvable"));

        mockMvc.perform(get("/v1/export-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Déclaration export introuvable"));
    }

    @Test
    @DisplayName("POST /v1/export-declarations → 200 avec type déclaré")
    void create_success_withType() throws Exception {
        when(exportDeclarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/export-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"declarationType":"AES","exporterEori":"FR12345678900001",
                     "destinationCountry":"US","goodsDescription":"Pièces mécaniques",
                     "hsCode":"84821010","declaredValue":2500,"currency":"USD",
                     "netWeight":50,"grossWeight":55,"packagesCount":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode").value("84821010"));
    }

    @Test
    @DisplayName("POST /v1/export-declarations → 200 sans type déclaré (devise par défaut EUR)")
    void create_success_withoutType() throws Exception {
        when(exportDeclarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/export-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exporterEori\":\"FR12345678900001\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/export-declarations → 400 si le service refuse")
    void create_rejected() throws Exception {
        when(exportDeclarationService.create(any()))
            .thenThrow(new IllegalArgumentException("HS code invalide"));

        mockMvc.perform(post("/v1/export-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationType\":\"AES\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("HS code invalide"));
    }

    @Test
    @DisplayName("PUT /v1/export-declarations/{id} → 200 avec tous les champs renseignés")
    void update_success_allFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/export-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"declarationType":"EXS","exporterEori":"FR12345678900001",
                     "destinationCountry":"US","goodsDescription":"Pièces mécaniques",
                     "hsCode":"84821010","declaredValue":2500,"currency":"USD",
                     "netWeight":50,"grossWeight":55,"packagesCount":2}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/export-declarations/{id} → 200 sans aucun champ (corps vide)")
    void update_success_emptyBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/export-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/export-declarations/{id} → 400 si introuvable ou non modifiable")
    void update_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.update(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée"));

        mockMvc.perform(put("/v1/export-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seule une déclaration en brouillon peut être modifiée"));
    }

    @Test
    @DisplayName("PUT /v1/export-declarations/{id}/status → 200")
    void updateStatus_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.updateStatus(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/export-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUBMITTED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/export-declarations/{id}/status → 400 si transition invalide")
    void updateStatus_invalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.updateStatus(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Transition invalide : DRAFT → VALIDATED"));

        mockMvc.perform(put("/v1/export-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VALIDATED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Transition invalide : DRAFT → VALIDATED"));
    }

    @Test
    @DisplayName("DELETE /v1/export-declarations/{id} → 200")
    void delete_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/export-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Déclaration d'export supprimée"));
    }

    @Test
    @DisplayName("DELETE /v1/export-declarations/{id} → 400 si introuvable ou non supprimable")
    void delete_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées"))
            .when(exportDeclarationService).delete(id);

        mockMvc.perform(delete("/v1/export-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seules les déclarations en DRAFT peuvent être supprimées"));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/stats → 200")
    void stats() throws Exception {
        when(exportDeclarationService.getStats()).thenReturn(java.util.Map.of("DRAFT", 3));

        mockMvc.perform(get("/v1/export-declarations/stats").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.DRAFT").value(3));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id}/pdf → 200")
    void exportPdf_success() throws Exception {
        UUID id = UUID.randomUUID();
        ExportDeclaration d = sample();
        when(exportDeclarationService.getById(id)).thenReturn(d);
        when(documentExportService.generateExportPdf(d)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/v1/export-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id}/pdf → 400 si introuvable")
    void exportPdf_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/export-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id}/validate → 200 sans alerte")
    void validate_noAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateExport(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/export-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id}/validate → 200 avec alertes")
    void validate_withAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateExport(any()))
            .thenReturn(List.of(new DeclarationValidationService.Alert("WARNING", "MISSING_EORI", "EORI manquant")));

        mockMvc.perform(get("/v1/export-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("GET /v1/export-declarations/{id}/validate → 400 si introuvable")
    void validate_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(exportDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/export-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }
}
