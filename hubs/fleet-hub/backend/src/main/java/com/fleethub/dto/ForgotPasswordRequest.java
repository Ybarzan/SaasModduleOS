package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "L'identifiant est requis") String username
) {}
