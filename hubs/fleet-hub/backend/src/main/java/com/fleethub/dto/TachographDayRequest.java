package com.fleethub.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TachographDayRequest(
        @NotNull(message = "Le chauffeur est obligatoire") Long driverId,
        @NotNull(message = "La date est obligatoire") LocalDate date,
        @NotNull(message = "Les heures de conduite sont obligatoires") Double drivingHours,
        Double workHours,
        Double restMinutes,
        boolean compliant
) {}
