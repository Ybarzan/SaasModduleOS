package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TripRequest(
        @NotNull(message = "Le chauffeur est obligatoire") Long driverId,
        @NotNull(message = "Le camion est obligatoire") Long truckId,
        @NotNull(message = "L'heure de départ est obligatoire") LocalDateTime startTime,
        @NotNull(message = "L'heure d'arrivée est obligatoire") LocalDateTime endTime,
        @NotNull(message = "La distance est obligatoire") Double distanceKm,
        Double cargoWeightTons,
        boolean loaded,
        @NotBlank(message = "Le statut est obligatoire") String status,
        boolean onTime
) {}
