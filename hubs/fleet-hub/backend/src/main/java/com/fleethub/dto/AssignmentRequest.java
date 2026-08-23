package com.fleethub.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignmentRequest(
        @NotNull(message = "Le chauffeur est obligatoire") Long driverId,
        @NotNull(message = "Le camion est obligatoire") Long truckId,
        @NotNull(message = "La date de début est obligatoire") LocalDate startDate,
        LocalDate endDate,
        boolean active
) {}
