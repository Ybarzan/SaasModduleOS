package com.fleethub.dto.medical;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Réglages du transport médical de la société. */
public record MedicalSettingsDto(
        /** Délai maximal entre prise en charge et remise au laboratoire (15 min à 24 h). */
        @NotNull @Min(15) @Max(1440) Integer maxTransitMinutes
) {}
