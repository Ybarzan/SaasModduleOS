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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portail de pointage chauffeur : identification (roster + PIN), rôle CHAUFFEUR
 * scopé, cycle de service (début/pause/reprise/fin) et régression sur la
 * suppression d'un chauffeur ayant un code portail configuré.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PointageControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String accessCode;
    private long driverId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "pointage-" + System.nanoTime() + "@test.fr";
        MvcResult registerRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Pointage Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registerBody = objectMapper.readTree(registerRes.getResponse().getContentAsString());
        adminToken = registerBody.get("token").asText();
        accessCode = registerBody.get("companyAccessCode").asText();
        assertNotNull(accessCode, "L'inscription doit générer un code d'accès portail");

        MvcResult driverRes = mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Chauffeur\",\"licenseNumber\":\"FR-PTG-"
                                + System.nanoTime() + "\",\"phone\":\"01 00 00 00 00\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        driverId = objectMapper.readTree(driverRes.getResponse().getContentAsString()).get("id").asLong();
    }

    private void setPin(String pin) throws Exception {
        mvc.perform(post("/api/drivers/" + driverId + "/pin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String chauffeurLogin(String pin) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"chauffeur-" + driverId + "@pointage.internal\",\"password\":\""
                                + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class).get("token").toString();
    }

    @Test
    void createDriver_hasPinFalse_thenSetPin_hasPinTrue() throws Exception {
        mvc.perform(get("/api/drivers/" + driverId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPin").value(false));

        setPin("1111");

        mvc.perform(get("/api/drivers/" + driverId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPin").value(true));
    }

    @Test
    void roster_returnsCompanyNameAndActiveDriverOnly() throws Exception {
        setPin("1111");
        mvc.perform(get("/api/pointage/roster/" + accessCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Pointage Test"))
                .andExpect(jsonPath("$.drivers[0].loginUsername").value("chauffeur-" + driverId + "@pointage.internal"));
    }

    @Test
    void roster_inactiveDriver_isExcluded() throws Exception {
        // Désactive le chauffeur via PUT (le roster ne doit plus le lister).
        JsonNode current = objectMapper.readTree(
                mvc.perform(get("/api/drivers/" + driverId).header("Authorization", "Bearer " + adminToken))
                        .andReturn().getResponse().getContentAsString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/drivers/" + driverId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + current.get("firstName").asText() + "\",\"lastName\":\""
                                + current.get("lastName").asText() + "\",\"licenseNumber\":\""
                                + current.get("licenseNumber").asText() + "\",\"phone\":\""
                                + current.get("phone").asText() + "\",\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/pointage/roster/" + accessCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivers").isEmpty());
    }

    @Test
    void roster_unknownAccessCode_returns404() throws Exception {
        mvc.perform(get("/api/pointage/roster/DOESNOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setPin_thenLogin_grantsChauffeurRoleScopedToken() throws Exception {
        setPin("2222");
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"chauffeur-" + driverId + "@pointage.internal\",\"password\":\"2222\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("CHAUFFEUR", body.get("role").asText());
    }

    @Test
    void wrongPin_isRejected() throws Exception {
        setPin("3333");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"chauffeur-" + driverId + "@pointage.internal\",\"password\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullShift_startPauseResumeEnd_returnsCompliantSummary() throws Exception {
        setPin("4444");
        String chauffeurToken = chauffeurLogin("4444");

        mvc.perform(get("/api/pointage/status").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));

        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mvc.perform(post("/api/pointage/pause").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSE"));

        mvc.perform(post("/api/pointage/resume").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mvc.perform(post("/api/pointage/end").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(true))
                .andExpect(jsonPath("$.pauseCount").value(1));

        mvc.perform(get("/api/pointage/status").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void start_whenAlreadyStarted_returns409() throws Exception {
        setPin("5555");
        String chauffeurToken = chauffeurLogin("5555");
        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isConflict());
    }

    @Test
    void pause_withoutActiveService_returns409() throws Exception {
        setPin("6666");
        String chauffeurToken = chauffeurLogin("6666");
        mvc.perform(post("/api/pointage/pause").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isConflict());
    }

    @Test
    void resume_withoutPause_returns409() throws Exception {
        setPin("7777");
        String chauffeurToken = chauffeurLogin("7777");
        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pointage/resume").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isConflict());
    }

    @Test
    void end_withoutActiveService_returns409() throws Exception {
        setPin("8888");
        String chauffeurToken = chauffeurLogin("8888");
        mvc.perform(post("/api/pointage/end").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isConflict());
    }

    @Test
    void chauffeurToken_cannotAccessBackOfficeEndpoints() throws Exception {
        setPin("9990");
        String chauffeurToken = chauffeurLogin("9990");
        mvc.perform(get("/api/drivers").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStatus_chauffeurToken_isForbidden() throws Exception {
        setPin("4441");
        String chauffeurToken = chauffeurLogin("4441");
        mvc.perform(get("/api/pointage/admin/status").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminStatus_returnsCurrentStateForActiveDrivers() throws Exception {
        setPin("4442");
        String chauffeurToken = chauffeurLogin("4442");
        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/pointage/admin/status").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverName").value("Jean Chauffeur"))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"));
    }

    @Test
    void adminToday_listsEventsAcrossDrivers() throws Exception {
        setPin("4443");
        String chauffeurToken = chauffeurLogin("4443");
        mvc.perform(post("/api/pointage/start").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/pointage/admin/today").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverName").value("Jean Chauffeur"))
                .andExpect(jsonPath("$[0].type").value("DEBUT"));
    }

    @Test
    void deletingDriverWithPin_succeeds() throws Exception {
        // Régression : avant e8100f0, supprimer un chauffeur ayant un PIN de
        // pointage configuré échouait en 500 (FK app_user.driver_id orpheline).
        setPin("1230");
        mvc.perform(delete("/api/drivers/" + driverId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/drivers/" + driverId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
