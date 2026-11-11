package com.fleethub.dto.medical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Passage en traitement ou clôture (action corrective obligatoire pour clôturer). */
public record NonConformityUpdateRequest(
        @NotBlank @Pattern(regexp = "EN_TRAITEMENT|CLOTUREE", message = "Statut attendu : EN_TRAITEMENT ou CLOTUREE")
        String status,
        @Size(max = 1000) String correctiveAction
) {}
