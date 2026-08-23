package com.incokalk.controller;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ECommerceSyncLog;
import com.incokalk.service.ECommerceSyncService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ECommerceControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ECommerceSyncService syncService;

    @Test
    @DisplayName("POST /v1/ecommerce/integrations → créer une intégration")
    void createIntegration() throws Exception {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .storeUrl("https://mystore.myshopify.com")
                .isActive(true)
                .build();

        when(syncService.createIntegration(any(), any(), any(), any(), any(), any(), any())).thenReturn(integration);

        mockMvc.perform(post("/v1/ecommerce/integrations")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"SHOPIFY\",\"storeUrl\":\"https://mystore.myshopify.com\",\"apiKey\":\"key123\",\"apiSecret\":\"secret456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("SHOPIFY"))
                .andExpect(jsonPath("$.storeUrl").value("https://mystore.myshopify.com"));
    }

    @Test
    @DisplayName("GET /v1/ecommerce/integrations → lister les intégrations")
    void listIntegrations() throws Exception {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.WOOCOMMERCE)
                .storeUrl("https://myshop.com")
                .isActive(true)
                .build();

        when(syncService.listIntegrations(any())).thenReturn(List.of(integration));

        mockMvc.perform(get("/v1/ecommerce/integrations")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("WOOCOMMERCE"));
    }

    @Test
    @DisplayName("POST /v1/ecommerce/integrations/{id}/sync → déclencher synchronisation manuelle")
    void triggerSync() throws Exception {
        UUID integrationId = UUID.randomUUID();
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(integrationId)
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();

        when(syncService.findIntegrationById(eq(integrationId), any())).thenReturn(java.util.Optional.of(integration));

        ECommerceSyncLog syncLog = ECommerceSyncLog.builder()
                .id(UUID.randomUUID())
                .integrationId(integrationId)
                .status(ECommerceSyncLog.SyncStatus.SUCCESS)
                .ordersProcessed(10)
                .ordersCreated(8)
                .ordersFailed(2)
                .build();

        when(syncService.syncSingleIntegration(integration)).thenReturn(syncLog);

        mockMvc.perform(post("/v1/ecommerce/integrations/" + integrationId + "/sync")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.ordersProcessed").value(10))
                .andExpect(jsonPath("$.ordersCreated").value(8))
                .andExpect(jsonPath("$.ordersFailed").value(2));
    }

    @Test
    @DisplayName("GET /v1/ecommerce/integrations/{id}/orders → lister commandes synchronisées")
    void listSyncedOrders() throws Exception {
        UUID integrationId = UUID.randomUUID();
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(integrationId)
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();

        when(syncService.findIntegrationById(eq(integrationId), any())).thenReturn(java.util.Optional.of(integration));
        when(syncService.resolveAdapter(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(null);

        mockMvc.perform(get("/v1/ecommerce/integrations/" + integrationId + "/orders")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /v1/ecommerce/sync-log → historique des synchronisations")
    void getSyncLog() throws Exception {
        ECommerceSyncLog syncLog = ECommerceSyncLog.builder()
                .id(UUID.randomUUID())
                .integrationId(UUID.randomUUID())
                .status(ECommerceSyncLog.SyncStatus.SUCCESS)
                .ordersProcessed(5)
                .build();

        when(syncService.getSyncLogs(companyId)).thenReturn(List.of(syncLog));

        mockMvc.perform(get("/v1/ecommerce/sync-log")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("PUT /v1/ecommerce/integrations/{id} → modifier une intégration")
    void updateIntegration() throws Exception {
        UUID integrationId = UUID.randomUUID();
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(integrationId)
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .storeUrl("https://newstore.myshopify.com")
                .isActive(false)
                .build();

        when(syncService.updateIntegration(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(integration);

        mockMvc.perform(put("/v1/ecommerce/integrations/" + integrationId)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeUrl\":\"https://newstore.myshopify.com\",\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeUrl").value("https://newstore.myshopify.com"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("DELETE /v1/ecommerce/integrations/{id} → désactiver une intégration")
    void deactivateIntegration() throws Exception {
        UUID integrationId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/ecommerce/integrations/" + integrationId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());
    }
}