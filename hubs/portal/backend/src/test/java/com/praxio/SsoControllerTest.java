package com.praxio;

import com.praxio.model.SsoCredentials;
import com.praxio.repository.SsoCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void putCredentials_storesPasswordEncryptedAtRest() throws Exception {
        mvc.perform(put("/api/sso/credentials").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fleetHubEmail\":\"ops@acme.fr\",\"fleetHubPassword\":\"super-secret-pw\"}"))
                .andExpect(status().isOk());

        String rawColumnValue = jdbcTemplate.queryForObject(
                "SELECT fleet_hub_password FROM sso_credentials WHERE id = ?",
                String.class, SsoCredentials.SINGLETON_ID);
        assertThat(rawColumnValue).isNotEqualTo("super-secret-pw");
        assertThat(rawColumnValue).doesNotContain("super-secret-pw");

        SsoCredentials reloaded = ssoCredentialsRepository.findById(SsoCredentials.SINGLETON_ID).orElseThrow();
        assertThat(reloaded.getFleetHubPassword()).isEqualTo("super-secret-pw");
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
