package com.fleethub.dto.tour;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record TourRequest(
        @NotBlank(message = "Le nom de la tournée est obligatoire") @Size(max = 255) String name,
        @NotNull(message = "La date est obligatoire") LocalDate date,
        Long driverId,
        Long truckId,
        Long depotId,
        /** Facultatif : 08:00 par défaut. */
        LocalTime plannedStart,
        @Size(max = 1000) String notes
) {}
