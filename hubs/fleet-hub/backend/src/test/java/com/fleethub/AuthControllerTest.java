package com.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;

    @Test
    void login_success_returnsTokenAndRole() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_isRejected() throws Exception {
        mvc.perform(get("/api/trucks"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void protectedEndpoint_withToken_returns200() throws Exception {
        String token = loginToken();
        mvc.perform(get("/api/trucks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongRole_isForbiddenAndKeepsSession() throws Exception {
        // Un token valide mais avec un rôle insuffisant doit renvoyer 403 (et non 401),
        // pour ne pas faire croire au client que la session est invalide.
        String adminToken = loginTokenFor("admin", "admin");
        mvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void saasAdminCanAccessBackOffice() throws Exception {
        String saasToken = loginTokenFor("saasadmin", "admin");
        mvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + saasToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidToken_isRejected() throws Exception {
        mvc.perform(get("/api/trucks")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void register_duplicateEmail_returns409NotAuthenticationError() throws Exception {
        // Régression : sur une requête sans JWT (endpoint public), une ResponseStatusException
        // levée par le service passait par un forward interne /error qui retraversait la
        // chaîne de sécurité et masquait le vrai code (409) derrière un 401 générique.
        String body = "{\"companyName\":\"Dup\",\"firstName\":\"A\",\"lastName\":\"B\","
                + "\"email\":\"dup-check@test.fr\",\"password\":\"initialpass1\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Un compte existe déjà avec cet email"));
    }

    @Test
    void acceptInvitation_invalidToken_returns400NotAuthenticationError() throws Exception {
        mvc.perform(post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce lien d'invitation est invalide ou a déjà été utilisé"));
    }

    @Test
    void forgotPassword_existingUser_returns200AndSetsToken() throws Exception {
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isOk());

        AppUser user = userRepository.findByUsername("admin").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(user.getResetToken());
        org.junit.jupiter.api.Assertions.assertNotNull(user.getResetTokenExpiresAt());
    }

    @Test
    void forgotPassword_unknownUser_returns200WithoutEnumeration() throws Exception {
        // Ne doit jamais révéler si le compte existe : même statut que pour un compte réel.
        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost@nowhere.fr\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndAllowsLogin() throws Exception {
        // Compte jetable dédié : la suite partage le contexte Spring/H2 entre classes de
        // test, on évite donc de modifier le mot de passe de "admin" utilisé ailleurs.
        String email = "reset-flow@test.fr";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Reset Flow Co\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"initialpass1\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String token = userRepository.findByUsername(email).orElseThrow().getResetToken();

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"newpassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Le jeton est à usage unique.
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"anotherpassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_revokesToken() throws Exception {
        // 1. Login pour obtenir un token valide
        String token = loginToken();

        // 2. Vérifier que le token fonctionne
        mvc.perform(get("/api/trucks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3. Logout (révoque le token côté serveur)
        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 4. Le même token ne doit plus fonctionner
        mvc.perform(get("/api/trucks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String loginToken() throws Exception {
        return loginTokenFor("admin", "admin");
    }

    private String loginTokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, Map.class).get("token").toString();
    }
}
