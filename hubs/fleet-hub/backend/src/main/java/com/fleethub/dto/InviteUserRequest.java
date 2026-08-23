package com.fleethub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank(message = "Le prénom est requis") @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères") String firstName,
        @NotBlank(message = "Le nom est requis") @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères") String lastName,
        @NotBlank @Email(message = "Email invalide") String email,
        @Pattern(regexp = "ADMIN|GESTIONNAIRE", message = "Rôle invalide") String role
) {}
