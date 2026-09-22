package com.fleethub.dto;

import java.time.LocalDateTime;

/**
 * Position GPS d'un seul véhicule, pour FleetMarket (voir MarketplaceController
 * "/api/marketplace/vehicle-position") — jamais la flotte entière, un
 * camion à la fois, scopé à la société propriétaire de la clé appelante.
 */
public record VehiclePositionDto(
        String registration,
        boolean available,
        Double latitude,
        Double longitude,
        Double speedKph,
        LocalDateTime lastGpsUpdate
) {}
