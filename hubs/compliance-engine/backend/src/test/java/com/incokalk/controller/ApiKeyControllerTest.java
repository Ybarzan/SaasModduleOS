package com.incokalk.controller;

import com.incokalk.model.ApiKey;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.service.ApiKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/api-keys/** est bien restreint à OWNER/ADMIN et scopé par société.
 *
 * Avant correctif :
 *  - aucun endpoint n'était annoté @RolesAllowed : un utilisateur USER pouvait créer,
 *    lister ou révoquer les clés API de la société (credential-management endpoint).
 *  - le service scopait par userId (créateur) et non par companyId (TenantContext),
 *    alors qu'un même utilisateur peut appartenir à plusieurs sociétés (company_roles) :
 *    une clé créée sous la société A pouvait apparaître / être révoquée depuis le
 *    contexte de la société B.
 */
class ApiKeyControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    @Test
    @DisplayName("POST /v1/api-keys → 403 pour un rôle USER")
    void create_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(post("/v1/api-keys")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clé prod\",\"plan\":\"PRO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/api-keys → 403 pour un rôle MANAGER")
    void create_forbiddenForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/api-keys")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clé prod\",\"plan\":\"PRO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/api-keys → 201 pour un rôle ADMIN, créée sous la société courante (TenantContext)")
    void create_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.create(eq(userId), eq(companyId), eq("Clé prod"), eq(User.Plan.PRO)))
                .thenReturn(new ApiKeyService.CreatedApiKey(keyId, "ic_live_secret", "ic_live_abcd1234", "PRO", 500));

        mockMvc.perform(post("/v1/api-keys")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clé prod\",\"plan\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("ic_live_secret"))
                .andExpect(jsonPath("$.prefix").value("ic_live_abcd1234"));

        verify(apiKeyService).create(userId, companyId, "Clé prod", User.Plan.PRO);
    }

    @Test
    @DisplayName("GET /v1/api-keys → 403 pour un rôle USER")
    void list_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/api-keys")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/api-keys → 200 pour un rôle ADMIN, scopé sur la société courante")
    void list_allowedForAdmin_scopedByCompany() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        User user = User.builder().id(userId).email("test@test.com").plan(User.Plan.PRO).build();
        Company company = Company.builder().id(companyId).name("Acme").build();
        ApiKey key = ApiKey.builder()
                .id(UUID.randomUUID()).user(user).company(company)
                .keyHash("super-secret-hash").keyPrefix("ic_live_abcd1234")
                .name("Clé prod").plan(User.Plan.PRO).dailyLimit(500).active(true)
                .build();
        when(apiKeyService.listForCompany(companyId)).thenReturn(List.of(key));

        mockMvc.perform(get("/v1/api-keys")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyPrefix").value("ic_live_abcd1234"))
                .andExpect(jsonPath("$[0].companyId").value(companyId.toString()))
                // La clé brute et son hash ne doivent jamais être exposés hors création.
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].key").doesNotExist());

        verify(apiKeyService).listForCompany(companyId);
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} → 403 pour un rôle USER")
    void revoke_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api-keys/" + keyId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} → 200 pour un rôle ADMIN, révocation scopée sur la société courante")
    void revoke_allowedForAdmin_scopedByCompany() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api-keys/" + keyId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Clé révoquée"));

        verify(apiKeyService).revoke(keyId, companyId);
    }
}
