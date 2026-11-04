package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
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
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentification des autres hubs sur /api/internal/** (InternalApiKeyFilter) :
 * une clé par hub avec ses seuls droits, rotation et révocation, clé historique
 * conservée, gel d'abonnement appliqué, JWT du personnel refusé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalApiKeyFilterTest {

    private static final String HEADER = "X-Internal-Integration-Key";
    private static final String SHIPMENT_BODY = "{\"shipperName\":\"Labo\",\"consigneeName\":\"Plateau\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private long companyId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "filter-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Filter\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        token = body.get("token").asText();
        companyId = body.get("companyId").asLong();
        admin(post("/api/internal-integrations/enable"));
        admin(post("/api/internal-integrations/shipment-creation/enable"));
    }

    private JsonNode admin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        MvcResult res = mvc.perform(req.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String key(String consumer) throws Exception {
        return admin(post("/api/internal-integrations/keys/" + consumer)).get("apiKey").asText();
    }

    private void expect(String key, String method, String url, ResultMatcher expected) throws Exception {
        var req = "POST".equals(method)
                ? post(url).contentType(MediaType.APPLICATION_JSON).content(SHIPMENT_BODY)
                : get(url);
        mvc.perform(req.header(HEADER, key)).andExpect(expected);
    }

    // ---- Droits par hub ----

    @Test
    void logistixAndFinance_canOnlyReadShipments() throws Exception {
        for (String consumer : new String[]{"LOGISTIX", "FINANCE"}) {
            String k = key(consumer);
            expect(k, "GET", "/api/internal/shipments", status().isOk());
            expect(k, "POST", "/api/internal/shipments", status().isForbidden());
            expect(k, "GET", "/api/internal/dashboard-summary", status().isForbidden());
            expect(k, "GET", "/api/internal/billing-summary", status().isForbidden());
        }
    }

    @Test
    void portal_canReadSummaries_butNoShipments() throws Exception {
        String k = key("PORTAL");
        expect(k, "GET", "/api/internal/dashboard-summary", status().isOk());
        expect(k, "GET", "/api/internal/billing-summary", status().isOk());
        expect(k, "GET", "/api/internal/shipments", status().isForbidden());
        expect(k, "POST", "/api/internal/shipments", status().isForbidden());
    }

    @Test
    void complianceEngine_readsCreatesAndSeesDashboard_butNotBilling() throws Exception {
        String k = key("COMPLIANCE_ENGINE");
        expect(k, "GET", "/api/internal/shipments", status().isOk());
        expect(k, "POST", "/api/internal/shipments", status().isCreated());
        expect(k, "GET", "/api/internal/dashboard-summary", status().isOk());
        expect(k, "GET", "/api/internal/billing-summary", status().isForbidden());
    }

    @Test
    void unknownInternalRoute_isNotServed() throws Exception {
        expect(key("COMPLIANCE_ENGINE"), "GET", "/api/internal/anything-else", status().isNotFound());
    }

    // ---- Rotation, révocation, clé historique ----

    @Test
    void rotation_invalidatesThePreviousKeyImmediately() throws Exception {
        String first = key("LOGISTIX");
        String second = key("LOGISTIX");
        expect(first, "GET", "/api/internal/shipments", status().isUnauthorized());
        expect(second, "GET", "/api/internal/shipments", status().isOk());
    }

    @Test
    void revocation_rejectsTheKey_withoutTouchingOtherHubs() throws Exception {
        String logistix = key("LOGISTIX");
        String finance = key("FINANCE");
        admin(delete("/api/internal-integrations/keys/LOGISTIX"));
        expect(logistix, "GET", "/api/internal/shipments", status().isUnauthorized());
        expect(finance, "GET", "/api/internal/shipments", status().isOk());
    }

    @Test
    void legacySharedKey_keepsAllRights_untilRevoked_andCannotBeRegenerated() throws Exception {
        // Ce que produit la migration V24 pour une société qui utilisait déjà la clé unique.
        String legacy = "0123456789abcdef0123456789abcdef";
        jdbcTemplate.update("INSERT INTO integration_api_key (company_id, consumer, key_hash, created_at) "
                + "VALUES (?, 'LEGACY', ?, ?)", companyId, ApiKeyHasher.hash(legacy), LocalDateTime.now());

        expect(legacy, "GET", "/api/internal/shipments", status().isOk());
        expect(legacy, "GET", "/api/internal/billing-summary", status().isOk());
        mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.keys[?(@.consumer == 'LEGACY')].configured").value(true));

        mvc.perform(post("/api/internal-integrations/keys/LEGACY").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        admin(delete("/api/internal-integrations/keys/LEGACY"));
        expect(legacy, "GET", "/api/internal/shipments", status().isUnauthorized());
    }

    @Test
    void unknownConsumer_returns404() throws Exception {
        mvc.perform(post("/api/internal-integrations/keys/ERP").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---- Gel d'abonnement et JWT ----

    @Test
    void frozenSubscription_alsoFreezesWhatOtherHubsSee() throws Exception {
        String k = key("COMPLIANCE_ENGINE");
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setTrialEndsAt(LocalDateTime.now().minusDays(1));
        companyRepository.save(company);

        expect(k, "GET", "/api/internal/shipments", status().isPaymentRequired());
        expect(k, "POST", "/api/internal/shipments", status().isPaymentRequired());
    }

    @Test
    void staffJwt_isNotEnoughForInternalRoutes() throws Exception {
        mvc.perform(get("/api/internal/shipments").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lastUsedAt_isRecorded() throws Exception {
        String k = key("LOGISTIX");
        expect(k, "GET", "/api/internal/shipments", status().isOk());
        mvc.perform(get("/api/internal-integrations/settings").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.keys[?(@.consumer == 'LOGISTIX')].lastUsedAt").isNotEmpty());
    }
}
