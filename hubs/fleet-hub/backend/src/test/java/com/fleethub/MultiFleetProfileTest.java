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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Socle multi-flottes : profil de flotte par société, catégories VUL/VL,
 * tachygraphe optionnel et consommation de référence par défaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiFleetProfileTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode register(String company, String email, String profile) throws Exception {
        String profileJson = profile != null ? ",\"fleetProfile\":\"" + profile + "\"" : "";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"" + profileJson + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void register_defaultsToPoidsLourd() throws Exception {
        JsonNode auth = register("TEST-PL", "pl@test.fr", null);
        org.junit.jupiter.api.Assertions.assertEquals("POIDS_LOURD", auth.get("fleetProfile").asText());
    }

    @Test
    void register_withProfile_andAdminCanChangeIt() throws Exception {
        JsonNode auth = register("TEST-Labo Express", "labo@test.fr", "COLLECTE_SANTE");
        String token = auth.get("token").asText();
        org.junit.jupiter.api.Assertions.assertEquals("COLLECTE_SANTE", auth.get("fleetProfile").asText());

        mvc.perform(get("/api/company").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetProfile").value("COLLECTE_SANTE"))
                .andExpect(jsonPath("$.toursEnabled").value(true));

        mvc.perform(put("/api/company/fleet-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fleetProfile\":\"MIXTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetProfile").value("MIXTE"));
    }

    @Test
    void register_rejectsUnknownProfile() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"X\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"bad-profile@test.fr\",\"password\":\"password123\",\"fleetProfile\":\"FUSEE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vul_withoutTachograph_getsDefaultConsumption() throws Exception {
        String token = register("TEST-Messagerie", "messagerie@test.fr", "MESSAGERIE").get("token").asText();

        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"VU-001-AA\",\"brand\":\"Renault\",\"model\":\"Master\","
                                + "\"truckType\":\"VUL\",\"fuelType\":\"ELECTRIC\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckType").value("VUL"))
                .andExpect(jsonPath("$.expectedConsumptionL100Km").value(9.0))
                .andExpect(jsonPath("$.requiresTachograph").value(false))
                .andExpect(jsonPath("$.heavy").value(false));

        // VUL en transport international : tachygraphe explicitement déclaré
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"VU-002-BB\",\"brand\":\"Iveco\",\"model\":\"Daily\","
                                + "\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\",\"active\":true,\"tachographEquipped\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresTachograph").value(true));
    }

    @Test
    void tracteur_requiresTachographByDefault() throws Exception {
        String token = register("TEST-Routier", "routier@test.fr", "POIDS_LOURD").get("token").asText();
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"PL-001-AA\",\"brand\":\"Volvo\",\"model\":\"FH\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"expectedConsumptionL100Km\":30.5,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedConsumptionL100Km").value(30.5))
                .andExpect(jsonPath("$.requiresTachograph").value(true))
                .andExpect(jsonPath("$.heavy").value(true));
    }
}
