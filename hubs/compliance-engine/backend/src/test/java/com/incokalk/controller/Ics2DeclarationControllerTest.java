package com.incokalk.controller;

import com.incokalk.model.Ics2Declaration;
import com.incokalk.service.DeclarationValidationService;
import com.incokalk.service.DocumentExportService;
import com.incokalk.service.Ics2DeclarationService;
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

class Ics2DeclarationControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private Ics2DeclarationService ics2DeclarationService;
    @MockBean
    private DocumentExportService documentExportService;
    @MockBean
    private DeclarationValidationService validationService;

    private Ics2Declaration sample() {
        return Ics2Declaration.builder()
            .declarationNumber("ICS2-2026-001")
            .status(Ics2Declaration.Ics2Status.DRAFT)
            .senderEori("FR12345678900001")
            .receiverEori("US98765432100001")
            .vesselName("MSC AURORA")
            .voyageNumber("V123")
            .containerNumber("MSCU1234567")
            .hsCode6("851761")
            .goodsDescription("Téléphones portables")
            .grossWeight(BigDecimal.valueOf(120))
            .packagesCount(5)
            .build();
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations → 200 liste")
    void list_success() throws Exception {
        when(ics2DeclarationService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/ics2-declarations").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].declarationNumber").value("ICS2-2026-001"));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id} → 200")
    void getById_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id)).thenReturn(sample());

        mockMvc.perform(get("/v1/ics2-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode6").value("851761"));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id} → 400 si introuvable")
    void getById_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("Déclaration ICS2 introuvable"));

        mockMvc.perform(get("/v1/ics2-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Déclaration ICS2 introuvable"));
    }

    @Test
    @DisplayName("POST /v1/ics2-declarations → 200")
    void create_success() throws Exception {
        when(ics2DeclarationService.create(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/ics2-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"senderEori":"FR12345678900001","receiverEori":"US98765432100001",
                     "vesselName":"MSC AURORA","voyageNumber":"V123","containerNumber":"MSCU1234567",
                     "hsCode6":"851761","goodsDescription":"Téléphones portables",
                     "grossWeight":120,"packagesCount":5}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode6").value("851761"));
    }

    @Test
    @DisplayName("POST /v1/ics2-declarations → 400 si le service refuse")
    void create_rejected() throws Exception {
        when(ics2DeclarationService.create(any()))
            .thenThrow(new IllegalArgumentException("Entreprise introuvable"));

        mockMvc.perform(post("/v1/ics2-declarations")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senderEori\":\"FR12345678900001\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Entreprise introuvable"));
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id} → 200 avec tous les champs renseignés")
    void update_success_allFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/ics2-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"senderEori":"FR12345678900001","receiverEori":"US98765432100001",
                     "vesselName":"MSC AURORA","voyageNumber":"V123","containerNumber":"MSCU1234567",
                     "hsCode6":"851761","goodsDescription":"Téléphones portables",
                     "grossWeight":120,"packagesCount":5}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id} → 200 sans aucun champ (corps vide)")
    void update_success_emptyBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/ics2-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id} → 400 si non modifiable")
    void update_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.update(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée"));

        mockMvc.perform(put("/v1/ics2-declarations/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seule une déclaration en brouillon peut être modifiée"));
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id}/status → 200")
    void updateStatus_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.updateStatus(eq(id), eq(Ics2Declaration.Ics2Status.SENT)))
            .thenReturn(sample());

        mockMvc.perform(put("/v1/ics2-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SENT\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id}/status → 400 si transition invalide (rejetée par le service)")
    void updateStatus_invalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.updateStatus(eq(id), eq(Ics2Declaration.Ics2Status.ACCEPTED)))
            .thenThrow(new IllegalArgumentException("Transition invalide : DRAFT → ACCEPTED"));

        mockMvc.perform(put("/v1/ics2-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Transition invalide : DRAFT → ACCEPTED"));
    }

    @Test
    @DisplayName("PUT /v1/ics2-declarations/{id}/status → 400 si statut inconnu (valueOf échoue)")
    void updateStatus_unknownStatusValue() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/v1/ics2-declarations/" + id + "/status")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOT_A_STATUS\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /v1/ics2-declarations/{id} → 200")
    void delete_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/ics2-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Déclaration ICS2 supprimée"));
    }

    @Test
    @DisplayName("DELETE /v1/ics2-declarations/{id} → 400 si introuvable ou non supprimable")
    void delete_rejected() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées"))
            .when(ics2DeclarationService).delete(id);

        mockMvc.perform(delete("/v1/ics2-declarations/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Seules les déclarations en DRAFT peuvent être supprimées"));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/stats → 200")
    void stats() throws Exception {
        when(ics2DeclarationService.getStats()).thenReturn(java.util.Map.of("DRAFT", 4));

        mockMvc.perform(get("/v1/ics2-declarations/stats").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.DRAFT").value(4));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id}/pdf → 200")
    void exportPdf_success() throws Exception {
        UUID id = UUID.randomUUID();
        Ics2Declaration d = sample();
        when(ics2DeclarationService.getById(id)).thenReturn(d);
        when(documentExportService.generateIcs2Pdf(d)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/v1/ics2-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id}/pdf → 400 si introuvable")
    void exportPdf_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/ics2-declarations/" + id + "/pdf").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id}/validate → 200 sans alerte")
    void validate_noAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateIcs2(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/ics2-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id}/validate → 200 avec alertes")
    void validate_withAlerts() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id)).thenReturn(sample());
        when(validationService.validateIcs2(any()))
            .thenReturn(List.of(new DeclarationValidationService.Alert("WARNING", "MISSING_HS", "HS code manquant")));

        mockMvc.perform(get("/v1/ics2-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    @DisplayName("GET /v1/ics2-declarations/{id}/validate → 400 si introuvable")
    void validate_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(ics2DeclarationService.getById(id))
            .thenThrow(new IllegalArgumentException("introuvable"));

        mockMvc.perform(get("/v1/ics2-declarations/" + id + "/validate").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }
}
