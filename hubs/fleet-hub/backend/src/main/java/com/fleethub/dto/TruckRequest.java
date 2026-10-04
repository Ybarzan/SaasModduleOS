package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record TruckRequest(
        @NotBlank(message = "L'immatriculation est obligatoire") String registration,
        @NotBlank(message = "La marque est obligatoire") String brand,
        @NotBlank(message = "Le modèle est obligatoire") String model,
        Integer modelYear,
        @NotBlank(message = "Le type est obligatoire") String truckType,
        @NotBlank(message = "L'énergie est obligatoire") String fuelType,
        Double capacityTons,
        LocalDate acquisitionDate,
        Double purchasePrice,
        /** Facultatif : à défaut, valeur type de la catégorie de véhicule. */
        Double expectedConsumptionL100Km,
        boolean active,
        /** Facultatif : null = déduit de la catégorie (poids lourd => équipé). */
        Boolean tachographEquipped
) {}
