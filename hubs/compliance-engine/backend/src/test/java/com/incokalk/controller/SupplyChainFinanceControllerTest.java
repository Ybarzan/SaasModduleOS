package com.incokalk.controller;

import com.incokalk.model.InvoiceFinancing;
import com.incokalk.service.SupplyChainFinanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplyChainFinanceControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupplyChainFinanceService supplyChainFinanceService;

    @Test
    @DisplayName("POST /v1/finance/request → demander un financement")
    void requestFinancing() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(UUID.randomUUID())
                .invoiceId(invoiceId)
                .requestedAmount(new BigDecimal("10000.00"))
                .status(InvoiceFinancing.Status.PENDING)
                .build();

        when(supplyChainFinanceService.requestFinancing(any(UUID.class), any(BigDecimal.class))).thenReturn(financing);

        mockMvc.perform(post("/v1/finance/request")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":\"" + invoiceId + "\",\"amount\":10000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /v1/finance/{id}/approve → approuver un financement")
    void approveFinancing() throws Exception {
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .status(InvoiceFinancing.Status.APPROVED)
                .build();

        when(supplyChainFinanceService.approveFinancing(financingId)).thenReturn(financing);

        mockMvc.perform(post("/v1/finance/" + financingId + "/approve")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /v1/finance/{id}/fund → financer une demande")
    void fundFinancing() throws Exception {
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .status(InvoiceFinancing.Status.FUNDED)
                .financeAmount(new BigDecimal("9750.00"))
                .build();

        when(supplyChainFinanceService.fundFinancing(financingId)).thenReturn(financing);

        mockMvc.perform(post("/v1/finance/" + financingId + "/fund")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FUNDED"));
    }

    @Test
    @DisplayName("POST /v1/finance/{id}/repay → rembourser un financement")
    void repayFinancing() throws Exception {
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .status(InvoiceFinancing.Status.REPAID)
                .build();

        when(supplyChainFinanceService.repayFinancing(financingId)).thenReturn(financing);

        mockMvc.perform(post("/v1/finance/" + financingId + "/repay")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPAID"));
    }

    @Test
    @DisplayName("GET /v1/finance/history → historique des financements")
    void getHistory() throws Exception {
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(UUID.randomUUID())
                .requestedAmount(new BigDecimal("5000.00"))
                .status(InvoiceFinancing.Status.FUNDED)
                .build();

        when(supplyChainFinanceService.getFinancingHistory()).thenReturn(List.of(financing));

        mockMvc.perform(get("/v1/finance/history")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FUNDED"));
    }

    @Test
    @DisplayName("GET /v1/finance/stats → statistiques")
    void getStats() throws Exception {
        when(supplyChainFinanceService.getStats()).thenReturn(Map.of(
                "totalFinanced", 125000.0,
                "pendingCount", 3,
                "fundedCount", 12,
                "repaidCount", 8,
                "averageFeePercent", 2.50
        ));

        mockMvc.perform(get("/v1/finance/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFinanced").value(125000.0))
                .andExpect(jsonPath("$.pendingCount").value(3));
    }

    @Test
    @DisplayName("GET /v1/finance/early-payment-discount → calculer escompte")
    void getEarlyPaymentDiscount() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(supplyChainFinanceService.getEarlyPaymentDiscount(eq(invoiceId), any(BigDecimal.class)))
                .thenReturn(Map.of(
                        "invoiceId", invoiceId,
                        "originalAmount", 10000.0,
                        "discountPercent", 2.0,
                        "discountAmount", 200.0,
                        "discountedAmount", 9800.0
                ));

        mockMvc.perform(get("/v1/finance/early-payment-discount")
                        .header("Authorization", authHeader())
                        .param("invoiceId", invoiceId.toString())
                        .param("amount", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalAmount").value(10000.0))
                .andExpect(jsonPath("$.discountAmount").value(200.0));
    }
}