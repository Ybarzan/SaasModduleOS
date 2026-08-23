package com.incokalk.controller;

import com.incokalk.model.EmailIntake;
import com.incokalk.service.EmailIntakeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailIntakeControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailIntakeService emailIntakeService;

    @Test
    @DisplayName("GET /v1/email-intake/messages → historique des emails")
    void getHistory() throws Exception {
        EmailIntake intake = EmailIntake.builder()
                .id(UUID.randomUUID())
                .senderEmail("client@example.com")
                .subject("Demande de devis")
                .status(EmailIntake.IntakeStatus.PARSED)
                .receivedAt(LocalDateTime.now())
                .build();

        when(emailIntakeService.getIntakeHistory(companyId)).thenReturn(List.of(intake));

        mockMvc.perform(get("/v1/email-intake/messages")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderEmail").value("client@example.com"))
                .andExpect(jsonPath("$[0].subject").value("Demande de devis"));
    }

    @Test
    @DisplayName("GET /v1/email-intake/messages/pending → emails en attente")
    void getPending() throws Exception {
        EmailIntake intake = EmailIntake.builder()
                .id(UUID.randomUUID())
                .senderEmail("pending@example.com")
                .status(EmailIntake.IntakeStatus.PARSED)
                .build();

        when(emailIntakeService.getPendingIntakes()).thenReturn(List.of(intake));

        mockMvc.perform(get("/v1/email-intake/messages/pending")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderEmail").value("pending@example.com"));
    }

    @Test
    @DisplayName("GET /v1/email-intake/messages/{id} → détail d'un email")
    void getIntake() throws Exception {
        UUID intakeId = UUID.randomUUID();
        EmailIntake intake = EmailIntake.builder()
                .id(intakeId)
                .senderEmail("detail@example.com")
                .subject("Detail test")
                .build();

        when(emailIntakeService.getIntake(intakeId)).thenReturn(intake);

        mockMvc.perform(get("/v1/email-intake/messages/" + intakeId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(intakeId.toString()));
    }

    @Test
    @DisplayName("GET /v1/email-intake/messages/{id} → 404 si introuvable")
    void getIntake_notFound() throws Exception {
        UUID intakeId = UUID.randomUUID();
        when(emailIntakeService.getIntake(intakeId)).thenReturn(null);

        mockMvc.perform(get("/v1/email-intake/messages/" + intakeId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /v1/email-intake/messages/{id}/confirm → confirme et crée brouillon")
    void confirmIntake() throws Exception {
        UUID intakeId = UUID.randomUUID();
        EmailIntake confirmed = EmailIntake.builder()
                .id(intakeId)
                .status(EmailIntake.IntakeStatus.CONFIRMED)
                .matchedCompanyId(companyId)
                .build();

        when(emailIntakeService.confirmIntake(intakeId, companyId)).thenReturn(confirmed);

        mockMvc.perform(post("/v1/email-intake/messages/" + intakeId + "/confirm")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /v1/email-intake/messages/{id}/reject → rejette l'email")
    void rejectIntake() throws Exception {
        UUID intakeId = UUID.randomUUID();

        mockMvc.perform(post("/v1/email-intake/messages/" + intakeId + "/reject")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/email-intake/messages/stats → statistiques")
    void getStats() throws Exception {
        when(emailIntakeService.getStats()).thenReturn(Map.of(
                "total", 100L,
                "parsed", 80L,
                "shipmentCreated", 60L,
                "confirmed", 40L
        ));

        mockMvc.perform(get("/v1/email-intake/messages/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100))
                .andExpect(jsonPath("$.parsed").value(80));
    }
}