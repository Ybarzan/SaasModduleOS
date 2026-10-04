package com.fleethub.dto.tour;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Répartition automatique des commandes « à planifier » d'une journée entre plusieurs véhicules. */
public record DispatchRequest(
        @NotNull(message = "La date est obligatoire") LocalDate date,
        @NotNull(message = "Le dépôt est obligatoire") Long depotId,
        /** Départ du dépôt (défaut 08:00). */
        LocalTime start,
        /** Retour au dépôt au plus tard (défaut 18:00). */
        LocalTime end,
        /** Préfixe du nom des tournées créées (défaut « Tournée »). */
        @Size(max = 200) String namePrefix,
        /** Sous-ensemble de commandes à répartir (défaut : toutes celles à planifier ce jour-là). */
        List<Long> orderIds,
        @NotEmpty(message = "Sélectionnez au moins un véhicule") @Size(max = 50) @Valid List<VehicleSlot> vehicles
) {
    /** Un véhicule disponible : camion et/ou chauffeur, capacité en quantité (null = illimitée). */
    public record VehicleSlot(Long truckId, Long driverId, @Min(1) Integer capacity) {}
}
