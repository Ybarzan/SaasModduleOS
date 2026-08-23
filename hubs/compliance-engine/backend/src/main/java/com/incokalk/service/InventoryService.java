package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.InventoryItem;
import com.incokalk.model.ItemBarcode;
import com.incokalk.model.StockBalance;
import com.incokalk.model.StockMovement;
import com.incokalk.repository.InventoryItemRepository;
import com.incokalk.repository.ItemBarcodeRepository;
import com.incokalk.repository.StockBalanceRepository;
import com.incokalk.repository.StockMovementRepository;
import com.incokalk.repository.WarehouseRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository itemRepo;
    private final ItemBarcodeRepository barcodeRepo;
    private final StockBalanceRepository balanceRepo;
    private final StockMovementRepository movementRepo;
    private final WarehouseRepository warehouseRepo;

    // ── Items ──────────────────────────────────────────────────────────

    public List<InventoryItem> getAllItems() {
        return itemRepo.findByCompanyId(TenantContext.get());
    }

    public List<InventoryItem> searchItems(String q) {
        UUID companyId = TenantContext.get();
        if (q == null || q.isBlank()) {
            return itemRepo.findByCompanyIdAndIsActiveTrue(companyId);
        }
        String term = q.trim();
        List<InventoryItem> byName = itemRepo.findByCompanyIdAndNameContainingIgnoreCase(companyId, term);
        List<InventoryItem> bySku = itemRepo.findByCompanyIdAndSkuContainingIgnoreCase(companyId, term);
        byName.addAll(bySku);
        return byName.stream().distinct().toList();
    }

    public InventoryItem getItem(UUID id) {
        return itemRepo.findByCompanyIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Article non trouvé"));
    }

    @Transactional
    public InventoryItem createItem(InventoryItem item) {
        UUID companyId = TenantContext.get();
        item.setId(null);
        item.setCompanyId(companyId);
        if (item.getName() == null || item.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'article est requis");
        }
        if (item.getUnit() == null || item.getUnit().isBlank()) {
            item.setUnit("PCS");
        }
        return itemRepo.save(item);
    }

    @Transactional
    public InventoryItem updateItem(UUID id, InventoryItem updated) {
        InventoryItem existing = getItem(id);
        existing.setSku(updated.getSku());
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setHsCode(updated.getHsCode());
        existing.setOriginCountry(updated.getOriginCountry());
        existing.setUnit(updated.getUnit());
        existing.setUnitPrice(updated.getUnitPrice());
        existing.setCategory(updated.getCategory());
        existing.setActive(updated.isActive());
        return itemRepo.save(existing);
    }

    @Transactional
    public void deleteItem(UUID id) {
        InventoryItem existing = getItem(id);
        existing.setActive(false);
        itemRepo.save(existing);
    }

    // ── Barcodes ───────────────────────────────────────────────────────

    public List<ItemBarcode> getBarcodes(UUID itemId) {
        return barcodeRepo.findByCompanyIdAndItemId(TenantContext.get(), itemId);
    }

    @Transactional
    public ItemBarcode addBarcode(UUID itemId, String barcode, String type) {
        UUID companyId = TenantContext.get();
        getItem(itemId);
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Le code-barres est requis");
        }
        Optional<ItemBarcode> existing = barcodeRepo.findByCompanyIdAndBarcode(companyId, barcode.trim());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Ce code-barres est déjà associé à un article");
        }
        ItemBarcode bc = ItemBarcode.builder()
                .companyId(companyId)
                .itemId(itemId)
                .barcode(barcode.trim())
                .barcodeType(type != null && !type.isBlank() ? type.toUpperCase() : "EAN13")
                .build();
        return barcodeRepo.save(bc);
    }

    @Transactional
    public void removeBarcode(UUID itemId, UUID barcodeId) {
        UUID companyId = TenantContext.get();
        ItemBarcode bc = barcodeRepo.findByCompanyIdAndItemId(companyId, itemId).stream()
                .filter(b -> b.getId().equals(barcodeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Code-barres non trouvé"));
        barcodeRepo.delete(bc);
    }

    /**
     * Résout un code-barres (ou SKU) vers un article.
     */
    public Optional<InventoryItem> resolveBarcode(String barcode) {
        UUID companyId = TenantContext.get();
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        String value = barcode.trim();
        Optional<ItemBarcode> match = barcodeRepo.findByCompanyIdAndBarcode(companyId, value);
        if (match.isPresent()) {
            return itemRepo.findByCompanyIdAndId(companyId, match.get().getItemId());
        }
        return itemRepo.findFirstByCompanyIdAndSku(companyId, value);
    }

    // ── Stock ──────────────────────────────────────────────────────────

    public List<StockBalance> getBalances(UUID warehouseId) {
        UUID companyId = TenantContext.get();
        if (warehouseId != null) {
            return balanceRepo.findByCompanyIdAndWarehouseIdOrderByLastUpdatedDesc(companyId, warehouseId);
        }
        return balanceRepo.findByCompanyIdOrderByLastUpdatedDesc(companyId);
    }

    public List<StockMovement> getMovements(UUID itemId) {
        return movementRepo.findByCompanyIdAndItemIdOrderByCreatedAtDesc(TenantContext.get(), itemId);
    }

    @Transactional
    public StockBalance adjustStock(UUID warehouseId, UUID itemId, BigDecimal quantity, String note, UUID userId) {
        UUID companyId = TenantContext.get();
        if (warehouseRepo.findByCompanyIdAndId(companyId, warehouseId).isEmpty()) {
            throw new ResourceNotFoundException("Entrepôt non trouvé");
        }
        getItem(itemId);

        StockBalance balance = balanceRepo.findByWarehouseIdAndItemId(warehouseId, itemId)
                .orElseGet(() -> StockBalance.builder()
                        .companyId(companyId)
                        .warehouseId(warehouseId)
                        .itemId(itemId)
                        .build());
        BigDecimal newQty = balance.getQuantityOnHand().add(quantity);
        if (newQty.signum() < 0) {
            throw new IllegalArgumentException("Stock insuffisant (solde actuel: " + balance.getQuantityOnHand() + ")");
        }
        balance.setQuantityOnHand(newQty);
        StockBalance saved = balanceRepo.save(balance);

        movementRepo.save(StockMovement.builder()
                .companyId(companyId)
                .warehouseId(warehouseId)
                .itemId(itemId)
                .quantity(quantity)
                .type(StockMovement.Type.ADJUSTMENT)
                .referenceType("ADJUSTMENT")
                .note(note)
                .createdBy(userId)
                .build());
        return saved;
    }
}
