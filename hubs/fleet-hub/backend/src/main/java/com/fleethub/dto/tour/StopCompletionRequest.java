package com.fleethub.dto.tour;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

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
        LocalDateTime occurredAt,
        /** Transport médical : quantités par catégorie (SANG, PCR, URINE, AUTRE). */
        Map<@Pattern(regexp = "SANG|PCR|URINE|AUTRE", message = "Catégorie de produit inconnue") String,
                @NotNull @Min(0) @Max(9999) Integer> products,
        /** Position du téléphone au moment de la clôture (facultative). */
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        /** Motif obligatoire pour corriger un passage médical déjà clôturé. */
        @Size(max = 500) String correctionReason
) {
    public StopCompletionRequest(String status, String signedBy, Integer parcelCount, Integer sampleCount,
                                 Double temperatureCelsius, String scannedCodes, String failureReason, String notes) {
        this(status, signedBy, parcelCount, sampleCount, temperatureCelsius, scannedCodes, failureReason, notes,
                null, null, null, null, null);
    }
}
