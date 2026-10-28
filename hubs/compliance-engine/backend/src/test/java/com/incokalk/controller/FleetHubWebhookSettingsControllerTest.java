package com.incokalk.controller;

import com.incokalk.controller.integration.FleetHubWebhookSettingsController;
import com.incokalk.dto.integration.fleethub.FleetHubWebhookSettingsDto;
import com.incokalk.model.CompanyRole;
import com.incokalk.service.integration.fleethub.FleetHubWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que {@link FleetHubWebhookSettingsController} (activation de la
 * réception d'événements fleet-hub) est bien restreint à OWNER/ADMIN -- même
 * garde-fou que ApiKeyControllerTest pour /v1/api-keys.
 */
class FleetHubWebhookSettingsControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FleetHubWebhookService fleetHubWebhookService;

    @Test
    @DisplayName("GET /v1/fleethub-webhook/settings → 403 pour un rôle USER")
    void settings_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/fleethub-webhook/settings").header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/fleethub-webhook/settings → 200 pour OWNER")
    void settings_okForOwner() throws Exception {
        when(fleetHubWebhookService.settings(companyId)).thenReturn(new FleetHubWebhookSettingsDto(false, null));

        mockMvc.perform(get("/v1/fleethub-webhook/settings").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleethubWebhookEnabled").value(false))
                .andExpect(jsonPath("$.fleethubWebhookKey").doesNotExist());
    }

    @Test
    @DisplayName("POST /v1/fleethub-webhook/enable → 403 pour un rôle MANAGER")
    void enable_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/fleethub-webhook/enable").header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/fleethub-webhook/enable → 200 pour ADMIN, renvoie la clé en clair")
    void enable_okForAdmin_returnsPlainKey() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        when(fleetHubWebhookService.enable(companyId)).thenReturn(new FleetHubWebhookSettingsDto(true, "clef-en-clair"));

        mockMvc.perform(post("/v1/fleethub-webhook/enable").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleethubWebhookEnabled").value(true))
                .andExpect(jsonPath("$.fleethubWebhookKey").value("clef-en-clair"));
    }
}
