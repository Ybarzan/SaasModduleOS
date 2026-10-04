package com.fleethub.dto.tour;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record OrderRequest(
        @NotNull(message = "La date est obligatoire") LocalDate date,
        @NotNull(message = "Le site est obligatoire") Long siteId,
        @Size(max = 255) String reference,
        @Pattern(regexp = "LIVRAISON|ENLEVEMENT|COLLECTE", message = "Type inconnu") String type,
        LocalTime windowStart,
        LocalTime windowEnd,
        @Min(0) @Max(480) Integer serviceMinutes,
        @Min(0) @Max(100000) Integer quantity,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        @Size(max = 1000) String notes
) {}
