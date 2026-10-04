package com.fleethub.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TruckDto(
        Long id,
        String registration,
        String brand,
        String model,
        Integer year,
        String truckType,
        String fuelType,
        Double capacityTons,
        LocalDate acquisitionDate,
        Double purchasePrice,
        Double expectedConsumptionL100Km,
        String currentStatus,
        LocalDateTime lastGpsUpdate,
        boolean active,
        Long assignmentId,
        Long driverId,
        String driverName,
        /** Valeur saisie (null = déduite de la catégorie). */
        Boolean tachographEquipped,
        /** Valeur effective : véhicule soumis au 561/2006. */
        boolean requiresTachograph,
        /** Poids lourd (tracteur, porteur, fourgon PL) vs VUL / VL. */
        boolean heavy
) {}
