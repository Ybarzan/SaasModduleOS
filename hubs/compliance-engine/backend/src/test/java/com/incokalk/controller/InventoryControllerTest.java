package com.incokalk.controller;

import com.incokalk.model.InventoryItem;
import com.incokalk.model.ItemBarcode;
import com.incokalk.model.StockBalance;
import com.incokalk.model.StockMovement;
import com.incokalk.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    private InventoryItem sampleItem() {
        return InventoryItem.builder()
            .id(UUID.randomUUID())
            .companyId(companyId)
            .sku("WID-1")
            .name("Widget")
            .description("desc")
            .hsCode("850110")
            .originCountry("CN")
            .unit("KG")
            .unitPrice(BigDecimal.valueOf(12.5))
            .category("cat")
            .isActive(true)
            .build();
    }

    private ItemBarcode sampleBarcode(UUID itemId) {
        return ItemBarcode.builder()
            .id(UUID.randomUUID())
            .companyId(companyId)
            .itemId(itemId)
            .barcode("1234567890128")
            .barcodeType("EAN13")
            .build();
    }

    private StockBalance sampleBalance(UUID warehouseId, UUID itemId) {
        return StockBalance.builder()
            .id(UUID.randomUUID())
            .companyId(companyId)
            .warehouseId(warehouseId)
            .itemId(itemId)
            .quantityOnHand(BigDecimal.TEN)
            .build();
    }

    private StockMovement sampleMovement(UUID warehouseId, UUID itemId) {
        return StockMovement.builder()
            .id(UUID.randomUUID())
            .companyId(companyId)
            .warehouseId(warehouseId)
            .itemId(itemId)
            .quantity(BigDecimal.ONE)
            .type(StockMovement.Type.ADJUSTMENT)
            .build();
    }

    // ── Items ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/inventory/items → 200 sans filtre q")
    void listItems_noQuery() throws Exception {
        when(inventoryService.searchItems(isNull())).thenReturn(List.of(sampleItem()));

        mockMvc.perform(get("/v1/inventory/items").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sku").value("WID-1"));
    }

    @Test
    @DisplayName("GET /v1/inventory/items?q=wid → 200 avec filtre q")
    void listItems_withQuery() throws Exception {
        when(inventoryService.searchItems(eq("wid"))).thenReturn(List.of(sampleItem()));

        mockMvc.perform(get("/v1/inventory/items?q=wid").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sku").value("WID-1"));
    }

    @Test
    @DisplayName("GET /v1/inventory/items/{id} → 200")
    void getItem_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(inventoryService.getItem(id)).thenReturn(sampleItem());

        mockMvc.perform(get("/v1/inventory/items/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("WID-1"));
    }

    @Test
    @DisplayName("GET /v1/inventory/items/{id} → 404 si introuvable")
    void getItem_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(inventoryService.getItem(id))
            .thenThrow(new com.incokalk.exception.ResourceNotFoundException("Article non trouvé"));

        mockMvc.perform(get("/v1/inventory/items/" + id).header("Authorization", authHeader()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /v1/inventory/items → 200 avec tous les champs fournis")
    void createItem_fullBody() throws Exception {
        when(inventoryService.createItem(any())).thenReturn(sampleItem());

        mockMvc.perform(post("/v1/inventory/items")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Widget","sku":"WID-1","description":"desc",
                     "hsCode":"850110","originCountry":"CN","unit":"KG",
                     "unitPrice":12.5,"category":"cat","active":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("WID-1"));
    }

    @Test
    @DisplayName("POST /v1/inventory/items → 200 avec champs optionnels absents (valeurs par défaut)")
    void createItem_minimalBody() throws Exception {
        when(inventoryService.createItem(any())).thenReturn(sampleItem());

        mockMvc.perform(post("/v1/inventory/items")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Widget2\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/inventory/items → 400 si nom manquant (validation)")
    void createItem_validationFailure() throws Exception {
        mockMvc.perform(post("/v1/inventory/items")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /v1/inventory/items/{id} → 200 avec tous les champs fournis")
    void updateItem_fullBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(inventoryService.updateItem(eq(id), any())).thenReturn(sampleItem());

        mockMvc.perform(put("/v1/inventory/items/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Widget","sku":"WID-1","description":"desc",
                     "hsCode":"850110","originCountry":"CN","unit":"KG",
                     "unitPrice":12.5,"category":"cat","active":true}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /v1/inventory/items/{id} → 200 avec champs optionnels absents")
    void updateItem_minimalBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(inventoryService.updateItem(eq(id), any())).thenReturn(sampleItem());

        mockMvc.perform(put("/v1/inventory/items/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Widget\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /v1/inventory/items/{id} → 204")
    void deleteItem_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/inventory/items/" + id).header("Authorization", authHeader()))
            .andExpect(status().isNoContent());
    }

    // ── Résolution code-barres ─────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/inventory/resolve?barcode=... → 200 si trouvé")
    void resolveBarcode_found() throws Exception {
        when(inventoryService.resolveBarcode("1234567890128")).thenReturn(Optional.of(sampleItem()));

        mockMvc.perform(get("/v1/inventory/resolve?barcode=1234567890128")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("WID-1"));
    }

    @Test
    @DisplayName("GET /v1/inventory/resolve?barcode=... → 404 si introuvable")
    void resolveBarcode_notFound() throws Exception {
        when(inventoryService.resolveBarcode("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/inventory/resolve?barcode=unknown")
                .header("Authorization", authHeader()))
            .andExpect(status().isNotFound());
    }

    // ── Barcodes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/inventory/items/{itemId}/barcodes → 200")
    void listBarcodes_success() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(inventoryService.getBarcodes(itemId)).thenReturn(List.of(sampleBarcode(itemId)));

        mockMvc.perform(get("/v1/inventory/items/" + itemId + "/barcodes")
                .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].barcode").value("1234567890128"));
    }

    @Test
    @DisplayName("POST /v1/inventory/items/{itemId}/barcodes → 200")
    void addBarcode_success() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(inventoryService.addBarcode(eq(itemId), eq("1234567890128"), eq("EAN13")))
            .thenReturn(sampleBarcode(itemId));

        mockMvc.perform(post("/v1/inventory/items/" + itemId + "/barcodes")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"1234567890128\",\"type\":\"EAN13\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.barcode").value("1234567890128"));
    }

    @Test
    @DisplayName("POST /v1/inventory/items/{itemId}/barcodes → 400 si déjà associé")
    void addBarcode_rejected() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(inventoryService.addBarcode(eq(itemId), any(), any()))
            .thenThrow(new IllegalArgumentException("Ce code-barres est déjà associé à un article"));

        mockMvc.perform(post("/v1/inventory/items/" + itemId + "/barcodes")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"1234567890128\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /v1/inventory/items/{itemId}/barcodes/{barcodeId} → 204")
    void removeBarcode_success() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID barcodeId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/inventory/items/" + itemId + "/barcodes/" + barcodeId)
                .header("Authorization", authHeader()))
            .andExpect(status().isNoContent());
    }

    // ── Stock ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/inventory/balances → 200 sans filtre warehouseId")
    void getBalances_noFilter() throws Exception {
        when(inventoryService.getBalances(isNull())).thenReturn(List.of(sampleBalance(UUID.randomUUID(), UUID.randomUUID())));

        mockMvc.perform(get("/v1/inventory/balances").header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/inventory/balances?warehouseId=... → 200 avec filtre")
    void getBalances_withFilter() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(inventoryService.getBalances(eq(warehouseId)))
            .thenReturn(List.of(sampleBalance(warehouseId, UUID.randomUUID())));

        mockMvc.perform(get("/v1/inventory/balances?warehouseId=" + warehouseId)
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/inventory/movements?itemId=... → 200")
    void getMovements_success() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(inventoryService.getMovements(itemId))
            .thenReturn(List.of(sampleMovement(UUID.randomUUID(), itemId)));

        mockMvc.perform(get("/v1/inventory/movements?itemId=" + itemId)
                .header("Authorization", authHeader()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/inventory/adjustments → 200")
    void adjust_success() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(inventoryService.adjustStock(eq(warehouseId), eq(itemId), any(), any(), any()))
            .thenReturn(sampleBalance(warehouseId, itemId));

        mockMvc.perform(post("/v1/inventory/adjustments")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":\"" + warehouseId + "\",\"itemId\":\"" + itemId
                    + "\",\"quantity\":5,\"note\":\"ajustement test\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/inventory/adjustments → 400 si stock insuffisant")
    void adjust_insufficientStock() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(inventoryService.adjustStock(eq(warehouseId), eq(itemId), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("Stock insuffisant (solde actuel: 0)"));

        mockMvc.perform(post("/v1/inventory/adjustments")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":\"" + warehouseId + "\",\"itemId\":\"" + itemId
                    + "\",\"quantity\":-100,\"note\":\"trop\"}"))
            .andExpect(status().isBadRequest());
    }
}
