package com.praxio;

import com.praxio.repository.SsoCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Praxio n'a pas de comptes utilisateurs -- Basic Auth protège tout
 * /api/sso/**. Ligne de configuration singleton réinitialisée avant chaque
 * test pour rester indépendant de l'ordre d'exécution (voir DashboardControllerTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SsoControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SsoCredentialsRepository ssoCredentialsRepository;

    @BeforeEach
    void resetSingletonConfig() {
        ssoCredentialsRepository.deleteAll();
    }

    @Test
    void getCredentials_withoutCredentials_returns401() throws Exception {
        mvc.perform(get("/api/sso/credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void getCredentials_withValidCredentials_defaultsToUnconfigured() throws Exception {
        mvc.perform(get("/api/sso/credentials").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceEngineConfigured").value(false))
                .andExpect(jsonPath("$.fleetHubConfigured").value(false))
                .andExpect(jsonPath("$.logistixConfigured").value(false));
    }

    @Test
    void putCredentials_thenGetCredentials_reflectsUpdate() throws Exception {
        mvc.perform(put("/api/sso/credentials").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fleetHubEmail\":\"ops@acme.fr\",\"fleetHubPassword\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetHubConfigured").value(true))
                .andExpect(jsonPath("$.fleetHubEmail").value("ops@acme.fr"))
                .andExpect(jsonPath("$.complianceEngineConfigured").value(false));

        mvc.perform(get("/api/sso/credentials").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetHubConfigured").value(true));
    }

    @Test
    void launch_withoutCredentials_returns401() throws Exception {
        mvc.perform(post("/api/sso/launch/complianceEngine")).andExpect(status().isUnauthorized());
    }

    @Test
    void launch_unknownHub_returns400() throws Exception {
        mvc.perform(post("/api/sso/launch/nope").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void launch_noCredentialsConfiguredForHub_returns400() throws Exception {
        mvc.perform(post("/api/sso/launch/complianceEngine").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
    }
}
