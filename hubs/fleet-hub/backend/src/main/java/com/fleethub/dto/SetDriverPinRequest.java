package com.fleethub.dto;

import jakarta.validation.constraints.Pattern;

public record SetDriverPinRequest(
        @Pattern(regexp = "\\d{4}", message = "Le code doit contenir exactement 4 chiffres") String pin
) {}
