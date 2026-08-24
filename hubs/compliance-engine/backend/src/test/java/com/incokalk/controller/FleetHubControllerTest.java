package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.FleetHubConfig;
import com.incokalk.service.fleethub.FleetHubConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/fleethub/** applique effectivement le contrôle de rôle
 * (RolesAllowedAspect, pas @PreAuthorize -- même piège déjà rencontré, voir
 * CarrierBookingControllerTest). Créer/modifier/supprimer une configuration
 * réservé à OWNER/ADMIN (identifiants d'un compte de service externe) ; lister
 * et tester la connexion ouverts à MANAGER aussi.
 */
class FleetHubControllerTest extends ControllerTestBase {

    @MockBean
    private FleetHubConfigService configService;

    private FleetHubConfig config(UUID id) {
        return FleetHubConfig.builder().id(id).name("Flotte principale").baseUrl("https://fleethub.example.com")
                .username("integration@acme.io").isActive(true).build();
    }

    @Test
    @DisplayName("GET /v1/fleethub → 403 pour un rôle USER")
    void listConfigs_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/fleethub").header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/fleethub → 200 pour un rôle MANAGER")
    void listConfigs_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(configService.listConfigs(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/fleethub").header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/fleethub → 403 pour un rôle MANAGER (réservé OWNER/ADMIN)")
    void createConfig_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/fleethub")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flotte principale","baseUrl":"https://fleethub.example.com","username":"integration@acme.io","password":"secret","isActive":true}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/fleethub → 200 pour un rôle ADMIN")
    void createConfig_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        UUID id = UUID.randomUUID();
        when(configService.createConfig(any(), any())).thenReturn(config(id));

        mockMvc.perform(post("/v1/fleethub")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flotte principale","baseUrl":"https://fleethub.example.com","username":"integration@acme.io","password":"secret","isActive":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flotte principale"));
    }

    @Test
    @DisplayName("DELETE /v1/fleethub/{id} → 403 pour un rôle MANAGER")
    void deleteConfig_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/fleethub/" + id).header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /v1/fleethub/{id} → 204 pour un rôle ADMIN")
    void deleteConfig_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/fleethub/" + id).header("Authorization", authHeader()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /v1/fleethub/{id}/test → 403 pour un rôle USER")
    void testConnection_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/v1/fleethub/" + id + "/test").header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/fleethub/{id}/test → 200 pour un rôle MANAGER")
    void testConnection_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        UUID id = UUID.randomUUID();
        when(configService.testConnection(any(), any())).thenReturn(true);

        mockMvc.perform(post("/v1/fleethub/" + id + "/test").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
