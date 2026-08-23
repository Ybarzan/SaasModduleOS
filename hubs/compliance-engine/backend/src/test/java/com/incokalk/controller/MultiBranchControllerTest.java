package com.incokalk.controller;

import com.incokalk.model.CompanyBranch;
import com.incokalk.model.InterBranchTransfer;
import com.incokalk.service.MultiBranchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MultiBranchControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MultiBranchService branchService;

    @Test
    @DisplayName("GET /v1/branches → liste des filiales")
    void getBranches() throws Exception {
        CompanyBranch branch = CompanyBranch.builder()
                .id(UUID.randomUUID())
                .branchName("Filiale Paris")
                .build();

        when(branchService.getBranches()).thenReturn(List.of(branch));

        mockMvc.perform(get("/v1/branches")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchName").value("Filiale Paris"));
    }

    @Test
    @DisplayName("POST /v1/branches/add → ajoute une filiale")
    void addBranch() throws Exception {
        UUID branchCompanyId = UUID.randomUUID();
        CompanyBranch branch = CompanyBranch.builder()
                .id(UUID.randomUUID())
                .branchCompanyId(branchCompanyId)
                .branchName("Nouvelle Filiale")
                .build();

        when(branchService.addBranch(eq(companyId), eq(branchCompanyId), eq("Nouvelle Filiale"))).thenReturn(branch);

        mockMvc.perform(post("/v1/branches/add")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchCompanyId\":\"" + branchCompanyId + "\",\"branchName\":\"Nouvelle Filiale\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("Nouvelle Filiale"));
    }

    @Test
    @DisplayName("DELETE /v1/branches/{id} → désactive une filiale")
    void removeBranch() throws Exception {
        UUID branchId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/branches/" + branchId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(branchService).removeBranch(eq(companyId), eq(branchId));
    }

    @Test
    @DisplayName("GET /v1/branches/parent → société mère")
    void getParentCompany() throws Exception {
        CompanyBranch parent = CompanyBranch.builder()
                .parentCompanyId(UUID.randomUUID())
                .branchName("Maison Mère")
                .build();

        when(branchService.getParentCompany()).thenReturn(Optional.of(parent));

        mockMvc.perform(get("/v1/branches/parent")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("Maison Mère"));
    }

    @Test
    @DisplayName("GET /v1/branches/consolidated-report → rapport consolidé")
    void getConsolidatedReport() throws Exception {
        when(branchService.getConsolidatedReport()).thenReturn(Map.of(
                "totalShipments", 150L,
                "totalRevenue", 1250000.0,
                "companiesCount", 3
        ));

        mockMvc.perform(get("/v1/branches/consolidated-report")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").value(150))
                .andExpect(jsonPath("$.companiesCount").value(3));
    }

    @Test
    @DisplayName("GET /v1/branches/transfers → historique transferts")
    void getTransferHistory() throws Exception {
        InterBranchTransfer transfer = InterBranchTransfer.builder()
                .id(UUID.randomUUID())
                .goodsDescription("Produits test")
                .quantity(new BigDecimal("100"))
                .build();

        when(branchService.getTransferHistory()).thenReturn(List.of(transfer));

        mockMvc.perform(get("/v1/branches/transfers")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goodsDescription").value("Produits test"));
    }

    @Test
    @DisplayName("POST /v1/branches/transfers → créer un transfert")
    void createTransfer() throws Exception {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        InterBranchTransfer transfer = InterBranchTransfer.builder()
                .id(UUID.randomUUID())
                .fromBranchId(fromId)
                .toBranchId(toId)
                .goodsDescription("Marchandises")
                .quantity(new BigDecimal("50"))
                .build();

        when(branchService.transferGoods(eq(companyId), eq(fromId), eq(toId), eq("Marchandises"), any(BigDecimal.class)))
                .thenReturn(transfer);

        mockMvc.perform(post("/v1/branches/transfers")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBranchId\":\"" + fromId + "\",\"toBranchId\":\"" + toId + "\",\"goodsDescription\":\"Marchandises\",\"quantity\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goodsDescription").value("Marchandises"))
                .andExpect(jsonPath("$.quantity").value(50));
    }
}