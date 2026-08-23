package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Le mot de passe est requis pour confirmer la suppression")
        String password) {
}
