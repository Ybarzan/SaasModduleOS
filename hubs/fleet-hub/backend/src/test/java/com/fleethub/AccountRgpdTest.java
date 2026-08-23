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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le volet RGPD : export des données (portabilité), journal d'audit,
 * suppression du compte (effacement) et mentions légales publiques.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRgpdTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String register(String company, String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void exportAndAuditLogForAdmin() throws Exception {
        String token = register("RGPD Co", "admin@rgpd.fr");

        // Un vrai login pour générer une trace CONNEXION dans le journal.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@rgpd.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.name").value("RGPD Co"))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.users[0].username").value("admin@rgpd.fr"));

        MvcResult audit = mvc.perform(get("/api/account/audit-log")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = objectMapper.readTree(audit.getResponse().getContentAsString());
        boolean hasLogin = false;
        boolean hasCreation = false;
        for (JsonNode e : entries) {
            String action = e.get("action").asText();
            if ("CONNEXION".equals(action)) hasLogin = true;
            if ("CREATION_COMPTE".equals(action)) hasCreation = true;
        }
        assertTrue(hasLogin, "Le journal doit contenir une trace de connexion");
        assertTrue(hasCreation, "Le journal doit contenir la création du compte");
    }

    @Test
    void exportForbiddenForGest() throws Exception {
        String adminToken = register("RGPD Gest", "admin@gest.fr");
        MvcResult inviteRes = mvc.perform(post("/api/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Martin\",\"email\":\"jean@gest.fr\","
                                + "\"role\":\"GESTIONNAIRE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteUrl = objectMapper.readTree(inviteRes.getResponse().getContentAsString())
                .get("inviteUrl").asText();
        String inviteToken = inviteUrl.split("token=")[1];

        mvc.perform(post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jean@gest.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String gestToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/account/export").header("Authorization", "Bearer " + gestToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/account/delete")
                        .header("Authorization", "Bearer " + gestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAccountErasesData() throws Exception {
        String token = register("RGPD Del", "admin@del.fr");

        // L'accès fonctionne avant suppression.
        mvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Mot de passe incorrect → refus.
        mvc.perform(post("/api/account/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden());

        // Suppression avec confirmation → 204 puis jeton devenu invalide (401).
        mvc.perform(post("/api/account/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Le login du compte supprimé échoue.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@del.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legalDocumentsArePublic() throws Exception {
        mvc.perform(get("/api/legal/terms")).andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("terms"));
        mvc.perform(get("/api/legal/privacy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Politique de Confidentialité"));
        mvc.perform(get("/api/legal/unknown")).andExpect(status().isNotFound());
    }
}
