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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InventoryService — Tests unitaires")
class InventoryServiceTest {

    @Mock InventoryItemRepository itemRepo;
    @Mock ItemBarcodeRepository barcodeRepo;
    @Mock StockBalanceRepository balanceRepo;
    @Mock StockMovementRepository movementRepo;
    @Mock WarehouseRepository warehouseRepo;

    @InjectMocks InventoryService service;

    UUID companyId;
    UUID itemId;
    InventoryItem item;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        item = InventoryItem.builder().id(itemId).companyId(companyId).name("Widget").sku("SKU-1").build();
    }

    @Test
    @DisplayName("getAllItems → liste des articles de la company")
    void getAllItems() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(itemRepo.findByCompanyId(companyId)).thenReturn(List.of(item));
            assertThat(service.getAllItems()).hasSize(1);
        }
    }

    @Test
    @DisplayName("createItem → sauvegarde avec companyId assigné")
    void createItem() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(itemRepo.save(any(InventoryItem.class))).thenAnswer(a -> {
                InventoryItem i = a.getArgument(0);
                i.setId(UUID.randomUUID());
                return i;
            });
            InventoryItem created = service.createItem(
                    InventoryItem.builder().name("Nouvel article").unit("KG").build());
            assertThat(created.getCompanyId()).isEqualTo(companyId);
            assertThat(created.getId()).isNotNull();
            assertThat(created.getUnit()).isEqualTo("KG");
        }
    }

    @Test
    @DisplayName("createItem sans nom → IllegalArgumentException")
    void createItemBlankName() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            assertThatThrownBy(() -> service.createItem(InventoryItem.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("resolveBarcode → article trouvé via code-barres")
    void resolveBarcode() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            ItemBarcode bc = ItemBarcode.builder().companyId(companyId).itemId(itemId)
                    .barcode("3760123456789").build();
            when(barcodeRepo.findByCompanyIdAndBarcode(companyId, "3760123456789"))
                    .thenReturn(Optional.of(bc));
            when(itemRepo.findByCompanyIdAndId(companyId, itemId)).thenReturn(Optional.of(item));
            assertThat(service.resolveBarcode("3760123456789")).contains(item);
        }
    }

    @Test
    @DisplayName("resolveBarcode → fallback sur SKU")
    void resolveBarcodeBySku() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(barcodeRepo.findByCompanyIdAndBarcode(companyId, "SKU-1")).thenReturn(Optional.empty());
            when(itemRepo.findFirstByCompanyIdAndSku(companyId, "SKU-1")).thenReturn(Optional.of(item));
            assertThat(service.resolveBarcode("SKU-1")).contains(item);
        }
    }

    @Test
    @DisplayName("resolveBarcode inconnu → Optional.empty")
    void resolveBarcodeUnknown() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(barcodeRepo.findByCompanyIdAndBarcode(companyId, "XXXX")).thenReturn(Optional.empty());
            when(itemRepo.findFirstByCompanyIdAndSku(companyId, "XXXX")).thenReturn(Optional.empty());
            assertThat(service.resolveBarcode("XXXX")).isEmpty();
        }
    }

    @Test
    @DisplayName("addBarcode → échec si code déjà utilisé")
    void addBarcodeDuplicate() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(itemRepo.findByCompanyIdAndId(companyId, itemId)).thenReturn(Optional.of(item));
            when(barcodeRepo.findByCompanyIdAndBarcode(companyId, "3760"))
                    .thenReturn(Optional.of(ItemBarcode.builder().build()));
            assertThatThrownBy(() -> service.addBarcode(itemId, "3760", "EAN13"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("adjustStock → met à jour le solde + crée un mouvement")
    void adjustStock() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID warehouseId = UUID.randomUUID();
            when(warehouseRepo.findByCompanyIdAndId(companyId, warehouseId))
                    .thenReturn(Optional.of(com.incokalk.model.Warehouse.builder().build()));
            when(itemRepo.findByCompanyIdAndId(companyId, itemId)).thenReturn(Optional.of(item));
            when(balanceRepo.findByWarehouseIdAndItemId(warehouseId, itemId)).thenReturn(Optional.empty());
            when(balanceRepo.save(any(StockBalance.class))).thenAnswer(a -> a.getArgument(0));
            when(movementRepo.save(any(StockMovement.class))).thenAnswer(a -> a.getArgument(0));

            StockBalance saved = service.adjustStock(warehouseId, itemId, BigDecimal.TEN, "Inventaire", null);
            assertThat(saved.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.TEN);
            verify(movementRepo, times(1)).save(any(StockMovement.class));
        }
    }

    @Test
    @DisplayName("adjustStock → refus si stock négatif")
    void adjustStockNegative() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID warehouseId = UUID.randomUUID();
            when(warehouseRepo.findByCompanyIdAndId(companyId, warehouseId))
                    .thenReturn(Optional.of(com.incokalk.model.Warehouse.builder().build()));
            when(itemRepo.findByCompanyIdAndId(companyId, itemId)).thenReturn(Optional.of(item));
            StockBalance bal = StockBalance.builder().companyId(companyId).warehouseId(warehouseId)
                    .itemId(itemId).quantityOnHand(BigDecimal.ONE).build();
            when(balanceRepo.findByWarehouseIdAndItemId(warehouseId, itemId)).thenReturn(Optional.of(bal));
            assertThatThrownBy(() -> service.adjustStock(warehouseId, itemId, BigDecimal.valueOf(-5), "x", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getItem inconnu → ResourceNotFoundException")
    void getItemNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(itemRepo.findByCompanyIdAndId(companyId, itemId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getItem(itemId)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getBalances avec warehouseId → filtré par companyId ET warehouseId (pas de fuite inter-tenant)")
    void getBalancesFiltersByCompanyAndWarehouse() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID warehouseId = UUID.randomUUID();
            StockBalance balance = StockBalance.builder().companyId(companyId).warehouseId(warehouseId).build();
            when(balanceRepo.findByCompanyIdAndWarehouseIdOrderByLastUpdatedDesc(companyId, warehouseId))
                    .thenReturn(List.of(balance));

            List<StockBalance> result = service.getBalances(warehouseId);

            assertThat(result).containsExactly(balance);
            verify(balanceRepo).findByCompanyIdAndWarehouseIdOrderByLastUpdatedDesc(companyId, warehouseId);
            verify(balanceRepo, never()).findByCompanyIdOrderByLastUpdatedDesc(any());
        }
    }

    @Test
    @DisplayName("getBalances sans warehouseId → filtré uniquement par companyId")
    void getBalancesWithoutWarehouseFiltersByCompany() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(balanceRepo.findByCompanyIdOrderByLastUpdatedDesc(companyId)).thenReturn(List.of());

            service.getBalances(null);

            verify(balanceRepo).findByCompanyIdOrderByLastUpdatedDesc(companyId);
        }
    }

    @Test
    @DisplayName("getMovements → filtré par companyId ET itemId (pas de fuite inter-tenant)")
    void getMovementsFiltersByCompanyAndItem() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            StockMovement movement = StockMovement.builder().companyId(companyId).itemId(itemId).build();
            when(movementRepo.findByCompanyIdAndItemIdOrderByCreatedAtDesc(companyId, itemId))
                    .thenReturn(List.of(movement));

            List<StockMovement> result = service.getMovements(itemId);

            assertThat(result).containsExactly(movement);
            verify(movementRepo).findByCompanyIdAndItemIdOrderByCreatedAtDesc(companyId, itemId);
        }
    }
}
