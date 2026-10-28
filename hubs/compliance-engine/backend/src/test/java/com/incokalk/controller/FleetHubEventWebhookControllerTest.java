package com.incokalk.controller;

import com.incokalk.service.integration.fleethub.FleetHubWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/webhooks/fleethub/shipment-events est accessible sans JWT
 * (permitAll, voir SecurityConfig "/v1/webhooks/**") mais s'authentifie via
 * X-FleetHub-Webhook-Key -- même principe que StripeWebhookController
 * (secret dédié) et InternalDashboardController de logistix (clé
 * machine-à-machine).
 */
class FleetHubEventWebhookControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FleetHubWebhookService fleetHubWebhookService;

    private static final String BODY = """
            {"eventType":"SHIPMENT_CREATED","shipmentId":"%s","orderNumber":"FH-42"}
            """.formatted(UUID.randomUUID());

    @Test
    @DisplayName("sans en-tête X-FleetHub-Webhook-Key → 401, aucun événement enregistré")
    void handleShipmentEvent_missingKey_unauthorized() throws Exception {
        when(fleetHubWebhookService.resolveCompanyId(null))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/v1/webhooks/fleethub/shipment-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(fleetHubWebhookService, org.mockito.Mockito.never()).recordEvent(any(), any());
    }

    @Test
    @DisplayName("clé invalide → 401, aucun événement enregistré")
    void handleShipmentEvent_invalidKey_unauthorized() throws Exception {
        when(fleetHubWebhookService.resolveCompanyId("clef-invalide"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/v1/webhooks/fleethub/shipment-events")
                        .header("X-FleetHub-Webhook-Key", "clef-invalide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(fleetHubWebhookService, org.mockito.Mockito.never()).recordEvent(any(), any());
    }

    @Test
    @DisplayName("clé valide → 202, événement transmis au service avec la société résolue")
    void handleShipmentEvent_validKey_accepted() throws Exception {
        when(fleetHubWebhookService.resolveCompanyId("clef-valide")).thenReturn(companyId);

        mockMvc.perform(post("/v1/webhooks/fleethub/shipment-events")
                        .header("X-FleetHub-Webhook-Key", "clef-valide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        verify(fleetHubWebhookService).recordEvent(eq(companyId), any());
    }

    @Test
    @DisplayName("aucun JWT requis -- route accessible sans Authorization")
    void handleShipmentEvent_noAuthorizationHeaderNeeded() throws Exception {
        when(fleetHubWebhookService.resolveCompanyId("clef-valide")).thenReturn(companyId);

        mockMvc.perform(post("/v1/webhooks/fleethub/shipment-events")
                        .header("X-FleetHub-Webhook-Key", "clef-valide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());
    }
}
