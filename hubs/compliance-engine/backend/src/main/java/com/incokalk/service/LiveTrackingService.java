package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Carrier;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.TrackingEventRepository;
import com.incokalk.service.tracking.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTrackingService {

    private final TrackingProviderRegistry registry;
    private final ShipmentOrderRepository shipmentRepo;
    private final TrackingEventRepository trackingEventRepo;

    public List<TrackingUpdate> trackShipment(UUID shipmentId, UUID companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);
        String mode = detectMode(shipment);
        String trackingNumber = resolveTrackingNumber(shipment);

        if (trackingNumber == null || trackingNumber.isBlank()) {
            return List.of();
        }

        TrackingProvider provider = registry.getProvider(mode);
        if (provider == null) {
            log.warn("No tracking provider for mode {}", mode);
            return List.of();
        }

        return provider.getTrackingInfo(trackingNumber, companyId);
    }

    public LivePosition getLivePosition(UUID shipmentId, UUID companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);
        String mode = detectMode(shipment);
        String trackingNumber = resolveTrackingNumber(shipment);

        if (trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }

        TrackingProvider provider = registry.getProvider(mode);
        if (provider == null) {
            return null;
        }

        return provider.getCurrentPosition(trackingNumber, companyId);
    }

    public List<TrackingUpdate> trackByNumber(String trackingNumber, String mode, UUID companyId) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return List.of();
        }

        TrackingProvider provider = registry.getProvider(mode);
        if (provider == null) {
            log.warn("No tracking provider for mode {}", mode);
            return List.of();
        }

        return provider.getTrackingInfo(trackingNumber, companyId);
    }

    @Transactional
    public ShipmentOrder syncTrackingToEvents(UUID shipmentId, UUID companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);
        String mode = detectMode(shipment);
        String trackingNumber = resolveTrackingNumber(shipment);

        if (trackingNumber == null || trackingNumber.isBlank()) {
            return shipment;
        }

        TrackingProvider provider = registry.getProvider(mode);
        if (provider == null) {
            return shipment;
        }

        List<TrackingUpdate> updates = provider.getTrackingInfo(trackingNumber, companyId);
        for (TrackingUpdate update : updates) {
            TrackingEvent event = TrackingEvent.builder()
                    .shipment(shipment)
                    .status(update.getStatus())
                    .location(update.getLocation())
                    .latitude(update.getLatitude())
                    .longitude(update.getLongitude())
                    .description(update.getDescription())
                    .eventTime(update.getEventTime())
                    .source(update.getSource())
                    .dataSource(TrackingEvent.DataSource.LIVE)
                    .build();
            trackingEventRepo.save(event);
        }

        List<TrackingEvent> allEvents = trackingEventRepo.findByShipmentIdOrderByEventTimeDesc(shipmentId);
        Hibernate.initialize(shipment.getTrackingEvents());
        shipment.setTrackingEvents(allEvents);
        return shipment;
    }

    private ShipmentOrder loadShipment(UUID shipmentId, UUID companyId) {
        return shipmentRepo.findById(shipmentId)
                .filter(s -> s.getCompany() != null && s.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));
    }

    private String detectMode(ShipmentOrder shipment) {
        Carrier carrier = shipment.getCarrier();
        if (carrier != null && carrier.getTransportModes() != null) {
            String modes = carrier.getTransportModes().toUpperCase();
            if (modes.contains("SEA")) return "MARITIME";
            if (modes.contains("AIR")) return "AIR";
            if (modes.contains("ROAD")) return "ROAD";
        }
        return "ROAD";
    }

    private String resolveTrackingNumber(ShipmentOrder shipment) {
        return shipment.getOrderNumber();
    }
}
