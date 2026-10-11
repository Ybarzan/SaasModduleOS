package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.model.Carrier;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.model.TrackingEvent;
import com.fleethub.model.Truck;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.repository.TrackingEventRepository;
import com.fleethub.service.tracking.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Suivi temps réel d'une expédition. Deux sources possibles, dans cet ordre :
 * <ol>
 *   <li><b>Camion de la flotte propre</b> ({@link ShipmentOrder#getTruck()} non nul) :
 *       la position vient directement du GPS du camion FleetHub
 *       ({@code currentLatitude}/{@code currentLongitude}/{@code currentSpeedKph}),
 *       sans appel externe — c'est l'amélioration rendue possible par la fusion
 *       des deux domaines dans la même application (voir docs de migration).</li>
 *   <li><b>Transporteur tiers</b> ({@link ShipmentOrder#getCarrier()}) : on retombe
 *       sur un {@link TrackingProvider} externe (AIS maritime, vol, Ship24 routier)
 *       résolu via {@link TrackingProviderRegistry}, comme avant la fusion.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTrackingService {

    private final TrackingProviderRegistry registry;
    private final ShipmentOrderRepository shipmentRepo;
    private final TrackingEventRepository trackingEventRepo;

    public List<TrackingUpdate> trackShipment(UUID shipmentId, Long companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);

        if (shipment.getTruck() != null) {
            TrackingUpdate update = fromTruck(shipment.getTruck());
            return update != null ? List.of(update) : List.of();
        }

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

    public LivePosition getLivePosition(UUID shipmentId, Long companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);

        if (shipment.getTruck() != null) {
            return positionFromTruck(shipment.getTruck());
        }

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

    public List<TrackingUpdate> trackByNumber(String trackingNumber, String mode, Long companyId) {
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
    public ShipmentOrder syncTrackingToEvents(UUID shipmentId, Long companyId) {
        ShipmentOrder shipment = loadShipment(shipmentId, companyId);

        List<TrackingUpdate> updates;
        if (shipment.getTruck() != null) {
            TrackingUpdate update = fromTruck(shipment.getTruck());
            updates = update != null ? List.of(update) : List.of();
        } else {
            String mode = detectMode(shipment);
            String trackingNumber = resolveTrackingNumber(shipment);
            if (trackingNumber == null || trackingNumber.isBlank()) {
                return shipment;
            }
            TrackingProvider provider = registry.getProvider(mode);
            if (provider == null) {
                return shipment;
            }
            updates = provider.getTrackingInfo(trackingNumber, companyId);
        }

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

    private TrackingUpdate fromTruck(Truck truck) {
        if (truck.getCurrentLatitude() == null || truck.getCurrentLongitude() == null) {
            return null;
        }
        return TrackingUpdate.builder()
                .status(truck.getCurrentStatus() != null ? truck.getCurrentStatus().name() : "ROULAGE")
                .location(truck.getRegistration())
                .latitude(truck.getCurrentLatitude())
                .longitude(truck.getCurrentLongitude())
                .description("Position GPS du camion " + truck.getRegistration())
                .eventTime(truck.getLastGpsUpdate() != null ? truck.getLastGpsUpdate() : java.time.LocalDateTime.now())
                .source("fleethub-gps")
                .build();
    }

    private LivePosition positionFromTruck(Truck truck) {
        if (truck.getCurrentLatitude() == null || truck.getCurrentLongitude() == null) {
            return null;
        }
        return LivePosition.builder()
                .latitude(truck.getCurrentLatitude())
                .longitude(truck.getCurrentLongitude())
                .speed(truck.getCurrentSpeedKph())
                .course(null)
                .heading("N/A")
                .timestamp(truck.getLastGpsUpdate())
                .source("fleethub-gps")
                .vesselName(truck.getRegistration())
                .build();
    }

    private ShipmentOrder loadShipment(UUID shipmentId, Long companyId) {
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
