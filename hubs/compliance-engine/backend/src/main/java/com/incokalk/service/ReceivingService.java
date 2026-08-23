package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivingService {

    private final ReceivingOrderRepository orderRepo;
    private final ReceivingOrderLineRepository lineRepo;
    private final ReceivingScanRepository scanRepo;
    private final DiscrepancyRepository discrepancyRepo;
    private final StockBalanceRepository balanceRepo;
    private final StockMovementRepository movementRepo;
    private final InventoryService inventoryService;
    private final ShipmentOrderRepository shipmentRepo;
    private final ShipmentItemRepository shipmentItemRepo;

    // ── Création / listing ─────────────────────────────────────────────

    @Transactional
    public ReceivingOrder createOrder(UUID warehouseId, UUID shipmentId, String reference,
                                      String notes, List<CreateLine> lines, UUID userId) {
        UUID companyId = TenantContext.get();

        ShipmentOrder shipment = null;
        if (shipmentId != null) {
            shipment = shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Expédition non trouvée"));
        }
        if (reference == null || reference.isBlank()) {
            reference = shipment != null
                    ? shipment.getOrderNumber()
                            + (shipment.getGoodsDescription() != null && !shipment.getGoodsDescription().isBlank()
                            ? " — " + shipment.getGoodsDescription() : "")
                    : null;
        }

        ReceivingOrder order = ReceivingOrder.builder()
                .companyId(companyId)
                .warehouseId(warehouseId)
                .shipmentId(shipmentId)
                .orderNumber(generateOrderNumber())
                .reference(reference)
                .notes(notes)
                .status(ReceivingOrder.Status.DRAFT)
                .createdBy(userId)
                .build();
        order = orderRepo.save(order);

        if (lines != null && !lines.isEmpty()) {
            for (CreateLine line : lines) {
                inventoryService.getItem(line.itemId());
                lineRepo.save(ReceivingOrderLine.builder()
                        .companyId(companyId)
                        .receivingOrderId(order.getId())
                        .itemId(line.itemId())
                        .quantityExpected(line.quantityExpected() != null ? line.quantityExpected() : BigDecimal.ZERO)
                        .unit(line.unit() != null ? line.unit() : "PCS")
                        .build());
            }
        } else if (shipmentId != null) {
            List<ShipmentItem> shipmentItems = shipmentItemRepo.findByShipmentId(shipmentId);
            for (ShipmentItem si : shipmentItems) {
                if (si.getItemId() != null) {
                    inventoryService.getItem(si.getItemId());
                }
                lineRepo.save(ReceivingOrderLine.builder()
                        .companyId(companyId)
                        .receivingOrderId(order.getId())
                        .itemId(si.getItemId())
                        .quantityExpected(si.getQuantity())
                        .unit(si.getUnit())
                        .build());
            }
        }
        return order;
    }

    public List<ReceivingOrder> listOrders(ReceivingOrder.Status status, UUID warehouseId, UUID shipmentId) {
        UUID companyId = TenantContext.get();
        if (shipmentId != null) {
            return orderRepo.findByCompanyIdAndShipmentIdOrderByCreatedAtDesc(companyId, shipmentId);
        }
        if (status != null) {
            return orderRepo.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, status);
        }
        if (warehouseId != null) {
            return orderRepo.findByCompanyIdAndWarehouseIdOrderByCreatedAtDesc(companyId, warehouseId);
        }
        return orderRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public OrderDetail getOrder(UUID id) {
        ReceivingOrder order = getOrderEntity(id);
        UUID companyId = TenantContext.get();
        List<ReceivingOrderLine> lines = lineRepo.findByReceivingOrderIdOrderByCreatedAtAsc(id);
        List<ReceivingScan> scans = scanRepo.findByReceivingOrderIdOrderByScannedAtAsc(id);
        List<Discrepancy> discrepancies = discrepancyRepo.findByReceivingOrderIdOrderByCreatedAtDesc(id);

        BigDecimal expected = lines.stream()
                .map(ReceivingOrderLine::getQuantityExpected).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = lines.stream()
                .map(ReceivingOrderLine::getQuantityReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
        long openDiscrepancies = discrepancies.stream()
                .filter(d -> d.getResolutionStatus() == Discrepancy.ResolutionStatus.OPEN).count();

        ShipmentBrief shipment = null;
        if (order.getShipmentId() != null) {
            ShipmentOrder sh = shipmentRepo.findByIdAndCompanyId(order.getShipmentId(), companyId).orElse(null);
            if (sh != null) {
                shipment = new ShipmentBrief(sh.getId(), sh.getOrderNumber(), sh.getStatus().name(),
                        sh.getGoodsDescription(), sh.getPackagesCount(), sh.getGoodsValue(), sh.getCurrency());
            }
        }

        return new OrderDetail(
                order, lines, scans, discrepancies, expected, received,
                openDiscrepancies, expected.subtract(received), shipment);
    }

    public List<Discrepancy> getDiscrepancies() {
        return discrepancyRepo.findByCompanyIdOrderByCreatedAtDesc(TenantContext.get());
    }

    // ── Lignes ─────────────────────────────────────────────────────────

    @Transactional
    public ReceivingOrderLine addLine(UUID orderId, UUID itemId, BigDecimal quantityExpected, String unit) {
        UUID companyId = TenantContext.get();
        ReceivingOrder order = getOrderEntity(orderId);
        if (order.getStatus() == ReceivingOrder.Status.COMPLETED
                || order.getStatus() == ReceivingOrder.Status.CANCELLED) {
            throw new IllegalArgumentException("Ce bon de réception est clôturé");
        }
        inventoryService.getItem(itemId);
        Optional<ReceivingOrderLine> existing = lineRepo.findByReceivingOrderIdAndItemId(orderId, itemId);
        if (existing.isPresent()) {
            ReceivingOrderLine line = existing.get();
            line.setQuantityExpected(line.getQuantityExpected().add(quantityExpected));
            return lineRepo.save(line);
        }
        return lineRepo.save(ReceivingOrderLine.builder()
                .companyId(companyId)
                .receivingOrderId(orderId)
                .itemId(itemId)
                .quantityExpected(quantityExpected)
                .unit(unit != null ? unit : "PCS")
                .build());
    }

    // ── Scan (point clé) ───────────────────────────────────────────────

    @Transactional
    public ReceivingScan scan(UUID orderId, ScanRequest req, UUID userId) {
        UUID companyId = TenantContext.get();
        ReceivingOrder order = getOrderEntity(orderId);
        if (order.getStatus() == ReceivingOrder.Status.COMPLETED
                || order.getStatus() == ReceivingOrder.Status.CANCELLED) {
            throw new IllegalArgumentException("Ce bon de réception est clôturé");
        }

        BigDecimal qty = req.quantity() != null ? req.quantity() : BigDecimal.ONE;
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("La quantité doit être positive");
        }

        // 1. Résoudre l'article : itemId direct, sinon code-barres, sinon création à la volée
        InventoryItem item = null;
        if (req.itemId() != null) {
            item = inventoryService.getItem(req.itemId());
        } else if (req.barcode() != null && !req.barcode().isBlank()) {
            item = inventoryService.resolveBarcode(req.barcode()).orElse(null);
        }
        if (item == null) {
            if (req.createItem() != null) {
                item = inventoryService.createItem(InventoryItem.builder()
                        .name(req.createItem().name())
                        .sku(req.createItem().sku())
                        .hsCode(req.createItem().hsCode())
                        .originCountry(req.createItem().originCountry())
                        .unit(req.createItem().unit() != null ? req.createItem().unit() : "PCS")
                        .build());
                if (req.barcode() != null && !req.barcode().isBlank()) {
                    inventoryService.addBarcode(item.getId(), req.barcode(), req.barcodeType());
                }
            } else {
                throw new ResourceNotFoundException(
                        "Produit inconnu pour ce code-barres. Créez l'article ou fournissez son identifiant.");
            }
        }

        // 2. Mise à jour de la ligne (reçue vs attendue) + écart éventuel
        Optional<ReceivingOrderLine> optLine = lineRepo.findByReceivingOrderIdAndItemId(orderId, item.getId());
        BigDecimal receivedTotal;
        if (optLine.isPresent()) {
            ReceivingOrderLine line = optLine.get();
            line.setQuantityReceived(line.getQuantityReceived().add(qty));
            lineRepo.save(line);
            receivedTotal = line.getQuantityReceived();
            if (receivedTotal.compareTo(line.getQuantityExpected()) > 0) {
                saveDiscrepancy(orderId, item.getId(), line.getId(), Discrepancy.Type.OVER,
                        line.getQuantityExpected(), receivedTotal, receivedTotal.subtract(line.getQuantityExpected()),
                        "Quantité reçue supérieure à la quantité attendue");
            }
        } else {
            ReceivingOrderLine line = lineRepo.save(ReceivingOrderLine.builder()
                    .companyId(companyId)
                    .receivingOrderId(orderId)
                    .itemId(item.getId())
                    .quantityExpected(BigDecimal.ZERO)
                    .quantityReceived(qty)
                    .unit(item.getUnit())
                    .build());
            saveDiscrepancy(orderId, item.getId(), line.getId(), Discrepancy.Type.UNEXPECTED,
                    BigDecimal.ZERO, qty, qty, "Produit reçu non prévu au bon de réception");
        }

        // 3. Enregistrer le scan
        ReceivingScan scan = ReceivingScan.builder()
                .companyId(companyId)
                .receivingOrderId(orderId)
                .lineId(optLine.map(ReceivingOrderLine::getId).orElse(null))
                .itemId(item.getId())
                .barcode(req.barcode())
                .quantity(qty)
                .lotNumber(req.lotNumber())
                .expiryDate(req.expiryDate())
                .serialNumber(req.serialNumber())
                .photoUrl(req.photoUrl())
                .notes(req.notes())
                .scannedBy(userId)
                .build();
        scan = scanRepo.save(scan);

        // 4. Poste stock (stock balance + mouvement)
        updateStock(order, item.getId(), qty, StockMovement.Type.RECEIPT, "Réception " + order.getOrderNumber(), userId);

        // 5. Passage en réception
        if (order.getStatus() == ReceivingOrder.Status.DRAFT) {
            order.setStatus(ReceivingOrder.Status.RECEIVING);
            orderRepo.save(order);
        }
        return scan;
    }

    @Transactional
    public Discrepancy reportDamage(UUID orderId, UUID itemId, BigDecimal qty, String notes, UUID userId) {
        UUID companyId = TenantContext.get();
        ReceivingOrder order = getOrderEntity(orderId);
        inventoryService.getItem(itemId);
        Optional<ReceivingOrderLine> optLine = lineRepo.findByReceivingOrderIdAndItemId(orderId, itemId);
        ReceivingOrderLine line = optLine.orElseGet(() -> lineRepo.save(ReceivingOrderLine.builder()
                .companyId(companyId)
                .receivingOrderId(orderId)
                .itemId(itemId)
                .quantityExpected(BigDecimal.ZERO)
                .unit("PCS")
                .build()));
        line.setQuantityDamaged(line.getQuantityDamaged().add(qty));
        lineRepo.save(line);

        updateStock(order, itemId, qty.negate(), StockMovement.Type.DAMAGED, "Marchandise endommagée à la réception", userId);

        return saveDiscrepancy(orderId, itemId, line.getId(), Discrepancy.Type.DAMAGED,
                line.getQuantityReceived(), line.getQuantityReceived().subtract(qty),
                qty, notes != null ? notes : "Marchandise endommagée à la réception");
    }

    // ── Clôture ────────────────────────────────────────────────────────

    @Transactional
    public ReceivingOrder complete(UUID orderId, UUID userId) {
        ReceivingOrder order = getOrderEntity(orderId);
        if (order.getStatus() == ReceivingOrder.Status.COMPLETED) {
            return order;
        }
        List<ReceivingOrderLine> lines = lineRepo.findByReceivingOrderIdOrderByCreatedAtAsc(orderId);
        for (ReceivingOrderLine line : lines) {
            BigDecimal diff = line.getQuantityExpected().subtract(line.getQuantityReceived());
            if (diff.signum() > 0) {
                saveDiscrepancy(orderId, line.getItemId(), line.getId(), Discrepancy.Type.SHORT,
                        line.getQuantityExpected(), line.getQuantityReceived(), diff.negate(),
                        "Quantité reçue inférieure à la quantité attendue");
            }
        }
        order.setStatus(ReceivingOrder.Status.COMPLETED);
        order.setReceivedBy(userId);
        order.setReceivedAt(LocalDateTime.now());
        log.info("Bon de réception {} clôturé", order.getOrderNumber());
        return orderRepo.save(order);
    }

    @Transactional
    public void cancel(UUID orderId) {
        ReceivingOrder order = getOrderEntity(orderId);
        if (order.getStatus() == ReceivingOrder.Status.COMPLETED) {
            throw new IllegalArgumentException("Impossible d'annuler un bon clôturé");
        }
        order.setStatus(ReceivingOrder.Status.CANCELLED);
        orderRepo.save(order);
    }

    // ── Écarts ─────────────────────────────────────────────────────────

    @Transactional
    public Discrepancy resolveDiscrepancy(UUID id, String notes) {
        UUID companyId = TenantContext.get();
        Discrepancy d = discrepancyRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Écart non trouvé"));
        if (!d.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("Écart non trouvé");
        }
        d.setResolutionStatus(Discrepancy.ResolutionStatus.RESOLVED);
        if (notes != null) {
            d.setNotes((d.getNotes() == null ? "" : d.getNotes() + " ") + notes);
        }
        return discrepancyRepo.save(d);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private ReceivingOrder getOrderEntity(UUID orderId) {
        return orderRepo.findByCompanyIdAndId(TenantContext.get(), orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon de réception non trouvé"));
    }

    private Discrepancy saveDiscrepancy(UUID orderId, UUID itemId, UUID lineId, Discrepancy.Type type,
                                        BigDecimal expected, BigDecimal actual, BigDecimal diff, String notes) {
        ReceivingOrder order = getOrderEntity(orderId);
        Optional<Discrepancy> open = discrepancyRepo.findByReceivingOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(d -> d.getType() == type && d.getItemId().equals(itemId)
                        && d.getResolutionStatus() == Discrepancy.ResolutionStatus.OPEN)
                .findFirst();
        Discrepancy d = open.orElseGet(() -> Discrepancy.builder()
                .companyId(TenantContext.get())
                .receivingOrderId(orderId)
                .itemId(itemId)
                .lineId(lineId)
                .type(type)
                .build());
        d.setExpectedQty(expected);
        d.setActualQty(actual);
        d.setDifference(diff);
        d.setNotes(notes);
        if (order.getStatus() == ReceivingOrder.Status.DRAFT) {
            order.setStatus(ReceivingOrder.Status.RECEIVING);
            orderRepo.save(order);
        }
        return discrepancyRepo.save(d);
    }

    private void updateStock(ReceivingOrder order, UUID itemId, BigDecimal qty,
                             StockMovement.Type type, String note, UUID userId) {
        UUID companyId = TenantContext.get();
        StockBalance balance = balanceRepo.findByWarehouseIdAndItemId(order.getWarehouseId(), itemId)
                .orElseGet(() -> StockBalance.builder()
                        .companyId(companyId)
                        .warehouseId(order.getWarehouseId())
                        .itemId(itemId)
                        .build());
        BigDecimal newQty = balance.getQuantityOnHand().add(qty);
        if (newQty.signum() < 0) {
            throw new IllegalArgumentException("Stock insuffisant (solde actuel: " + balance.getQuantityOnHand() + ")");
        }
        balance.setQuantityOnHand(newQty);
        balanceRepo.save(balance);

        movementRepo.save(StockMovement.builder()
                .companyId(companyId)
                .warehouseId(order.getWarehouseId())
                .itemId(itemId)
                .quantity(qty)
                .type(type)
                .referenceType("RECEIVING")
                .referenceId(order.getId())
                .note(note)
                .createdBy(userId)
                .build());
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
        return "RCT-" + date + "-" + random;
    }

    // ── Records ────────────────────────────────────────────────────────

    public record CreateLine(
            @jakarta.validation.constraints.NotNull UUID itemId,
            BigDecimal quantityExpected,
            String unit
    ) {}

    public record CreateItem(
            @jakarta.validation.constraints.NotBlank String name,
            String sku,
            String hsCode,
            String originCountry,
            String unit
    ) {}

    public record ScanRequest(
            String barcode,
            String barcodeType,
            UUID itemId,
            CreateItem createItem,
            BigDecimal quantity,
            String lotNumber,
            LocalDate expiryDate,
            String serialNumber,
            String photoUrl,
            String notes
    ) {}

    public record OrderDetail(
            ReceivingOrder order,
            List<ReceivingOrderLine> lines,
            List<ReceivingScan> scans,
            List<Discrepancy> discrepancies,
            BigDecimal totalExpected,
            BigDecimal totalReceived,
            long openDiscrepancyCount,
            BigDecimal remaining,
            ShipmentBrief shipment
    ) {}

    public record ShipmentBrief(
            UUID id,
            String orderNumber,
            String status,
            String goodsDescription,
            Integer packagesCount,
            Double goodsValue,
            String currency
    ) {}
}
