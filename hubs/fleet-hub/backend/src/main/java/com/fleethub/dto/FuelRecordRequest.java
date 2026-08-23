package com.fleethub.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FuelRecordRequest(
        @NotNull(message = "Le camion est obligatoire") Long truckId,
        @NotNull(message = "La date est obligatoire") LocalDate date,
        @NotNull(message = "Les litres sont obligatoires") Double liters,
        @NotNull(message = "Le montant est obligatoire") Double amount,
        Double odometerKm
) {}
