package com.fleethub.dto.medical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Déclaration manuelle d'une non-conformité par l'exploitant. */
public record NonConformityRequest(
        Long tourId,
        Long tourStopId,
        @NotBlank
        @Pattern(regexp = "ECART_QUANTITE|ECART_TEMPERATURE|DELAI_ACHEMINEMENT|COLLECTE_NON_REALISEE|EMBALLAGE|AUTRE",
                message = "Type de non-conformité inconnu")
        String type,
        @NotBlank @Pattern(regexp = "MINEURE|MAJEURE|CRITIQUE", message = "Gravité inconnue") String severity,
        @NotBlank @Size(min = 5, max = 1000) String description
) {}
