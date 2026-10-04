package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FleetProfileRequest(
        @NotBlank
        @Pattern(regexp = "POIDS_LOURD|MESSAGERIE|COLLECTE_SANTE|MIXTE", message = "Profil de flotte inconnu")
        String fleetProfile
) {}
