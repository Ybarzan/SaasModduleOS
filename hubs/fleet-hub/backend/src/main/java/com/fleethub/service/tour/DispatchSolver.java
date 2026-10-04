package com.fleethub.service.tour;

import java.util.List;

/**
 * Répartition de commandes entre plusieurs véhicules (problème de tournées de
 * véhicules avec capacités et fenêtres horaires).
 * <p>
 * Convention des matrices : l'indice 0 est le dépôt, la commande {@code i}
 * (position dans {@code jobs}) est à l'indice {@code i + 1}. Les temps sont en
 * secondes depuis minuit, les distances en mètres.
 */
public interface DispatchSolver {

    /** Commande à servir : quantité, créneau (secondes, nullables) et durée sur place. */
    record Job(int quantity, Integer windowStart, Integer windowEnd, int serviceSeconds) {}

    /** Véhicule : capacité (null = illimitée) et amplitude de travail (secondes depuis minuit). */
    record Vehicle(Integer capacity, int shiftStart, int shiftEnd) {}

    /** Pour chaque véhicule, les positions des commandes dans l'ordre de passage ; et les commandes non placées. */
    record Result(List<List<Integer>> routes, List<Integer> unassigned, String solver) {}

    Result solve(long[][] durations, long[][] distances, List<Job> jobs, List<Vehicle> vehicles);
}
