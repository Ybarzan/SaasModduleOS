package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Truck;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/marketplace/vehicle-position (voir MarketplaceService#vehiclePosition) :
 * tunnel GPS scopé pour FleetMarket — un seul camion à la fois, jamais la
 * flotte entière, jamais un camion d'une autre société.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketplaceVehiclePositionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TruckRepository truckRepository;

    private String adminToken;
    private String registration;
    private String marketplaceKey;

    @BeforeEach
    void setUp() throws Exception {
        String email = "vehpos-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"VehPos Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        adminToken = objectMapper.readTree(registerRes.getResponse().getContentAsString()).get("token").asText();

        registration = "VP-" + System.nanoTime();
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"" + registration + "\",\"brand\":\"Volvo\",\"model\":\"FH16\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"capacityTons\":19.5,"
                                + "\"expectedConsumptionL100Km\":32.0,\"active\":true}"))
                .andExpect(status().isOk());

        MvcResult optInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        marketplaceKey = objectMapper.readTree(optInRes.getResponse().getContentAsString()).get("marketplaceApiKey").asText();
    }

    @Test
    void withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/api/marketplace/vehicle-position").param("registration", registration))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withWrongKey_returns401() throws Exception {
        mvc.perform(get("/api/marketplace/vehicle-position")
                        .header("X-Marketplace-Key", "clef-incorrecte")
                        .param("registration", registration))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRegistration_returns404() throws Exception {
        mvc.perform(get("/api/marketplace/vehicle-position")
                        .header("X-Marketplace-Key", marketplaceKey)
                        .param("registration", "N-EXISTE-PAS"))
                .andExpect(status().isNotFound());
    }

    @Test
    void truckWithNoGpsYet_returnsAvailableFalse() throws Exception {
        mvc.perform(get("/api/marketplace/vehicle-position")
                        .header("X-Marketplace-Key", marketplaceKey)
                        .param("registration", registration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.latitude").doesNotExist());
    }

    @Test
    void truckWithAKnownPosition_returnsItsRealCoordinates() throws Exception {
        Truck truck = truckRepository.findByRegistration(registration).orElseThrow();
        truck.setCurrentLatitude(45.75);
        truck.setCurrentLongitude(4.85);
        truck.setCurrentSpeedKph(87.0);
        truck.setLastGpsUpdate(LocalDateTime.now());
        truckRepository.save(truck);

        MvcResult res = mvc.perform(get("/api/marketplace/vehicle-position")
                        .header("X-Marketplace-Key", marketplaceKey)
                        .param("registration", registration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.latitude").value(45.75))
                .andExpect(jsonPath("$.longitude").value(4.85))
                .andExpect(jsonPath("$.speedKph").value(87.0))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertNotNull(body.get("lastGpsUpdate"));
    }

    @Test
    void aTruckBelongingToAnotherCompany_isNeverReturned() throws Exception {
        String otherEmail = "vehpos-other-" + System.nanoTime() + "@test.fr";
        MvcResult otherRegisterRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"VehPos Other\",\"firstName\":\"C\",\"lastName\":\"D\","
                                + "\"email\":\"" + otherEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String otherToken = objectMapper.readTree(otherRegisterRes.getResponse().getContentAsString()).get("token").asText();
        MvcResult otherOptInRes = mvc.perform(post("/api/marketplace/opt-in").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andReturn();
        String otherKey = objectMapper.readTree(otherOptInRes.getResponse().getContentAsString()).get("marketplaceApiKey").asText();

        // La société "Other" tente d'interroger le camion de "VehPos Test" avec sa propre clé valide.
        mvc.perform(get("/api/marketplace/vehicle-position")
                        .header("X-Marketplace-Key", otherKey)
                        .param("registration", registration))
                .andExpect(status().isNotFound());
    }
}
