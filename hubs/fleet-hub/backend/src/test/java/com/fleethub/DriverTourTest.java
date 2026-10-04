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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Application chauffeur : le chauffeur (rôle CHAUFFEUR, PIN) ne voit que ses
 * tournées, peut les exécuter (arrivée, preuve de passage) et n'accède jamais
 * aux tournées des autres ni au back-office.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DriverTourTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private long driverA;
    private long driverB;

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

    private long driver(String first) throws Exception {
        return json(call(adminToken, post("/api/drivers"),
                "{\"firstName\":\"" + first + "\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-DT-" + System.nanoTime()
                        + "\",\"phone\":\"0600000000\",\"active\":true}").andExpect(status().isOk())).get("id").asLong();
    }

    private String chauffeurToken(long driverId, String pin) throws Exception {
        call(adminToken, post("/api/drivers/" + driverId + "/pin"), "{\"pin\":\"" + pin + "\"}")
                .andExpect(status().isNoContent());
        return json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"chauffeur-" + driverId + "@pointage.internal\",\"password\":\"" + pin + "\"}"))
                .andExpect(status().isOk())).get("token").asText();
    }

    private long tourFor(Long driverId, String name) throws Exception {
        long site = json(call(adminToken, post("/api/sites"),
                "{\"name\":\"Pharmacie " + name + "\",\"kind\":\"PHARMACIE\",\"latitude\":45.76,\"longitude\":4.85}"))
                .get("id").asLong();
        long tour = json(call(adminToken, post("/api/tours"), "{\"name\":\"" + name + "\",\"date\":\"" + LocalDate.now()
                + "\"" + (driverId != null ? ",\"driverId\":" + driverId : "") + "}")).get("id").asLong();
        call(adminToken, post("/api/tours/" + tour + "/stops"),
                "{\"siteId\":" + site + ",\"type\":\"COLLECTE\",\"temperatureMinCelsius\":2,\"temperatureMaxCelsius\":8}")
                .andExpect(status().isOk());
        return tour;
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\":\"TEST-Coursiers\",\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"dt-"
                        + System.nanoTime() + "@test.fr\",\"password\":\"password123\",\"fleetProfile\":\"COLLECTE_SANTE\"}"))
                .andExpect(status().isCreated())).get("token").asText();
        driverA = driver("Alice");
        driverB = driver("Bruno");
    }

    @Test
    void driverSeesOnlyOwnTours_andExecutesThem() throws Exception {
        long tourA = tourFor(driverA, "Tournée Alice");
        long tourB = tourFor(driverB, "Tournée Bruno");
        tourFor(null, "Tournée non affectée");
        String tokenA = chauffeurToken(driverA, "1234");

        JsonNode mine = json(call(tokenA, get("/api/me/tours"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tournée Alice")));
        long stop = mine.get(0).get("stops").get(0).get("id").asLong();

        call(tokenA, post("/api/me/tours/" + tourA + "/start"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EN_COURS"));
        call(tokenA, post("/api/me/tours/" + tourA + "/stops/" + stop + "/arrive"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.stops[0].arrivedAt").isNotEmpty());
        call(tokenA, post("/api/me/tours/" + tourA + "/stops/" + stop + "/complete"),
                "{\"status\":\"FAIT\",\"signedBy\":\"Pharmacien\",\"sampleCount\":3,\"temperatureCelsius\":4.5,\"scannedCodes\":\"A1,A2,A3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINEE"))
                .andExpect(jsonPath("$.stops[0].sampleCount").value(3))
                .andExpect(jsonPath("$.stops[0].temperatureExcursion").value(false));

        // L'exploitant voit la preuve saisie par le chauffeur
        call(adminToken, get("/api/tours/" + tourA), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].signedBy").value("Pharmacien"));

        // La tournée d'un autre chauffeur reste invisible et intouchable
        call(tokenA, post("/api/me/tours/" + tourB + "/start"), null).andExpect(status().isNotFound());
    }

    @Test
    void chauffeur_cannotReachBackOffice() throws Exception {
        long tourA = tourFor(driverA, "Tournée Alice");
        String tokenA = chauffeurToken(driverA, "4321");
        call(tokenA, get("/api/tours"), null).andExpect(status().isForbidden());
        call(tokenA, get("/api/tours/" + tourA), null).andExpect(status().isForbidden());
        call(tokenA, get("/api/sites"), null).andExpect(status().isForbidden());
        call(tokenA, post("/api/tours/" + tourA + "/optimize"), null).andExpect(status().isForbidden());
    }

    @Test
    void backOfficeUser_cannotUseDriverApi() throws Exception {
        call(adminToken, get("/api/me/tours"), null).andExpect(status().isForbidden());
    }
}
