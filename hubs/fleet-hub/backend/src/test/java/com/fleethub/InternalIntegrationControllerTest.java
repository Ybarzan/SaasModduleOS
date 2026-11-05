package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partage opt-in d'expéditions vers compliance-engine / Logistix :
 * activation/désactivation, clé jamais exposée hors de la réponse
 * d'activation -- même principe que MarketplaceControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalIntegrationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "internal-integ-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Internal Integ Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        adminToken = objectMapper.readTree(registerRes.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void settings_defaultsToDisabled_withOneUnconfiguredLinePerHub() throws Exception {
        mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(false))
                .andExpect(jsonPath("$.keys.length()").value(4))
                .andExpect(jsonPath("$.keys[?(@.consumer == 'LEGACY')]").isEmpty())
                .andExpect(jsonPath("$.keys[?(@.configured == true)]").isEmpty());
    }

    private String generateKey(String consumer) throws Exception {
        MvcResult res = mvc.perform(post("/api/internal-integrations/keys/" + consumer)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("apiKey").asText();
    }

    @Test
    void generateKey_isShownOnce_andOnlyItsHashIsStored() throws Exception {
        mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(true));
        String key = generateKey("LOGISTIX");
        assertNotNull(key);
        assertTrue(key.length() >= 32, "La clé doit être suffisamment longue pour ne pas être devinable");

        // En base, seul le hash de la clé est stocké (voir ApiKeyHasher), jamais la clé en clair.
        Integer hashedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_api_key WHERE key_hash = ?", Integer.class, ApiKeyHasher.hash(key));
        assertThat(hashedCount).isEqualTo(1);
        Integer plaintextCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_api_key WHERE key_hash = ?", Integer.class, key);
        assertThat(plaintextCount).isEqualTo(0);

        MvcResult settings = mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[?(@.consumer == 'LOGISTIX')].configured").value(true))
                .andExpect(jsonPath("$.keys[?(@.consumer == 'LOGISTIX')].keyHint").value(key.substring(key.length() - 4)))
                .andReturn();
        assertTrue(!settings.getResponse().getContentAsString().contains(key), "les réglages ne renvoient jamais la clé");
    }

    @Test
    void disable_thenKeyIsRejected_enable_thenAcceptedAgain() throws Exception {
        mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String key = generateKey("COMPLIANCE_ENGINE");

        mvc.perform(post("/api/internal-integrations/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIntegrationEnabled").value(false));
        mvc.perform(get("/api/internal/shipments").header("X-Internal-Integration-Key", key))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/internal/shipments").header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk());
    }

    @Test
    void shipmentCreationSettings_defaultsToDisabled() throws Exception {
        mvc.perform(get("/api/internal-integrations/shipment-creation/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowExternalShipmentCreation").value(false));
    }

    @Test
    void enableShipmentCreation_thenSettingsReflectsIt() throws Exception {
        mvc.perform(post("/api/internal-integrations/shipment-creation/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowExternalShipmentCreation").value(true));

        mvc.perform(get("/api/internal-integrations/shipment-creation/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowExternalShipmentCreation").value(true));
    }

    @Test
    void disableShipmentCreation_afterEnable_thenSettingsReflectsIt() throws Exception {
        mvc.perform(post("/api/internal-integrations/shipment-creation/enable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/internal-integrations/shipment-creation/disable").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowExternalShipmentCreation").value(false));
    }
}
