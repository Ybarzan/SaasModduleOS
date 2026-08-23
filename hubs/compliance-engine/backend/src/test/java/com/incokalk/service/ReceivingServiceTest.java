package com.incokalk.service;

import com.incokalk.model.*;
import com.incokalk.repository.*;
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

@DisplayName("ReceivingService — Tests unitaires")
class ReceivingServiceTest {

    @Mock ReceivingOrderRepository orderRepo;
    @Mock ReceivingOrderLineRepository lineRepo;
    @Mock ReceivingScanRepository scanRepo;
    @Mock DiscrepancyRepository discrepancyRepo;
    @Mock StockBalanceRepository balanceRepo;
    @Mock StockMovementRepository movementRepo;
    @Mock InventoryService inventoryService;
    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock ShipmentItemRepository shipmentItemRepo;

    @InjectMocks ReceivingService service;

    UUID companyId;
    UUID warehouseId;
    UUID itemId;
    UUID orderId;
    ReceivingOrder order;
    InventoryItem item;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        item = InventoryItem.builder().id(itemId).companyId(companyId).name("Widget").unit("PCS").build();
        order = ReceivingOrder.builder()
                .id(orderId).companyId(companyId).warehouseId(warehouseId)
                .orderNumber("RCT-0001").status(ReceivingOrder.Status.DRAFT).build();
    }

    @Test
    @DisplayName("createOrder → sauvegarde le bon + les lignes")
    void createOrder() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> {
                ReceivingOrder o = a.getArgument(0);
                o.setId(orderId);
                return o;
            });
            when(inventoryService.getItem(itemId)).thenReturn(item);
            when(lineRepo.save(any(ReceivingOrderLine.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingOrder created = service.createOrder(warehouseId, null, "REF", "notes",
                    List.of(new ReceivingService.CreateLine(itemId, BigDecimal.TEN, "PCS")), UUID.randomUUID());

            assertThat(created.getId()).isEqualTo(orderId);
            assertThat(created.getStatus()).isEqualTo(ReceivingOrder.Status.DRAFT);
            verify(orderRepo, times(1)).save(any(ReceivingOrder.class));
            verify(lineRepo, times(1)).save(any(ReceivingOrderLine.class));
        }
    }

    @Test
    @DisplayName("createOrder → lien shipment : référence préremplie depuis l'expédition")
    void createOrderFromShipment() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID shipmentId = UUID.randomUUID();
            ShipmentOrder shipment = ShipmentOrder.builder()
                    .id(shipmentId)
                    .company(Company.builder().id(companyId).build())
                    .orderNumber("SHP-20260731-0001")
                    .goodsDescription("Chaussures de sécurité")
                    .status(ShipmentOrder.Status.IN_TRANSIT)
                    .build();
            when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> {
                ReceivingOrder o = a.getArgument(0);
                o.setId(orderId);
                return o;
            });
            when(inventoryService.getItem(itemId)).thenReturn(item);
            when(lineRepo.save(any(ReceivingOrderLine.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingOrder created = service.createOrder(warehouseId, shipmentId, null, null,
                    List.of(new ReceivingService.CreateLine(itemId, BigDecimal.TEN, "PCS")), UUID.randomUUID());

            assertThat(created.getShipmentId()).isEqualTo(shipmentId);
            assertThat(created.getReference()).isEqualTo("SHP-20260731-0001 — Chaussures de sécurité");
            verify(orderRepo, times(1)).save(any(ReceivingOrder.class));
        }
    }

    @Test
    @DisplayName("createOrder → shipment d'une autre entreprise → ResourceNotFoundException")
    void createOrderWithForeignShipment() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID shipmentId = UUID.randomUUID();
            when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createOrder(warehouseId, shipmentId, null, null,
                    List.of(), UUID.randomUUID()))
                    .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
            verify(orderRepo, never()).save(any(ReceivingOrder.class));
        }
    }

    @Test
    @DisplayName("getOrder → OrderDetail expose le résumé shipment")
    void getOrderWithShipment() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID shipmentId = UUID.randomUUID();
            ReceivingOrder linked = ReceivingOrder.builder()
                    .id(orderId).companyId(companyId).warehouseId(warehouseId)
                    .orderNumber("RCT-0003").shipmentId(shipmentId).status(ReceivingOrder.Status.DRAFT).build();
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(linked));
            when(lineRepo.findByReceivingOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
            when(scanRepo.findByReceivingOrderIdOrderByScannedAtAsc(orderId)).thenReturn(List.of());
            when(discrepancyRepo.findByReceivingOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(List.of());
            ShipmentOrder shipment = ShipmentOrder.builder()
                    .id(shipmentId).company(Company.builder().id(companyId).build())
                    .orderNumber("SHP-20260731-0002").goodsDescription("Casques")
                    .status(ShipmentOrder.Status.DELIVERED).packagesCount(5).goodsValue(1200.0).currency("EUR")
                    .build();
            when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));

            ReceivingService.OrderDetail detail = service.getOrder(orderId);

            assertThat(detail.shipment()).isNotNull();
            assertThat(detail.shipment().orderNumber()).isEqualTo("SHP-20260731-0002");
            assertThat(detail.shipment().status()).isEqualTo("DELIVERED");
            assertThat(detail.shipment().packagesCount()).isEqualTo(5);
            assertThat(detail.remaining()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("listOrders → filtre par shipmentId")
    void listOrdersByShipment() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID shipmentId = UUID.randomUUID();
            when(orderRepo.findByCompanyIdAndShipmentIdOrderByCreatedAtDesc(companyId, shipmentId))
                    .thenReturn(List.of(order));

            List<ReceivingOrder> result = service.listOrders(null, null, shipmentId);

            assertThat(result).hasSize(1);
            verify(orderRepo, times(1)).findByCompanyIdAndShipmentIdOrderByCreatedAtDesc(companyId, shipmentId);
        }
    }

    @Test
    @DisplayName("scan → article connu : ligne mise à jour, stock posté, statut RECEIVING")
    void scanKnownItem() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(order));

            ReceivingOrderLine line = ReceivingOrderLine.builder()
                    .id(UUID.randomUUID()).companyId(companyId).receivingOrderId(orderId)
                    .itemId(itemId).quantityExpected(BigDecimal.TEN)
                    .quantityReceived(BigDecimal.ZERO).build();
            when(lineRepo.findByReceivingOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(line));
            when(inventoryService.getItem(itemId)).thenReturn(item);
            when(scanRepo.save(any(ReceivingScan.class))).thenAnswer(a -> a.getArgument(0));
            when(balanceRepo.findByWarehouseIdAndItemId(warehouseId, itemId)).thenReturn(Optional.empty());
            when(balanceRepo.save(any(StockBalance.class))).thenAnswer(a -> a.getArgument(0));
            when(movementRepo.save(any(StockMovement.class))).thenAnswer(a -> a.getArgument(0));
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingScan scan = service.scan(orderId,
                    new ReceivingService.ScanRequest("3760123456789", "EAN13", itemId, null,
                            BigDecimal.valueOf(5), "LOT-1", null, null, null, null),
                    UUID.randomUUID());

            assertThat(scan.getItemId()).isEqualTo(itemId);
            assertThat(scan.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(scan.getLotNumber()).isEqualTo("LOT-1");
            assertThat(line.getQuantityReceived()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(order.getStatus()).isEqualTo(ReceivingOrder.Status.RECEIVING);
            verify(movementRepo, times(1)).save(any(StockMovement.class));
        }
    }

    @Test
    @DisplayName("scan → produit inconnu créé à la volée avec son code-barres")
    void scanUnknownCreatesItem() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(order));

            when(inventoryService.resolveBarcode("3760")).thenReturn(Optional.empty());
            when(inventoryService.createItem(any(InventoryItem.class))).thenReturn(item);
            when(inventoryService.addBarcode(eq(itemId), eq("3760"), eq("QR"))).thenReturn(
                    ItemBarcode.builder().build());
            when(lineRepo.findByReceivingOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.empty());
            when(lineRepo.save(any(ReceivingOrderLine.class))).thenAnswer(a -> a.getArgument(0));
            when(scanRepo.save(any(ReceivingScan.class))).thenAnswer(a -> a.getArgument(0));
            when(balanceRepo.findByWarehouseIdAndItemId(warehouseId, itemId)).thenReturn(Optional.empty());
            when(balanceRepo.save(any(StockBalance.class))).thenAnswer(a -> a.getArgument(0));
            when(movementRepo.save(any(StockMovement.class))).thenAnswer(a -> a.getArgument(0));
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingScan scan = service.scan(orderId,
                    new ReceivingService.ScanRequest("3760", "QR", null,
                            new ReceivingService.CreateItem("Nouveau", "SKU-N", "84713000", "CN", "PCS"),
                            BigDecimal.ONE, null, null, null, null, null),
                    UUID.randomUUID());

            assertThat(scan.getItemId()).isEqualTo(itemId);
            verify(inventoryService, times(1)).createItem(any(InventoryItem.class));
            verify(inventoryService, times(1)).addBarcode(itemId, "3760", "QR");
        }
    }

    @Test
    @DisplayName("scan → produit inconnu sans payload création → ResourceNotFoundException")
    void scanUnknownNoCreate() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(order));
            when(inventoryService.resolveBarcode("9999")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.scan(orderId,
                    new ReceivingService.ScanRequest("9999", "EAN13", null, null,
                            BigDecimal.ONE, null, null, null, null, null),
                    UUID.randomUUID()))
                    .isInstanceOf(com.incokalk.exception.ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("complete → écart SHORT créé si reçu < attendu")
    void completeCreatesShortDiscrepancy() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            ReceivingOrder ord = ReceivingOrder.builder()
                    .id(orderId).companyId(companyId).warehouseId(warehouseId)
                    .orderNumber("RCT-0002").status(ReceivingOrder.Status.RECEIVING).build();
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(ord));

            ReceivingOrderLine line = ReceivingOrderLine.builder()
                    .id(UUID.randomUUID()).companyId(companyId).receivingOrderId(orderId)
                    .itemId(itemId).quantityExpected(BigDecimal.TEN)
                    .quantityReceived(BigDecimal.valueOf(4)).build();
            when(lineRepo.findByReceivingOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(line));
            when(discrepancyRepo.save(any(Discrepancy.class))).thenAnswer(a -> a.getArgument(0));
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingOrder completed = service.complete(orderId, UUID.randomUUID());

            assertThat(completed.getStatus()).isEqualTo(ReceivingOrder.Status.COMPLETED);
            assertThat(completed.getReceivedAt()).isNotNull();
            verify(discrepancyRepo, times(1)).save(any(Discrepancy.class));
        }
    }

    @Test
    @DisplayName("resolveDiscrepancy → statut RESOLVED")
    void resolveDiscrepancy() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID discId = UUID.randomUUID();
            Discrepancy d = Discrepancy.builder().id(discId).companyId(companyId)
                    .type(Discrepancy.Type.SHORT).build();
            when(discrepancyRepo.findById(discId)).thenReturn(Optional.of(d));
            when(discrepancyRepo.save(any(Discrepancy.class))).thenAnswer(a -> a.getArgument(0));
            Discrepancy resolved = service.resolveDiscrepancy(discId, "Accepté");
            assertThat(resolved.getResolutionStatus()).isEqualTo(Discrepancy.ResolutionStatus.RESOLVED);
        }
    }

    @Test
    @DisplayName("scan sur bon clôturé → IllegalArgumentException")
    void scanClosedOrder() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            ReceivingOrder closed = ReceivingOrder.builder().id(orderId).companyId(companyId)
                    .warehouseId(warehouseId).orderNumber("RCT-9").status(ReceivingOrder.Status.COMPLETED).build();
            when(orderRepo.findByCompanyIdAndId(companyId, orderId)).thenReturn(Optional.of(closed));
            assertThatThrownBy(() -> service.scan(orderId,
                    new ReceivingService.ScanRequest("X", "EAN13", itemId, null,
                            BigDecimal.ONE, null, null, null, null, null),
                    UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("createOrder avec shipmentId et sans lignes → dérive depuis shipment_items")
    void createOrder_fromShipmentItems() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            UUID shipmentId = UUID.randomUUID();
            UUID shipmentItemId = UUID.randomUUID();
            ShipmentOrder shipment = ShipmentOrder.builder()
                    .id(shipmentId).company(Company.builder().id(companyId).build())
                    .orderNumber("SHP-20260731-0001").status(ShipmentOrder.Status.IN_TRANSIT).build();
            ShipmentItem si = ShipmentItem.builder()
                    .id(shipmentItemId).shipmentId(shipmentId).itemId(itemId)
                    .quantity(new BigDecimal("20")).unit("PCS").build();

            when(shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)).thenReturn(Optional.of(shipment));
            when(shipmentItemRepo.findByShipmentId(shipmentId)).thenReturn(List.of(si));
            when(orderRepo.save(any(ReceivingOrder.class))).thenAnswer(a -> {
                ReceivingOrder o = a.getArgument(0);
                o.setId(orderId);
                return o;
            });
            when(inventoryService.getItem(itemId)).thenReturn(item);
            when(lineRepo.save(any(ReceivingOrderLine.class))).thenAnswer(a -> a.getArgument(0));

            ReceivingOrder created = service.createOrder(warehouseId, shipmentId, null, null, null, UUID.randomUUID());

            assertThat(created.getId()).isEqualTo(orderId);
            verify(lineRepo, times(1)).save(any(ReceivingOrderLine.class));
        }
    }
}
