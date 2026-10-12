package com.praxio;

import com.praxio.repository.DashboardConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Praxio n'a pas de comptes utilisateurs -- Basic Auth avec un unique mot de
 * passe administrateur (praxio.admin-password, "admin" par défaut en test)
 * protège tout /api/dashboard/**. Praxio n'étant pas multi-tenant, la ligne
 * de configuration singleton est partagée entre tous les tests de cette
 * classe (même contexte Spring, même base H2) : on la réinitialise avant
 * chaque test pour rester indépendant de l'ordre d'exécution.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DashboardConfigRepository dashboardConfigRepository;

    @BeforeEach
    void resetSingletonConfig() {
        dashboardConfigRepository.deleteAll();
    }

    @Test
    void getDashboard_withoutCredentials_returns401() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboard_withWrongPassword_returns401() throws Exception {
        mvc.perform(get("/api/dashboard").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboard_withValidCredentials_returnsAllHubsNotConfigured() throws Exception {
        mvc.perform(get("/api/dashboard").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceEngine.status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.fleetHub.status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.logistix.status").value("NOT_CONFIGURED"));
    }

    @Test
    void getKeysStatus_withValidCredentials_defaultsToUnconfigured() throws Exception {
        mvc.perform(get("/api/dashboard/keys").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceEngineConfigured").value(false))
                .andExpect(jsonPath("$.fleetHubConfigured").value(false))
                .andExpect(jsonPath("$.logistixConfigured").value(false));
    }

    @Test
    void putKeys_withoutCredentials_returns401() throws Exception {
        mvc.perform(put("/api/dashboard/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fleetHubApiKey\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putKeys_thenGetKeysStatus_reflectsUpdate() throws Exception {
        mvc.perform(put("/api/dashboard/keys").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fleetHubApiKey\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetHubConfigured").value(true))
                .andExpect(jsonPath("$.complianceEngineConfigured").value(false));

        mvc.perform(get("/api/dashboard/keys").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetHubConfigured").value(true));
    }
}
