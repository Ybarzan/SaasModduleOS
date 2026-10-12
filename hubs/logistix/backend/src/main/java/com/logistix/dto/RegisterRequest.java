package com.logistix.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Le nom de l'entreprise est requis") @Size(max = 255) String companyName,
        @NotBlank(message = "Le nom est requis") @Size(max = 200) String displayName,
        @NotBlank @Email(message = "Email invalide") String email,
        @NotBlank @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères") String password
) {}
