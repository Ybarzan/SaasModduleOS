package com.fleethub.dto.tour;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.time.LocalDateTime;

/**
 * Arrivée sur site ; {@code occurredAt} = heure réelle si l'action a été faite hors connexion.
 * La position du téléphone (facultative) alimente le journal de garde du transport médical.
 */
public record ArriveRequest(
        LocalDateTime occurredAt,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude
) {
    public ArriveRequest(LocalDateTime occurredAt) {
        this(occurredAt, null, null);
    }
}
