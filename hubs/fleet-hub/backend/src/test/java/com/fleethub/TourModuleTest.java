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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module Tournées de bout en bout : sites, composition, optimisation,
 * exécution avec preuves de passage, indicateurs, traçabilité, isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TourModuleTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String register(String company, String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\",\"fleetProfile\":\"COLLECTE_SANTE\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder req, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long site(String token, String name, String kind, double lat, double lon, String extra) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"latitude\":" + lat + ",\"longitude\":" + lon
                + (extra != null ? "," + extra : "") + "}";
        return json(call(token, post("/api/sites"), body).andExpect(status().isOk())).get("id").asLong();
    }

    @Test
    void fullTourLifecycle() throws Exception {
        String token = register("TEST-Bio Coursiers", "bio@test.fr");
        long depot = site(token, "Dépôt Lyon", "DEPOT", 45.7600, 4.8350, null);
        long pharmaFar = site(token, "Pharmacie Est", "PHARMACIE", 45.7600, 4.9500, null);
        long pharmaNear = site(token, "Pharmacie Centre", "PHARMACIE", 45.7600, 4.8600,
                "\"openingFrom\":\"08:00\",\"openingTo\":\"12:00\",\"serviceMinutes\":7");
        long lab = site(token, "Laboratoire Biolab", "LABORATOIRE", 45.7600, 4.9000, null);

        String today = LocalDate.now().toString();
        JsonNode tour = json(call(token, post("/api/tours"),
                "{\"name\":\"Collecte matin\",\"date\":\"" + today + "\",\"depotId\":" + depot + ",\"plannedStart\":\"07:30\"}")
                .andExpect(status().isOk()));
        long tourId = tour.get("id").asLong();

        // Arrêts ajoutés dans le désordre géographique
        call(token, post("/api/tours/" + tourId + "/stops"), "{\"siteId\":" + pharmaFar + ",\"type\":\"COLLECTE\"}")
                .andExpect(status().isOk());
        call(token, post("/api/tours/" + tourId + "/stops"),
                "{\"siteId\":" + pharmaNear + ",\"type\":\"COLLECTE\",\"temperatureMinCelsius\":2,\"temperatureMaxCelsius\":8}")
                .andExpect(status().isOk())
                // Créneau et durée repris du site
                .andExpect(jsonPath("$.stops[1].windowStart").value("08:00:00"))
                .andExpect(jsonPath("$.stops[1].serviceMinutes").value(7));
        JsonNode beforeOpt = json(call(token, post("/api/tours/" + tourId + "/stops"),
                "{\"siteId\":" + lab + ",\"type\":\"LIVRAISON\"}").andExpect(status().isOk()));
        assertEquals(3, beforeOpt.get("stopsTotal").asInt());
        double kmBefore = beforeOpt.get("plannedDistanceKm").asDouble();

        JsonNode optimized = json(call(token, post("/api/tours/" + tourId + "/optimize"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizedAt").isNotEmpty()));
        assertTrue(optimized.get("plannedDistanceKm").asDouble() <= kmBefore);
        assertEquals("Pharmacie Centre", optimized.get("stops").get(0).get("siteName").asText());
        assertEquals("Laboratoire Biolab", optimized.get("stops").get(1).get("siteName").asText());
        assertEquals("Pharmacie Est", optimized.get("stops").get(2).get("siteName").asText());

        long firstStop = optimized.get("stops").get(0).get("id").asLong();
        long secondStop = optimized.get("stops").get(1).get("id").asLong();
        long thirdStop = optimized.get("stops").get(2).get("id").asLong();

        call(token, post("/api/tours/" + tourId + "/start"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EN_COURS"));

        // Échec sans motif refusé
        call(token, post("/api/tours/" + tourId + "/stops/" + secondStop + "/complete"), "{\"status\":\"ECHEC\"}")
                .andExpect(status().isBadRequest());

        // Collecte réfrigérée relevée à 11 °C : rupture de la chaîne du froid
        call(token, post("/api/tours/" + tourId + "/stops/" + firstStop + "/complete"),
                "{\"status\":\"FAIT\",\"signedBy\":\"Mme Durand\",\"sampleCount\":4,\"temperatureCelsius\":11,"
                        + "\"scannedCodes\":\"S-001,S-002,S-003,S-004\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("FAIT"))
                .andExpect(jsonPath("$.stops[0].temperatureExcursion").value(true));
        call(token, post("/api/tours/" + tourId + "/stops/" + secondStop + "/complete"),
                "{\"status\":\"ECHEC\",\"failureReason\":\"Site fermé\"}")
                .andExpect(status().isOk());
        call(token, post("/api/tours/" + tourId + "/stops/" + thirdStop + "/complete"),
                "{\"status\":\"FAIT\",\"signedBy\":\"=cmd|calc\",\"parcelCount\":2}")
                .andExpect(status().isOk())
                // Tous les arrêts clôturés : la tournée se termine
                .andExpect(jsonPath("$.status").value("TERMINEE"));

        // Une tournée terminée n'est plus modifiable
        call(token, post("/api/tours/" + tourId + "/optimize"), null).andExpect(status().isConflict());

        call(token, get("/api/tours/stats?from=" + today + "&to=" + today), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tours").value(1))
                .andExpect(jsonPath("$.stopsDone").value(2))
                .andExpect(jsonPath("$.stopsFailed").value(1))
                .andExpect(jsonPath("$.samplesCollected").value(4))
                .andExpect(jsonPath("$.temperatureExcursions").value(1))
                .andExpect(jsonPath("$.failureReasons['Site fermé']").value(1));

        String csv = call(token, get("/api/tours/traceability.csv?from=" + today + "&to=" + today), null)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Mme Durand"));
        assertTrue(csv.contains("S-001,S-002,S-003,S-004"));
        assertTrue(csv.contains("'=cmd|calc"), "Les formules doivent être neutralisées dans le CSV");
        assertEquals(4, csv.strip().split("\n").length, "En-tête + 3 passages");

        // Site utilisé : suppression refusée (traçabilité)
        call(token, delete("/api/sites/" + pharmaNear), null).andExpect(status().isConflict());

        // Duplication pour le lendemain : arrêts copiés, statuts remis à zéro
        String tomorrow = LocalDate.now().plusDays(1).toString();
        call(token, post("/api/tours/" + tourId + "/duplicate?date=" + tomorrow), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(tomorrow))
                .andExpect(jsonPath("$.status").value("PLANIFIEE"))
                .andExpect(jsonPath("$.stopsTotal").value(3))
                .andExpect(jsonPath("$.stopsDone").value(0));
    }

    @Test
    void reorder_requiresAllStops() throws Exception {
        String token = register("TEST-Messagerie Ordre", "ordre@test.fr");
        long a = site(token, "Client A", "CLIENT", 48.85, 2.35, null);
        long b = site(token, "Client B", "CLIENT", 48.86, 2.36, null);
        long tourId = json(call(token, post("/api/tours"),
                "{\"name\":\"T1\",\"date\":\"" + LocalDate.now() + "\"}").andExpect(status().isOk())).get("id").asLong();
        call(token, post("/api/tours/" + tourId + "/stops"), "{\"siteId\":" + a + "}").andExpect(status().isOk());
        JsonNode t = json(call(token, post("/api/tours/" + tourId + "/stops"), "{\"siteId\":" + b + "}"));
        long s1 = t.get("stops").get(0).get("id").asLong();
        long s2 = t.get("stops").get(1).get("id").asLong();

        call(token, put("/api/tours/" + tourId + "/order"), "{\"stopIds\":[" + s2 + "]}")
                .andExpect(status().isBadRequest());
        call(token, put("/api/tours/" + tourId + "/order"), "{\"stopIds\":[" + s2 + "," + s1 + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].siteName").value("Client B"))
                .andExpect(jsonPath("$.stops[0].sequence").value(1));
    }

    @Test
    void toursAndSites_areIsolatedPerCompany() throws Exception {
        String tokenA = register("TEST-Isolation A", "iso-a@test.fr");
        String tokenB = register("TEST-Isolation B", "iso-b@test.fr");
        long siteA = site(tokenA, "Site A", "CLIENT", 45.0, 5.0, null);
        long tourA = json(call(tokenA, post("/api/tours"),
                "{\"name\":\"Tournée A\",\"date\":\"" + LocalDate.now() + "\"}")).get("id").asLong();

        call(tokenB, get("/api/sites/" + siteA), null).andExpect(status().isNotFound());
        call(tokenB, get("/api/tours/" + tourA), null).andExpect(status().isNotFound());
        // B ne peut pas utiliser le site de A dans sa propre tournée
        long tourB = json(call(tokenB, post("/api/tours"),
                "{\"name\":\"Tournée B\",\"date\":\"" + LocalDate.now() + "\"}")).get("id").asLong();
        call(tokenB, post("/api/tours/" + tourB + "/stops"), "{\"siteId\":" + siteA + "}")
                .andExpect(status().isNotFound());
        call(tokenB, get("/api/tours"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tournée B"));
    }

    @Test
    void site_validation() throws Exception {
        String token = register("TEST-Validation", "valid@test.fr");
        call(token, post("/api/sites"), "{\"name\":\"X\",\"kind\":\"CLIENT\",\"latitude\":45.0}")
                .andExpect(status().isBadRequest());
        call(token, post("/api/sites"), "{\"name\":\"X\",\"kind\":\"FUSEE\"}")
                .andExpect(status().isBadRequest());
        call(token, post("/api/sites"),
                "{\"name\":\"X\",\"kind\":\"CLIENT\",\"openingFrom\":\"18:00\",\"openingTo\":\"08:00\"}")
                .andExpect(status().isBadRequest());
    }
}
