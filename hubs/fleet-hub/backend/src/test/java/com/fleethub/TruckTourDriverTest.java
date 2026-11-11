package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un véhicule de tournée n'a pas de chauffeur attitré : la liste des véhicules doit
 * montrer le chauffeur de sa tournée du jour au lieu de « Non affecté ».
 */
@SpringBootTest
@AutoConfigureMockMvc
class TruckTourDriverTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listShowsTodaysTourDriver_forVehiclesWithoutStandingAssignment() throws Exception {
        String token = objectMapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        JsonNode trucks = objectMapper.readTree(mvc.perform(get("/api/trucks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode van = null;
        JsonNode tractor = null;
        for (JsonNode t : trucks) {
            if ("GV-201-LY".equals(t.get("registration").asText())) van = t;
            if ("GT-123-AB".equals(t.get("registration").asText())) tractor = t;
        }
        assertNotNull(van, "camionnette de démo absente");
        assertNotNull(tractor, "tracteur de démo absent");

        // Camionnette : pas d'affectation permanente, mais une tournée aujourd'hui
        assertTrue(van.get("assignmentId").isNull());
        assertEquals("Sophie Lambert", van.get("tourDriverName").asText());

        // Tracteur : chauffeur attitré, pas de tournée
        assertEquals("Jean Martin", tractor.get("driverName").asText());
        assertTrue(tractor.get("tourDriverName").isNull());
    }
}
