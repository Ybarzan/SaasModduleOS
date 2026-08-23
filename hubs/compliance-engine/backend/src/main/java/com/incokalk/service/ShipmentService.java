package com.incokalk.service;

import com.incokalk.dto.shipment.ShipmentOrderDTO;
import com.incokalk.dto.shipment.ShipmentStatusUpdateDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentOrderRepository shipmentRepo;
    private final TrackingEventRepository trackingEventRepo;
    private final CarrierRepository carrierRepo;
    private final ShippingRateRepository shippingRateRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final EventPublisher eventPublisher;
    private final ShipmentItemRepository shipmentItemRepo;
    private final InventoryService inventoryService;
    private final ClientUserRepository clientUserRepo;


    @Transactional
    public ShipmentOrder createShipment(ShipmentOrderDTO dto, UUID userId, UUID companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        User user = userId != null ? userRepo.findById(userId).orElse(null) : null;

        Carrier carrier = null;
        if (dto.getCarrierId() != null) {
            carrier = carrierRepo.findByIdAndCompanyId(dto.getCarrierId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transporteur non trouvé"));
        }

        ShippingRate shippingRate = null;
        if (dto.getShippingRateId() != null) {
            shippingRate = shippingRateRepo.findById(dto.getShippingRateId())
                    .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));
        }

        if (carrier == null && shippingRate != null && shippingRate.getCarrier() != null) {
            carrier = shippingRate.getCarrier();
        }

        UUID clientId = null;
        if (dto.getClientId() != null) {
            clientId = clientUserRepo.findByIdAndCompanyId(dto.getClientId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client introuvable"))
                    .getId();
        }

        String orderNumber = generateOrderNumber();

        Double quotedCost = dto.getQuotedCost();
        if (quotedCost == null && shippingRate != null) {
            double weight = dto.getWeightKg() != null ? dto.getWeightKg() : 0;
            double volume = dto.getVolumeM3() != null ? dto.getVolumeM3() : 0;
            quotedCost = shippingRate.getBaseRate()
                    + (shippingRate.getRatePerKg() * weight)
                    + (shippingRate.getRatePerCbm() * volume);
        }

        ShipmentOrder shipment = ShipmentOrder.builder()
                .company(company)
                .user(user)
                .clientId(clientId)
                .orderNumber(orderNumber)
                .status(ShipmentOrder.Status.DRAFT)
                .carrier(carrier)
                .shippingRate(shippingRate)
                .shipperName(dto.getShipperName())
                .shipperAddress(dto.getShipperAddress())
                .shipperCity(dto.getShipperCity())
                .shipperCountry(dto.getShipperCountry())
                .shipperPostalCode(dto.getShipperPostalCode())
                .consigneeName(dto.getConsigneeName())
                .consigneeAddress(dto.getConsigneeAddress())
                .consigneeCity(dto.getConsigneeCity())
                .consigneeCountry(dto.getConsigneeCountry())
                .consigneePostalCode(dto.getConsigneePostalCode())
                .goodsDescription(dto.getGoodsDescription())
                .goodsValue(dto.getGoodsValue())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "EUR")
                .weightKg(dto.getWeightKg())
                .volumeM3(dto.getVolumeM3())
                .packagesCount(dto.getPackagesCount() != null ? dto.getPackagesCount() : 1)
                .hsCode(dto.getHsCode())
                .incotermCode(dto.getIncotermCode())
                .isDangerous(dto.getIsDangerous() != null ? dto.getIsDangerous() : false)
                .quotedCost(quotedCost)
                .requestedPickupDate(dto.getRequestedPickupDate())
                .build();

        ShipmentOrder saved = shipmentRepo.save(shipment);
        if (dto.getItems() != null) {
            for (ShipmentOrderDTO.ShipmentOrderItemDTO itemDto : dto.getItems()) {
                UUID itemId = null;
                if (itemDto.getItemId() != null) {
                    inventoryService.getItem(itemDto.getItemId());
                    itemId = itemDto.getItemId();
                }
                ShipmentItem si = ShipmentItem.builder()
                        .companyId(companyId)
                        .shipmentId(saved.getId())
                        .itemId(itemId)
                        .sku(itemDto.getSku())
                        .name(itemDto.getName())
                        .description(itemDto.getDescription())
                        .hsCode(itemDto.getHsCode())
                        .quantity(itemDto.getQuantity() != null ? itemDto.getQuantity() : BigDecimal.ONE)
                        .unit(itemDto.getUnit() != null ? itemDto.getUnit() : "PCS")
                        .unitPrice(itemDto.getUnitPrice() != null ? itemDto.getUnitPrice() : BigDecimal.ZERO)
                        .build();
                shipmentItemRepo.save(si);
            }
        }
        eventPublisher.shipmentCreated(saved.getId(), orderNumber, companyId);
        return saved;
    }

    public List<ShipmentOrder> listShipments(UUID companyId) {
        return shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Page<ShipmentOrder> listShipments(UUID companyId, Pageable pageable) {
        return shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    public List<ShipmentOrder> listUserShipments(UUID userId) {
        return shipmentRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public ShipmentOrder updateStatus(UUID shipmentId, ShipmentStatusUpdateDTO dto, UUID companyId) {
        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
                .filter(s -> s.getCompany() != null && s.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        String oldStatus = shipment.getStatus().name();

        try {
            ShipmentOrder.Status newStatus = ShipmentOrder.Status.valueOf(dto.getStatus());
            validateStatusTransition(shipment.getStatus(), newStatus);
            shipment.setStatus(newStatus);

            if (newStatus == ShipmentOrder.Status.BOOKED) {
                shipment.setBookedAt(LocalDateTime.now());
            } else if (newStatus == ShipmentOrder.Status.IN_TRANSIT) {
                shipment.setShippedAt(LocalDateTime.now());
            } else if (newStatus == ShipmentOrder.Status.DELIVERED) {
                shipment.setActualDeliveryDate(LocalDateTime.now());
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide: " + dto.getStatus());
        }

        shipmentRepo.save(shipment);

        TrackingEvent event = TrackingEvent.builder()
                .shipment(shipment)
                .status(dto.getStatus())
                .location(dto.getLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .description(dto.getDescription())
                .source(dto.getSource() != null ? dto.getSource() : "manual")
                .dataSource(TrackingEvent.DataSource.MANUAL)
                .build();

        trackingEventRepo.save(event);

        eventPublisher.shipmentStatusChanged(shipmentId, shipment.getOrderNumber(),
                oldStatus, dto.getStatus(), companyId,
                shipment.getUser() != null ? shipment.getUser().getId() : null,
                TrackingEvent.DataSource.MANUAL);

        return shipment;
    }

    /**
     * Rattache (ou détache si clientId est null) une expédition à un compte client du
     * portail. Tant qu'une expédition n'est rattachée à aucun client, elle reste
     * invisible du portail client — voir ClientAuthService.getMyShipments.
     */
    @Transactional
    public ShipmentOrder assignClient(UUID shipmentId, UUID clientId, UUID companyId) {
        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
                .filter(s -> s.getCompany() != null && s.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        if (clientId != null) {
            clientUserRepo.findByIdAndCompanyId(clientId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client introuvable"));
        }

        shipment.setClientId(clientId);
        return shipmentRepo.save(shipment);
    }

    private static final java.util.Map<ShipmentOrder.Status, java.util.Set<ShipmentOrder.Status>> ALLOWED_TRANSITIONS =
        java.util.Map.of(
            ShipmentOrder.Status.DRAFT, java.util.Set.of(ShipmentOrder.Status.QUOTED, ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.CANCELLED),
            ShipmentOrder.Status.QUOTED, java.util.Set.of(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.DRAFT, ShipmentOrder.Status.CANCELLED),
            ShipmentOrder.Status.BOOKED, java.util.Set.of(ShipmentOrder.Status.IN_TRANSIT, ShipmentOrder.Status.CANCELLED),
            ShipmentOrder.Status.IN_TRANSIT, java.util.Set.of(ShipmentOrder.Status.DELIVERED, ShipmentOrder.Status.CANCELLED),
            ShipmentOrder.Status.DELIVERED, java.util.Set.of(),
            ShipmentOrder.Status.CANCELLED, java.util.Set.of()
        );

    private void validateStatusTransition(ShipmentOrder.Status current, ShipmentOrder.Status target) {
        if (current == target) return;
        java.util.Set<ShipmentOrder.Status> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, java.util.Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                "Transition de statut invalide : " + current + " -> " + target);
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrder getShipmentWithTracking(UUID shipmentId, UUID companyId) {
        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
                .filter(s -> s.getCompany() != null && s.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        List<TrackingEvent> events = trackingEventRepo.findByShipmentIdOrderByEventTimeDesc(shipmentId);
        Hibernate.initialize(shipment.getTrackingEvents());
        shipment.setTrackingEvents(events);

        return shipment;
    }

    @Transactional
    public void deleteShipment(UUID id, UUID companyId) {
        ShipmentOrder shipment = shipmentRepo.findById(id)
                .filter(s -> s.getCompany() != null && s.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));
        shipmentItemRepo.deleteByShipmentId(id);
        shipmentRepo.delete(shipment);
    }

    public Optional<ShipmentOrder> findByOrderNumber(String orderNumber) {
        return shipmentRepo.findByOrderNumber(orderNumber);
    }

    @Transactional
    public TrackingEvent addTrackingEvent(ShipmentOrder shipment, String status, String location,
                                           String description, String source, TrackingEvent.DataSource dataSource) {
        TrackingEvent event = TrackingEvent.builder()
            .shipment(shipment)
            .status(status)
            .location(location)
            .description(description)
            .eventTime(LocalDateTime.now())
            .source(source)
            .dataSource(dataSource)
            .build();
        return trackingEventRepo.save(event);
    }

    @Transactional
    public void processWebhookEvent(String trackingNumber, String status, String location,
                                     String description, String source) {
        findByOrderNumber(trackingNumber).ifPresent(shipment -> {
            UUID companyId = shipment.getCompany() != null ? shipment.getCompany().getId() : null;
            if (companyId == null) return;

            TenantContext.set(companyId);
            addTrackingEvent(shipment, status, location, description, source, TrackingEvent.DataSource.LIVE);

            String oldStatus = shipment.getStatus().name();
            ShipmentOrder.Status newStatus = mapWebhookStatus(status);
            if (newStatus != null && !oldStatus.equals(newStatus.name())) {
                shipment.setStatus(newStatus);
                shipmentRepo.save(shipment);
                eventPublisher.shipmentStatusChanged(
                    shipment.getId(), shipment.getOrderNumber(),
                    oldStatus, newStatus.name(), companyId,
                    shipment.getUser() != null ? shipment.getUser().getId() : null,
                    TrackingEvent.DataSource.LIVE);
            }

            log.info("Webhook {}: {} -> status '{}'", source, trackingNumber, status);
        });
    }

    private ShipmentOrder.Status mapWebhookStatus(String webhookStatus) {
        if (webhookStatus == null) return null;
        return switch (webhookStatus.toUpperCase()) {
            case "DELIVERED", "DELIVERY_NOTIFICATION" -> ShipmentOrder.Status.DELIVERED;
            case "IN_TRANSIT", "TRANSIT", "EN_ROUTE" -> ShipmentOrder.Status.IN_TRANSIT;
            case "PICKED_UP", "COLLECTED" -> ShipmentOrder.Status.BOOKED;
            case "FAILED_ATTEMPT", "EXCEPTION" -> ShipmentOrder.Status.IN_TRANSIT;
            default -> null;
        };
    }

    // ── Shipment items ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ShipmentItem> getShipmentItems(UUID shipmentId, UUID companyId) {
        shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));
        return shipmentItemRepo.findByShipmentId(shipmentId);
    }

    @Transactional
    public ShipmentItem addShipmentItem(UUID shipmentId, ShipmentOrderDTO.ShipmentOrderItemDTO dto, UUID companyId) {
        shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));
        UUID itemId = null;
        if (dto.getItemId() != null) {
            inventoryService.getItem(dto.getItemId());
            itemId = dto.getItemId();
        }
        ShipmentItem item = ShipmentItem.builder()
                .companyId(companyId)
                .shipmentId(shipmentId)
                .itemId(itemId)
                .sku(dto.getSku())
                .name(dto.getName())
                .description(dto.getDescription())
                .hsCode(dto.getHsCode())
                .quantity(dto.getQuantity() != null ? dto.getQuantity() : BigDecimal.ONE)
                .unit(dto.getUnit() != null ? dto.getUnit() : "PCS")
                .unitPrice(dto.getUnitPrice() != null ? dto.getUnitPrice() : BigDecimal.ZERO)
                .build();
        return shipmentItemRepo.save(item);
    }

    @Transactional
    public void deleteShipmentItems(UUID shipmentId, UUID companyId) {
        shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));
        shipmentItemRepo.deleteByShipmentId(shipmentId);
    }

    private String generateOrderNumber() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomDigits = String.format("%04d", RANDOM.nextInt(10000));
        return "SHP-" + dateStr + "-" + randomDigits;
    }
}
