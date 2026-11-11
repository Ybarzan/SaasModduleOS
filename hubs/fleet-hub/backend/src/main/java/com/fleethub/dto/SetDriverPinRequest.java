package com.fleethub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetDriverPinRequest(
        @NotNull(message = "Le code est obligatoire")
        @Pattern(regexp = "\\d{6}", message = "Le code doit contenir exactement 6 chiffres") String pin
) {}
