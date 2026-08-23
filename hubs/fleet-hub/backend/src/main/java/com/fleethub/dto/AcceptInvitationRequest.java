package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "Le token d'invitation est requis") @Size(max = 256, message = "Token d'invitation invalide") String token,
        @NotBlank @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères") String password
) {}
