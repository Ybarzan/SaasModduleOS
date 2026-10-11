package com.logistix.controller;

import com.logistix.model.InventoryItem;
import com.logistix.model.ItemBarcode;
import com.logistix.model.StockBalance;
import com.logistix.model.StockMovement;
import com.logistix.security.UserPrincipal;
import com.logistix.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Catalogue articles, code-barres, stock")
public class InventoryController {

    private final InventoryService inventoryService;

    // ── Items ──────────────────────────────────────────────────────────

    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Lister les articles (recherche par nom/SKU si ?q=)")
    public ResponseEntity<List<InventoryItem>> listItems(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(inventoryService.searchItems(q));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Obtenir un article")
    public ResponseEntity<InventoryItem> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getItem(id));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Créer un article")
    public ResponseEntity<InventoryItem> createItem(@Valid @RequestBody CreateItem body) {
        return ResponseEntity.ok(inventoryService.createItem(toEntity(body)));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Mettre à jour un article")
    public ResponseEntity<InventoryItem> updateItem(@PathVariable UUID id, @Valid @RequestBody CreateItem body) {
        return ResponseEntity.ok(inventoryService.updateItem(id, toEntity(body)));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un article")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
        inventoryService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    // ── Résolution code-barres ─────────────────────────────────────────

    @GetMapping("/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Résoudre un code-barres ou SKU vers un article")
    public ResponseEntity<InventoryItem> resolveBarcode(@RequestParam String barcode) {
        return inventoryService.resolveBarcode(barcode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Barcodes ───────────────────────────────────────────────────────

    @GetMapping("/items/{itemId}/barcodes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Lister les code-barres d'un article")
    public ResponseEntity<List<ItemBarcode>> listBarcodes(@PathVariable UUID itemId) {
        return ResponseEntity.ok(inventoryService.getBarcodes(itemId));
    }

    @PostMapping("/items/{itemId}/barcodes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Associer un code-barres à un article")
    public ResponseEntity<ItemBarcode> addBarcode(@PathVariable UUID itemId,
                                                  @Valid @RequestBody AddBarcode body) {
        return ResponseEntity.ok(inventoryService.addBarcode(itemId, body.barcode(), body.type()));
    }

    @DeleteMapping("/items/{itemId}/barcodes/{barcodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Retirer un code-barres")
    public ResponseEntity<Void> removeBarcode(@PathVariable UUID itemId, @PathVariable UUID barcodeId) {
        inventoryService.removeBarcode(itemId, barcodeId);
        return ResponseEntity.noContent().build();
    }

    // ── Stock ──────────────────────────────────────────────────────────

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Soldes de stock (filtre ?warehouseId=)")
    public ResponseEntity<List<StockBalance>> getBalances(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(inventoryService.getBalances(warehouseId));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Mouvements de stock d'un article")
    public ResponseEntity<List<StockMovement>> getMovements(@RequestParam UUID itemId) {
        return ResponseEntity.ok(inventoryService.getMovements(itemId));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Ajuster le stock (quantité signée)")
    public ResponseEntity<StockBalance> adjust(@Valid @RequestBody AdjustStock body,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(inventoryService.adjustStock(
                body.warehouseId(), body.itemId(), body.quantity(), body.note(), principal.getId()));
    }

    private InventoryItem toEntity(CreateItem body) {
        return InventoryItem.builder()
                .sku(body.sku())
                .name(body.name())
                .description(body.description())
                .hsCode(body.hsCode())
                .originCountry(body.originCountry())
                .unit(body.unit() != null ? body.unit() : "PCS")
                .unitPrice(body.unitPrice() != null ? body.unitPrice() : BigDecimal.ZERO)
                .category(body.category())
                .isActive(body.active() != null ? body.active() : true)
                .build();
    }

    public record CreateItem(
            @NotBlank String name,
            String sku,
            String description,
            String hsCode,
            String originCountry,
            String unit,
            @DecimalMin(value = "0.0", message = "Le prix unitaire ne peut pas être négatif") BigDecimal unitPrice,
            String category,
            Boolean active
    ) {}

    public record AddBarcode(@NotBlank String barcode, String type) {}

    public record AdjustStock(
            @jakarta.validation.constraints.NotNull UUID warehouseId,
            @jakarta.validation.constraints.NotNull UUID itemId,
            @jakarta.validation.constraints.NotNull BigDecimal quantity,
            String note
    ) {}
}
