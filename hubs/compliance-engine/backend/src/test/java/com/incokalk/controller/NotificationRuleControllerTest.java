package com.incokalk.controller;

import com.incokalk.dto.notification.NaturalLanguageRuleTranslationResult;
import com.incokalk.model.NotificationRule;
import com.incokalk.service.NotificationService;
import com.incokalk.service.rules.NlRuleTranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seul /translate est couvert ici : createRule/updateRule/deleteRule/test
 *  n'avaient aucun test avant cette passe -- hors scope de l'ajout. */
class NotificationRuleControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private NlRuleTranslationService nlRuleTranslationService;

    @Test
    @DisplayName("POST /v1/notification-rules/translate → délègue au service de traduction")
    void translateRule_success() throws Exception {
        NotificationRule draft = NotificationRule.builder()
                .id(UUID.randomUUID()).name("Alerte retard").eventType("SHIPMENT_STATUS_CHANGE")
                .isActive(false).build();

        when(nlRuleTranslationService.translate(eq("notifie-moi en cas de retard"), any(), any()))
                .thenReturn(NaturalLanguageRuleTranslationResult.ok(draft));

        mockMvc.perform(post("/v1/notification-rules/translate")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"notifie-moi en cas de retard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rule.active").value(false));
    }

    @Test
    @DisplayName("POST /v1/notification-rules/translate → échec du service renvoyé tel quel")
    void translateRule_failure() throws Exception {
        when(nlRuleTranslationService.translate(any(), any(), any()))
                .thenReturn(NaturalLanguageRuleTranslationResult.fail("non configurée"));

        mockMvc.perform(post("/v1/notification-rules/translate")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"une description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("non configurée"));
    }

    @Test
    @DisplayName("POST /v1/notification-rules/translate → description vide rejetée (400)")
    void translateRule_blankDescription_rejected() throws Exception {
        mockMvc.perform(post("/v1/notification-rules/translate")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
