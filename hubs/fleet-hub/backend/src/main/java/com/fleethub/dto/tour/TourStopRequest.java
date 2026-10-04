package com.fleethub.dto.tour;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record TourStopRequest(
        @NotNull(message = "Le site est obligatoire") Long siteId,
        @Pattern(regexp = "LIVRAISON|ENLEVEMENT|COLLECTE", message = "Type d'arrêt inconnu") String type,
        /** Facultatif : créneau d'ouverture du site par défaut. */
        LocalTime windowStart,
        LocalTime windowEnd,
        /** Facultatif : durée de passage du site, sinon 5 min. */
        @Min(0) @Max(480) Integer serviceMinutes,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        /** Quantité attendue (colis / sachets), facultative. */
        @Min(0) Integer expectedQuantity,
        @Size(max = 1000) String notes
) {}
