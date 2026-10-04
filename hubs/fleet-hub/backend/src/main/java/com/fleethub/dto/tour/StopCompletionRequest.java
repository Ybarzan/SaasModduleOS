package com.fleethub.dto.tour;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Clôture d'un arrêt par le chauffeur ou l'exploitant : preuve de passage. */
public record StopCompletionRequest(
        @NotBlank @Pattern(regexp = "FAIT|ECHEC", message = "Statut attendu : FAIT ou ECHEC") String status,
        @Size(max = 255) String signedBy,
        @Min(0) Integer parcelCount,
        @Min(0) Integer sampleCount,
        Double temperatureCelsius,
        @Size(max = 2000) String scannedCodes,
        @Size(max = 255) String failureReason,
        @Size(max = 1000) String notes,
        /** Heure réelle de la clôture, saisie hors connexion (bornée côté serveur) ; défaut : maintenant. */
        LocalDateTime occurredAt
) {
    public StopCompletionRequest(String status, String signedBy, Integer parcelCount, Integer sampleCount,
                                 Double temperatureCelsius, String scannedCodes, String failureReason, String notes) {
        this(status, signedBy, parcelCount, sampleCount, temperatureCelsius, scannedCodes, failureReason, notes, null);
    }
}
