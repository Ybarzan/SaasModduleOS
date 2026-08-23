package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255, message = "Le nom d'utilisateur ne doit pas dépasser 255 caractères") String username,
        @NotBlank @Size(max = 128, message = "Le mot de passe ne doit pas dépasser 128 caractères") String password,
        String totpCode
) {}
