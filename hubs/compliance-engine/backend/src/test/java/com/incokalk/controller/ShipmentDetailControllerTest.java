package com.incokalk.controller;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.service.ShipmentDetailService;
import com.incokalk.service.integration.fleethub.ShipmentDetailDto;
import com.incokalk.service.integration.fleethub.ShipmentItemDetailDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShipmentDetailControllerTest extends ControllerTestBase {

    @MockBean
    private ShipmentDetailService shipmentDetailService;

    @Test
    @DisplayName("GET /v1/shipments/{id} → 200, détail retourné")
    void getShipment_success() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        ShipmentDetailDto dto = new ShipmentDetailDto();
        dto.setId(shipmentId);
        dto.setOrderNumber("SHP-2026-001");
        dto.setStatus("IN_TRANSIT");
        dto.setShipperName("Shipper SARL");
        dto.setConsigneeName("Consignee Ltd");
        when(shipmentDetailService.getDetail(eq(shipmentId), eq(companyId))).thenReturn(dto);

        mockMvc.perform(get("/v1/shipments/" + shipmentId).header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("SHP-2026-001"))
                .andExpect(jsonPath("$.shipperName").value("Shipper SARL"))
                .andExpect(jsonPath("$.consigneeName").value("Consignee Ltd"));
    }

    @Test
    @DisplayName("GET /v1/shipments/{id} → 404 si fleet-hub indisponible ou expédition introuvable")
    void getShipment_notFound() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(shipmentDetailService.getDetail(eq(shipmentId), eq(companyId)))
                .thenThrow(new ResourceNotFoundException("Expédition introuvable"));

        mockMvc.perform(get("/v1/shipments/" + shipmentId).header("Authorization", authHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /v1/shipments/{id}/items → 200, articles retournés")
    void getShipmentItems_success() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        ShipmentItemDetailDto item = new ShipmentItemDetailDto();
        item.setSku("SKU-1");
        item.setName("Widget");
        item.setQuantity(BigDecimal.TEN);
        item.setUnit("PCS");
        item.setUnitPrice(BigDecimal.valueOf(5));
        when(shipmentDetailService.getItems(eq(shipmentId), eq(companyId))).thenReturn(List.of(item));

        mockMvc.perform(get("/v1/shipments/" + shipmentId + "/items").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[0].name").value("Widget"));
    }

    @Test
    @DisplayName("GET /v1/shipments/{id} sans authentification → 401")
    void getShipment_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/v1/shipments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
