package com.fleethub.dto;

import java.util.List;

/**
 * Réponse de GET /api/marketplace/availability, consommée par FleetMarket.
 * Aucune donnée nominative de chauffeur : uniquement un score de conformité
 * agrégé et les camions disponibles (voir MarketplaceService#availability).
 */
public record MarketplaceAvailabilityDto(
        String companyName,
        String city,
        /** % de jours tachygraphe conformes sur les 30 derniers jours, arrondi. Null si aucune donnée. */
        Integer complianceScore,
        List<AvailableTruckDto> trucksAvailable
) {
    public record AvailableTruckDto(
            Long truckId,
            String registration,
            Double capacityTons
    ) {}
}
