package com.incokalk.controller;

import com.incokalk.config.DataInitializer;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.service.CompanyService;
import com.incokalk.service.RoleChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyService companyService;
    @MockBean
    private RoleChecker roleChecker;
    // Empêche le CommandLineRunner de seed (DataInitializer) de s'exécuter avec un
    // CompanyService mocké (generateUniqueSlug renverrait null → violation NOT NULL sur slug).
    @MockBean
    private DataInitializer dataInitializer;

    private User sampleUser(UUID id) {
        return User.builder()
            .id(id)
            .email("user@example.com")
            .password("hash")
            .fullName("John Doe")
            .build();
    }

    private CompanyRole sampleRole(UUID userId, CompanyRole.Role role) {
        return CompanyRole.builder()
            .id(UUID.randomUUID())
            .user(sampleUser(userId))
            .role(role)
            .build();
    }

    // ── GET /v1/companies/{companyId}/roles ─────────────────────────────

    @Test
    @DisplayName("GET /v1/companies/{companyId}/roles → 403 si l'appelant n'a pas au moins MANAGER")
    void listRoles_forbidden() throws Exception {
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.MANAGER)))
            .thenReturn(false);

        mockMvc.perform(get("/v1/companies/" + companyId + "/roles")
                .header("Authorization", authHeader()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/companies/{companyId}/roles → 404 si la company n'existe pas")
    void listRoles_companyNotFound() throws Exception {
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.MANAGER)))
            .thenReturn(true);
        when(companyService.existsById(companyId)).thenReturn(false);

        mockMvc.perform(get("/v1/companies/" + companyId + "/roles")
                .header("Authorization", authHeader()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /v1/companies/{companyId}/roles → 200 avec la liste des rôles")
    void listRoles_success() throws Exception {
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.MANAGER)))
            .thenReturn(true);
        when(companyService.existsById(companyId)).thenReturn(true);
        UUID memberId = UUID.randomUUID();
        when(companyService.findRolesByCompanyId(companyId))
            .thenReturn(List.of(sampleRole(memberId, CompanyRole.Role.ADMIN)));

        mockMvc.perform(get("/v1/companies/" + companyId + "/roles")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].role").value("ADMIN"))
            .andExpect(jsonPath("$[0].userEmail").value("user@example.com"))
            .andExpect(jsonPath("$[0].userName").value("John Doe"));
    }

    // ── PUT /v1/companies/{companyId}/roles/{targetUserId} ──────────────

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 403 si l'appelant n'a pas au moins ADMIN")
    void updateRole_forbidden() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(false);

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MANAGER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 403 si auto-promotion en OWNER")
    void updateRole_selfPromoteToOwnerRejected() throws Exception {
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(roleChecker.getRole(eq(userId), eq(companyId))).thenReturn(CompanyRole.Role.ADMIN);

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + userId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 403 si l'appelant assigne un rôle au-dessus du sien")
    void updateRole_assignRoleAboveOwnRejected() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(roleChecker.getRole(eq(userId), eq(companyId))).thenReturn(CompanyRole.Role.ADMIN);

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 409 si rétrogradation du dernier OWNER")
    void updateRole_demoteLastOwnerRejected() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        CompanyRole existing = sampleRole(targetUserId, CompanyRole.Role.OWNER);
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(roleChecker.getRole(eq(userId), eq(companyId))).thenReturn(CompanyRole.Role.OWNER);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.of(existing));
        when(companyService.findRolesByCompanyId(companyId))
            .thenReturn(List.of(existing));

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 404 si le rôle cible est introuvable")
    void updateRole_notFound() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(roleChecker.getRole(eq(userId), eq(companyId))).thenReturn(CompanyRole.Role.OWNER);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MANAGER\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 200 succès")
    void updateRole_success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        CompanyRole existing = sampleRole(targetUserId, CompanyRole.Role.USER);
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(roleChecker.getRole(eq(userId), eq(companyId))).thenReturn(CompanyRole.Role.OWNER);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.of(existing));
        when(companyService.saveRole(any())).thenReturn(existing);

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MANAGER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("PUT /v1/companies/{companyId}/roles/{targetUserId} → 400 si le corps est invalide (role manquant)")
    void updateRole_validationError() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(put("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ── DELETE /v1/companies/{companyId}/roles/{targetUserId} ───────────

    @Test
    @DisplayName("DELETE /v1/companies/{companyId}/roles/{targetUserId} → 403 si l'appelant n'a pas au moins ADMIN")
    void removeRole_forbidden() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(false);

        mockMvc.perform(delete("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /v1/companies/{companyId}/roles/{targetUserId} → 404 si le rôle cible est introuvable")
    void removeRole_notFound() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.empty());

        mockMvc.perform(delete("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /v1/companies/{companyId}/roles/{targetUserId} → 400 si la cible est OWNER")
    void removeRole_cannotRemoveOwner() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.of(sampleRole(targetUserId, CompanyRole.Role.OWNER)));

        mockMvc.perform(delete("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /v1/companies/{companyId}/roles/{targetUserId} → 204 succès")
    void removeRole_success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        CompanyRole existing = sampleRole(targetUserId, CompanyRole.Role.MANAGER);
        when(roleChecker.hasRole(eq(userId), eq(companyId), eq(CompanyRole.Role.ADMIN)))
            .thenReturn(true);
        when(companyService.findRoleByCompanyIdAndUserId(companyId, targetUserId))
            .thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/v1/companies/" + companyId + "/roles/" + targetUserId)
                .header("Authorization", authHeader()))
            .andExpect(status().isNoContent());
    }
}
