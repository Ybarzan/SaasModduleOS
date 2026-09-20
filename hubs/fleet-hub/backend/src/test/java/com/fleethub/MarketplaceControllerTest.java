package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partage opt-in de disponibilité vers FleetMarket : activation/désactivation,
 * clé jamais exposée hors de la réponse d'activation, et authentification
 * machine-à-machine de /availability par X-Marketplace-Key (pas un JWT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketplaceControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "marketplace-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Marketplace Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        adminToken = objectMapper.readTree(registerRes.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void settings_defaultsToOptedOut() throws Exception {
        mvc.perform(get("/api/marketplace/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplaceOptIn").value(false))
                .andExpect(jsonPath("$.marketplaceApiKey").doesNotExist());
    }

    @Test
    void availability_withoutOptIn_returns401() throws Exception {
        mvc.perform(get("/api/marketplace/availability").header("X-Marketplace-Key", "n-importe-quoi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void availability_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/api/marketplace/availability"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optIn_generatesKey_settingsNeverExposesItAfterward() throws Exception {
        MvcResult optInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplaceOptIn").value(true))
                .andReturn();
        JsonNode body = objectMapper.readTree(optInRes.getResponse().getContentAsString());
        String key = body.get("marketplaceApiKey").asText();
        assertNotNull(key);
        assertTrue(key.length() >= 32, "La clé doit être suffisamment longue pour ne pas être devinable");

        mvc.perform(get("/api/marketplace/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplaceOptIn").value(true))
                .andExpect(jsonPath("$.marketplaceApiKey").doesNotExist());
    }

    @Test
    void availability_withValidKey_returnsCompanyDataAndEmptyTruckList() throws Exception {
        MvcResult optInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(optInRes.getResponse().getContentAsString()).get("marketplaceApiKey").asText();

        mvc.perform(get("/api/marketplace/availability").header("X-Marketplace-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Marketplace Test"))
                .andExpect(jsonPath("$.trucksAvailable").isArray())
                .andExpect(jsonPath("$.trucksAvailable").isEmpty())
                .andExpect(jsonPath("$.complianceScore").doesNotExist());
    }

    @Test
    void availability_withWrongKey_returns401() throws Exception {
        mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/marketplace/availability").header("X-Marketplace-Key", "clef-incorrecte"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optOut_thenAvailability_returns401EvenWithPreviouslyValidKey() throws Exception {
        MvcResult optInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(optInRes.getResponse().getContentAsString()).get("marketplaceApiKey").asText();

        mvc.perform(post("/api/marketplace/opt-out").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplaceOptIn").value(false));

        mvc.perform(get("/api/marketplace/availability").header("X-Marketplace-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trucksAvailable_includesActiveTruckWithNoGpsStatus() throws Exception {
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"MK-" + System.nanoTime() + "\",\"brand\":\"Volvo\",\"model\":\"FH16\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"capacityTons\":19.5,"
                                + "\"expectedConsumptionL100Km\":32.0,\"active\":true}"))
                .andExpect(status().isOk());

        MvcResult optInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String key = objectMapper.readTree(optInRes.getResponse().getContentAsString()).get("marketplaceApiKey").asText();

        mvc.perform(get("/api/marketplace/availability").header("X-Marketplace-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trucksAvailable[0].capacityTons").value(19.5));
    }
}
