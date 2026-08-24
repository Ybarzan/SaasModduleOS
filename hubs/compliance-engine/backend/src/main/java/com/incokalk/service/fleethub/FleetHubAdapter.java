package com.incokalk.service.fleethub;

import com.incokalk.model.FleetHubConfig;
import com.incokalk.repository.FleetHubConfigRepository;
import com.incokalk.service.tracking.LivePosition;
import com.incokalk.service.tracking.TrackingProvider;
import com.incokalk.service.tracking.TrackingUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Position GPS de la flotte propre du client, via l'API REST de fleet-hub
 * (docs/07-integration-fleet-hub.md) -- même famille que AirTrackingProvider/
 * MaritimeTrackingProvider/RoadTrackingProvider, un type de plus dans
 * TrackingProviderRegistry sous "FLEET_HUB". Le "trackingNumber" ici est
 * l'immatriculation du camion (ShipmentOrder.fleetHubTruckRegistration).
 *
 * fleet-hub n'expose qu'un instantané (position courante), pas un historique
 * d'événements -- getTrackingInfo ne renvoie donc jamais qu'un seul élément
 * au maximum, contrairement aux autres providers qui peuvent en renvoyer
 * plusieurs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetHubAdapter implements TrackingProvider {

    private final FleetHubConfigRepository configRepo;
    private final FleetHubClient client;

    @Override
    public String getProviderType() {
        return "FLEET_HUB";
    }

    @Override
    public String getName() {
        return "Fleet Hub";
    }

    @Override
    public List<TrackingUpdate> getTrackingInfo(String registration, UUID companyId) {
        return findVehicle(registration, companyId)
                .map(v -> List.of(toTrackingUpdate(v)))
                .orElse(List.of());
    }

    @Override
    public LivePosition getCurrentPosition(String registration, UUID companyId) {
        return findVehicle(registration, companyId).map(this::toLivePosition).orElse(null);
    }

    @Override
    public boolean isAvailable(UUID companyId) {
        if (companyId == null) return false;
        return !configRepo.findByCompanyIdAndIsActiveTrue(companyId).isEmpty();
    }

    private Optional<FleetHubVehicle> findVehicle(String registration, UUID companyId) {
        if (registration == null || registration.isBlank() || companyId == null) {
            return Optional.empty();
        }
        List<FleetHubConfig> configs = configRepo.findByCompanyIdAndIsActiveTrue(companyId);
        if (configs.isEmpty()) {
            return Optional.empty();
        }
        String normalized = registration.trim().toUpperCase();
        for (FleetHubConfig config : configs) {
            try {
                Optional<FleetHubVehicle> match = client.getVehicles(config).stream()
                        .filter(v -> v.getRegistration() != null && v.getRegistration().trim().toUpperCase().equals(normalized))
                        .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            } catch (Exception e) {
                log.warn("[FleetHub] Échec récupération véhicules ({}): {}", config.getName(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private TrackingUpdate toTrackingUpdate(FleetHubVehicle v) {
        return TrackingUpdate.builder()
                .status(v.getStatus())
                .location(null)
                .latitude(v.getLatitude())
                .longitude(v.getLongitude())
                .description(v.getDriverName() != null ? "Chauffeur : " + v.getDriverName() : null)
                .eventTime(v.getLastGpsUpdate() != null ? v.getLastGpsUpdate() : LocalDateTime.now())
                .source("Fleet Hub")
                .build();
    }

    private LivePosition toLivePosition(FleetHubVehicle v) {
        return LivePosition.builder()
                .latitude(v.getLatitude())
                .longitude(v.getLongitude())
                .speed(v.getSpeedKph())
                .course(null)
                .heading(v.getStatus())
                .timestamp(v.getLastGpsUpdate() != null ? v.getLastGpsUpdate() : LocalDateTime.now())
                .source("Fleet Hub")
                .vesselName(v.getRegistration())
                .build();
    }
}
