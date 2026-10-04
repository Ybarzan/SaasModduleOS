package com.fleethub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Le nom de l'entreprise est requis") @Size(max = 255, message = "Le nom de l'entreprise ne doit pas dépasser 255 caractères") String companyName,
        @NotBlank(message = "Le prénom est requis") @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères") String firstName,
        @NotBlank(message = "Le nom est requis") @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères") String lastName,
        @NotBlank @Email(message = "Email invalide") String email,
        @NotBlank @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères") String password,
        /** Facultatif (défaut POIDS_LOURD) : POIDS_LOURD, MESSAGERIE, COLLECTE_SANTE, MIXTE. */
        @Pattern(regexp = "POIDS_LOURD|MESSAGERIE|COLLECTE_SANTE|MIXTE", message = "Profil de flotte inconnu") String fleetProfile
) {
    /** Compatibilité : inscription sans profil explicite. */
    public RegisterRequest(String companyName, String firstName, String lastName, String email, String password) {
        this(companyName, firstName, lastName, email, password, null);
    }
}
