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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie la gestion des utilisateurs d'un tenant : invitation par email,
 * acceptation (mot de passe), changement de rôle, désactivation (blocage de la
 * connexion), garde-fous (dernier ADMIN, auto-suppression) et contrôle d'accès.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserManagementTest {

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

    private Invite invite(String adminToken, String email, String role) throws Exception {
        MvcResult res = mvc.perform(post("/api/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Martin\",\"email\":\""
                                + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        String inviteUrl = body.get("inviteUrl").asText();
        return new Invite(body.get("user").get("id").asLong(), inviteUrl.split("token=")[1]);
    }

    private String acceptAndLogin(String email, String token) throws Exception {
        mvc.perform(post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void inviteAcceptChangeRoleAndDisable() throws Exception {
        String adminToken = register("Users Co", "admin@usersco.fr");
        Invite invited = invite(adminToken, "jean.martin@usersco.fr", "GESTIONNAIRE");

        // L'invité n'a pas encore de compte actif tant qu'il n'a pas accepté.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jean.martin@usersco.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());

        // Acceptation : création du mot de passe + activation.
        String gestToken = acceptAndLogin("jean.martin@usersco.fr", invited.token);

        // Un GESTIONNAIRE ne peut pas gérer les utilisateurs (403).
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + gestToken))
                .andExpect(status().isForbidden());

        // Promotion en ADMIN par l'ADMIN du tenant.
        mvc.perform(put("/api/users/" + invited.id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Désactivation : la connexion est ensuite bloquée (403).
        mvc.perform(put("/api/users/" + invited.id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isOk());
        MvcResult blocked = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jean.martin@usersco.fr\",\"password\":\"password123\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(403, blocked.getResponse().getStatus(),
                "Login bloqué, body=" + blocked.getResponse().getContentAsString());
    }

    @Test
    void cannotDisableOrDeleteLastAdmin() throws Exception {
        String adminToken = register("Users Guard", "admin@guard.fr");
        long adminId = firstUserId(adminToken);

        // L'ADMIN fondateur est le seul ADMIN actif : il ne peut pas se désactiver.
        mvc.perform(put("/api/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        // Ni se supprimer lui-même (il resterait zéro ADMIN actif).
        mvc.perform(delete("/api/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolesAndCrossTenantIsolation() throws Exception {
        String adminA = register("Users A", "admin@a.fr");
        String adminB = register("Users B", "admin@b.fr");

        // Société B invite un utilisateur ; société A ne doit pas pouvoir le modifier/supprimer.
        Invite invitedB = invite(adminB, "paul@b.fr", "GESTIONNAIRE");

        mvc.perform(put("/api/users/" + invitedB.id)
                        .header("Authorization", "Bearer " + adminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/users/" + invitedB.id)
                        .header("Authorization", "Bearer " + adminA))
                .andExpect(status().isNotFound());

        // Un rôle inconnu est rejeté.
        mvc.perform(put("/api/users/" + invitedB.id)
                        .header("Authorization", "Bearer " + adminB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"HACKER\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());

        // Invitation avec un email déjà pris (même adresse dans une autre société) : conflit global.
        mvc.perform(post("/api/users/invite")
                        .header("Authorization", "Bearer " + adminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Martin\",\"email\":\""
                                + "paul@b.fr" + "\",\"role\":\"GESTIONNAIRE\"}"))
                .andExpect(status().isConflict());
    }

    private long firstUserId(String adminToken) throws Exception {
        MvcResult res = mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals(1, arr.size(), "Seul le fondateur existe");
        return arr.get(0).get("id").asLong();
    }

    private record Invite(long id, String token) {}
}
