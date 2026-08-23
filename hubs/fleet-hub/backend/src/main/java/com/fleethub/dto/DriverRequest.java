package com.fleethub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record DriverRequest(
        @NotBlank(message = "Le prénom est obligatoire") String firstName,
        @NotBlank(message = "Le nom est obligatoire") String lastName,
        @NotBlank(message = "Le numéro de permis est obligatoire") String licenseNumber,
        @NotBlank(message = "Le téléphone est obligatoire") String phone,
        @Email(message = "Email invalide") String email,
        LocalDate hireDate,
        boolean active
) {}
