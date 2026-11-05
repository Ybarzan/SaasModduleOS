package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le tableau de bord et les KPI sont mis en cache par société. Sans invalidation,
 * une saisie n'apparaissait qu'à l'expiration du cache (5 min avec Redis en
 * production, jamais avec le cache mémoire), et le tableau de bord semblait figé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KpiCacheInvalidationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String register(String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + name.toLowerCase() + "-" + System.nanoTime()
                                + "@test.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private int fleetSize(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("fleetSize").asInt();
    }

    private void createTruck(String token, String registration) throws Exception {
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"" + registration + "\",\"brand\":\"Renault\",\"model\":\"T\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"active\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void aSuccessfulWrite_refreshesTheDashboard_ofThatCompanyOnly() throws Exception {
        String a = register("CacheA");
        String b = register("CacheB");
        assertEquals(0, fleetSize(a));
        assertEquals(0, fleetSize(b)); // les deux résumés sont maintenant en cache

        createTruck(a, "CA-001-AA");

        assertEquals(1, fleetSize(a), "le tableau de bord doit refléter la saisie tout de suite");
        assertEquals(0, fleetSize(b));
    }

    @Test
    void anIngestedGpsPosition_refreshesTheDashboard() throws Exception {
        String a = register("CacheD");
        createTruck(a, "CD-001-AA");
        MvcResult cfg = mvc.perform(post("/api/integrations")
                        .header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GPS_WEBFLEET\",\"baseUrl\":\"http://127.0.0.1:1\","
                                + "\"apiKey\":\"cle\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        String webhookKey = objectMapper.readTree(cfg.getResponse().getContentAsString()).get("webhookKey").asText();
        assertEquals(0, inService(a)); // résumé en cache

        // Push du fournisseur : pas de session utilisateur, la société vient de la clé.
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", webhookKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"registration\":\"CD-001-AA\",\"latitude\":45.7,"
                                + "\"longitude\":4.8,\"speedKph\":60,\"timestamp\":\"2026-11-05T10:00:00\"}]}"))
                .andExpect(status().isOk());

        assertEquals(1, inService(a), "le véhicule en roulage doit apparaître sans attendre l'expiration du cache");
    }

    private int inService(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("vehiclesInService").asInt();
    }

    @Test
    void aRejectedWrite_doesNotNeedToInvalidate_butTheDashboardStaysCorrect() throws Exception {
        String a = register("CacheC");
        createTruck(a, "CC-001-AA");
        assertEquals(1, fleetSize(a));
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"CC-001-AA\",\"brand\":\"R\",\"model\":\"T\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals(1, fleetSize(a));
    }
}
