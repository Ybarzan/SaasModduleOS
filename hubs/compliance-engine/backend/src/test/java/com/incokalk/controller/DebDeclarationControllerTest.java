package com.incokalk.controller;

import com.incokalk.model.DebDeclaration;
import com.incokalk.service.DebDeclarationService;
import com.incokalk.service.DeclarationValidationService;
import com.incokalk.service.DocumentExportService;
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

class DebDeclarationControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private DebDeclarationService debDeclarationService;
    @MockBean
    private DocumentExportService documentExportService;
    @MockBean
    private DeclarationValidationService validationService;

    private DebDeclaration sample() {
        return DebDeclaration.builder()
            .declarationNumber("DEB-2026-08-001")
            .declarationType(DebDeclaration.DebType.DEB_INTRODUCTION)
            .period("2026-08")
            .partnerCountry("DE")
            .natureOfTransaction("11")
            .modeOfTransport("3")
            .netMass(BigDecimal.valueOf(120))
            .statisticalValue(BigDecimal.valueOf(5000))
            .hsCode8("85235110")
            .goodsDescription("Composants électroniques")
            .build();
    }

    @Test
    @DisplayName("GET /v1/deb-declarations → 200 liste")
    void list_success() throws Exception {
        when(debDeclarationService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/deb-declarations").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hsCode8").value("85235110"));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id} → 200")
    void getById_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id)).thenReturn(sample());

        mockMvc.perform(get("/v1/deb-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partnerCountry").value("DE"));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id} → 400 si introuvable")
    void getById_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("Déclaration DEB introuvable"));

        mockMvc.perform(get("/v1/deb-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Déclaration DEB introuvable"));
    }

    @Test
    @DisplayName("POST /v1/deb-declarations → 200 avec type déclaré")
    void create_success_withType() throws Exception {
        when(debDeclarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/deb-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"declarationType":"DEB_INTRODUCTION","period":"2026-08",
                     "partnerCountry":"DE","natureOfTransaction":"11","modeOfTransport":"3",
                     "netMass":120,"statisticalValue":5000,"hsCode8":"85235110",
                     "goodsDescription":"Composants électroniques"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode8").value("85235110"));
    }

    @Test
    @DisplayName("POST /v1/deb-declarations → 200 sans type déclaré")
    void create_success_withoutType() throws Exception {
        when(debDeclarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/deb-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"period\":\"2026-08\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/deb-declarations → 400 si le service refuse")
    void create_rejected() throws Exception {
        when(debDeclarationService.create(any()))
            .thenThrow(new IllegalArgumentException("Entreprise introuvable"));

        mockMvc.perform(post("/v1/deb-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"period\":\"2026-08\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Entreprise introuvable"));
    }

    @Test
    @DisplayName("POST /v1/deb-declarations → 400 si type de déclaration invalide")
    void create_invalidDeclarationType() throws Exception {
        mockMvc.perform(post("/v1/deb-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationType\":\"NOT_A_TYPE\",\"period\":\"2026-08\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id} → 200 avec tous les champs renseignés")
    void update_success_allFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/deb-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"declarationType":"DEB_EXPEDITION","period":"2026-09",
                     "partnerCountry":"IT","natureOfTransaction":"21","modeOfTransport":"1",
                     "netMass":80,"statisticalValue":3000,"hsCode8":"84821010",
                     "goodsDescription":"Pièces mécaniques"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id} → 200 sans aucun champ (corps vide)")
    void update_success_emptyBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/deb-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id} → 400 si introuvable ou non modifiable")
    void update_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.update(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée"));

        mockMvc.perform(put("/v1/deb-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seule une déclaration en brouillon peut être modifiée"));
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id} → 400 si type de déclaration invalide")
    void update_invalidDeclarationType() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/v1/deb-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationType\":\"NOT_A_TYPE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id}/status → 200")
    void updateStatus_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.updateStatus(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/deb-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VALIDATED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id}/status → 400 si transition invalide")
    void updateStatus_invalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.updateStatus(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Transition invalide : DRAFT → SUBMITTED"));

        mockMvc.perform(put("/v1/deb-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUBMITTED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Transition invalide : DRAFT → SUBMITTED"));
    }

    @Test
    @DisplayName("PUT /v1/deb-declarations/{id}/status → 400 si statut invalide (non énuméré)")
    void updateStatus_invalidEnumValue() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/v1/deb-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOT_A_STATUS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /v1/deb-declarations/{id} → 200")
    void delete_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/deb-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Déclaration DEB supprimée"));
    }

    @Test
    @DisplayName("DELETE /v1/deb-declarations/{id} → 400 si introuvable ou non supprimable")
    void delete_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées"))
            .when(debDeclarationService).delete(id);

        mockMvc.perform(delete("/v1/deb-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seules les déclarations en DRAFT peuvent être supprimées"));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/stats → 200")
    void stats() throws Exception {
        when(debDeclarationService.getStats()).thenReturn(java.util.Map.of("total", 5));

        mockMvc.perform(get("/v1/deb-declarations/stats").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id}/pdf → 200")
    void exportPdf_success() throws Exception {
        UUID id = UUID.randomUUID();
        DebDeclaration d = sample();
        when(debDeclarationService.getById(id)).thenReturn(d);
        when(documentExportService.generateDebPdf(d)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/v1/deb-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id}/pdf → 400 si introuvable")
    void exportPdf_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("Déclaration DEB introuvable"));

        mockMvc.perform(get("/v1/deb-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id}/validate → 200 sans alerte")
    void validate_noAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateDeb(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/deb-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id}/validate → 200 avec alertes")
    void validate_withAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateDeb(any()))
            .thenReturn(List.of(new DeclarationValidationService.Alert("WARNING", "MISSING_HS", "HS code manquant")));

        mockMvc.perform(get("/v1/deb-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/{id}/validate → 400 si introuvable")
    void validate_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(debDeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("Déclaration DEB introuvable"));

        mockMvc.perform(get("/v1/deb-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/by-period/{period} → 200")
    void getByPeriod_success() throws Exception {
        when(debDeclarationService.getByPeriod("2026-08")).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/deb-declarations/by-period/2026-08").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].period").value("2026-08"));
    }

    @Test
    @DisplayName("GET /v1/deb-declarations/by-period/{period} → 400 si le service refuse")
    void getByPeriod_rejected() throws Exception {
        when(debDeclarationService.getByPeriod("bad-period"))
            .thenThrow(new IllegalArgumentException("Période invalide"));

        mockMvc.perform(get("/v1/deb-declarations/by-period/bad-period").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Période invalide"));
    }
}
