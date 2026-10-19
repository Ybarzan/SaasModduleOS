package com.logistix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistix.model.Company;
import com.logistix.repository.CompanyRepository;
import com.logistix.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partage opt-in d'un résumé d'activité vers le tableau de bord cross-hub de
 * Praxio : réservé ADMIN, clé jamais exposée hors de la réponse d'activation
 * -- même principe que CompanySettingsControllerTest (fleethub-key) et
 * fleet-hub's InternalIntegrationControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PraxioIntegrationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;

    private String adminToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginToken("admin@logistix.fr");
        managerToken = loginToken("manager@logistix.fr");
        // Chaque test appelle /enable sur la même société seedée (contexte Spring
        // mis en cache entre les méthodes de test) : repartir sans clé à chaque
        // fois, sinon un test exécuté après un autre tomberait sur le cas "clé déjà
        // existante" de enable() (qui ne renvoie plus la clé -- voir
        // PraxioIntegrationService), au lieu du cas "première activation" attendu ici.
        for (Company company : companyRepository.findAll()) {
            company.setPraxioIntegrationApiKey(null);
            company.setPraxioIntegrationEnabled(false);
            companyRepository.save(company);
        }
    }

    @Test
    void enable_generatesKey_settingsNeverExposesItAfterward() throws Exception {
        MvcResult enableRes = mvc.perform(post("/v1/praxio-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.praxioIntegrationEnabled").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(enableRes.getResponse().getContentAsString());
        String key = body.get("praxioIntegrationApiKey").asText();
        assertNotNull(key);
        assertTrue(key.length() >= 32, "La clé doit être suffisamment longue pour ne pas être devinable");

        // Le champ stocké ne contient que le hash de la clé renvoyée ci-dessus,
        // jamais la clé en clair elle-même -- voir ApiKeyHasher.
        Company stored = companyRepository.findAll().stream()
                .filter(c -> c.getPraxioIntegrationApiKey() != null)
                .findFirst().orElseThrow();
        assertThat(stored.getPraxioIntegrationApiKey()).isNotEqualTo(key);
        assertThat(stored.getPraxioIntegrationApiKey()).isEqualTo(ApiKeyHasher.hash(key));

        mvc.perform(get("/v1/praxio-integration/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.praxioIntegrationEnabled").value(true))
                .andExpect(jsonPath("$.praxioIntegrationApiKey").doesNotExist());
    }

    @Test
    void disable_thenKeyIsRejectedByInternalEndpoint() throws Exception {
        MvcResult enableRes = mvc.perform(post("/v1/praxio-integration/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(enableRes.getResponse().getContentAsString()).get("praxioIntegrationApiKey").asText();

        mvc.perform(post("/v1/praxio-integration/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.praxioIntegrationEnabled").value(false));

        mvc.perform(get("/v1/internal/dashboard-summary").header("X-Internal-Integration-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manager_cannotReadOrChangeSettings_returns403() throws Exception {
        mvc.perform(get("/v1/praxio-integration/settings").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/praxio-integration/enable").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    private String loginToken(String email) throws Exception {
        String body = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, Map.class).get("token").toString();
    }
}
