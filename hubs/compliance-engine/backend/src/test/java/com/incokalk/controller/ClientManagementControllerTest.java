package com.incokalk.controller;

import com.incokalk.model.ClientUser;
import com.incokalk.model.CompanyRole;
import com.incokalk.service.ClientAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientManagementControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientAuthService clientAuthService;

    private String bearer(CompanyRole.Role role) {
        return "Bearer " + generateJwtToken(userId, companyId, role);
    }

    @Test
    @DisplayName("GET /v1/clients → un OWNER liste les clients de sa société")
    void listClientsAsOwner() throws Exception {
        ClientUser client = ClientUser.builder()
                .id(UUID.randomUUID())
                .email("client@acme.com")
                .fullName("Client Acme")
                .build();
        when(clientAuthService.listClients(eq(companyId))).thenReturn(List.of(client));

        mockMvc.perform(get("/v1/clients")
                        .requestAttr("companyId", companyId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("client@acme.com"));

        verify(clientAuthService).listClients(eq(companyId));
    }

    @Test
    @DisplayName("POST /v1/clients → un OWNER crée un compte client")
    void createClientAsOwner() throws Exception {
        ClientUser client = ClientUser.builder()
                .id(UUID.randomUUID())
                .email("nouveau@acme.com")
                .fullName("Nouveau Client")
                .build();
        when(clientAuthService.createClient(eq(companyId), eq("nouveau@acme.com"), any(), eq("Nouveau Client"), any()))
                .thenReturn(client);

        mockMvc.perform(post("/v1/clients")
                        .requestAttr("companyId", companyId)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nouveau@acme.com\",\"password\":\"password123\",\"fullName\":\"Nouveau Client\",\"phone\":\"+33100000000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nouveau@acme.com"));
    }

    @Test
    @DisplayName("POST /v1/clients → un MANAGER ne peut pas créer de compte client (403)")
    void createClientDeniedForManager() throws Exception {
        mockMvc.perform(post("/v1/clients")
                        .header("Authorization", bearer(CompanyRole.Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@acme.com\",\"password\":\"password123\",\"fullName\":\"X\",\"phone\":\"+33100000000\"}"))
                .andExpect(status().isForbidden());

        verify(clientAuthService, never()).createClient(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /v1/clients/{id} → un MANAGER ne peut pas supprimer un client (403)")
    void deleteClientDeniedForManager() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/v1/clients/" + UUID.randomUUID())
                        .header("Authorization", bearer(CompanyRole.Role.MANAGER)))
                .andExpect(status().isForbidden());

        verify(clientAuthService, never()).deleteClient(any(), any());
    }

    @Test
    @DisplayName("POST /v1/clients/{id}/reset-password → un MANAGER ne peut pas réinitialiser un mot de passe (403)")
    void resetPasswordDeniedForManager() throws Exception {
        mockMvc.perform(post("/v1/clients/" + UUID.randomUUID() + "/reset-password")
                        .header("Authorization", bearer(CompanyRole.Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"password123\"}"))
                .andExpect(status().isForbidden());

        verify(clientAuthService, never()).resetClientPassword(any(), any(), any());
    }
}
