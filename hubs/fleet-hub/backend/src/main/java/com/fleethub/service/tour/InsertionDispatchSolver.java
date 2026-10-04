package com.fleethub.service.tour;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Solveur intégré (sans dépendance externe) : insertion parallèle au moindre
 * coût. À chaque itération, la commande dont la meilleure insertion faisable
 * coûte le moins de kilomètres supplémentaires est insérée à cette position,
 * tous véhicules confondus. Les contraintes de capacité, de créneau (fin de
 * créneau stricte) et d'amplitude du véhicule (retour au dépôt) sont respectées.
 * Moins performant que VROOM sur de gros volumes, mais toujours disponible.
 */
@Component
public class InsertionDispatchSolver implements DispatchSolver {

    @Override
    public Result solve(long[][] durations, long[][] distances, List<Job> jobs, List<Vehicle> vehicles) {
        List<List<Integer>> routes = new ArrayList<>();
        int[] loads = new int[vehicles.size()];
        for (int v = 0; v < vehicles.size(); v++) routes.add(new ArrayList<>());

        List<Integer> remaining = new ArrayList<>();
        for (int j = 0; j < jobs.size(); j++) remaining.add(j);

        while (!remaining.isEmpty()) {
            int bestJob = -1, bestVehicle = -1, bestPos = -1;
            long bestCost = Long.MAX_VALUE;
            for (int j : remaining) {
                Job job = jobs.get(j);
                for (int v = 0; v < vehicles.size(); v++) {
                    Vehicle veh = vehicles.get(v);
                    if (veh.capacity() != null && loads[v] + job.quantity() > veh.capacity()) continue;
                    List<Integer> route = routes.get(v);
                    for (int pos = 0; pos <= route.size(); pos++) {
                        int prev = pos == 0 ? 0 : route.get(pos - 1) + 1;
                        int next = pos == route.size() ? 0 : route.get(pos) + 1;
                        long added = distances[prev][j + 1] + distances[j + 1][next] - distances[prev][next];
                        if (added >= bestCost) continue;
                        List<Integer> candidate = new ArrayList<>(route);
                        candidate.add(pos, j);
                        if (feasible(candidate, durations, jobs, veh)) {
                            bestCost = added;
                            bestJob = j;
                            bestVehicle = v;
                            bestPos = pos;
                        }
                    }
                }
            }
            if (bestJob < 0) break; // plus aucune insertion faisable
            routes.get(bestVehicle).add(bestPos, bestJob);
            loads[bestVehicle] += jobs.get(bestJob).quantity();
            remaining.remove(Integer.valueOf(bestJob));
        }
        return new Result(routes, remaining, "intégré");
    }

    /** Simule la tournée : attente en début de créneau, fin de créneau stricte, retour avant fin d'amplitude. */
    static boolean feasible(List<Integer> route, long[][] durations, List<Job> jobs, Vehicle vehicle) {
        long clock = vehicle.shiftStart();
        int at = 0;
        for (int j : route) {
            Job job = jobs.get(j);
            clock += durations[at][j + 1];
            if (job.windowStart() != null) clock = Math.max(clock, job.windowStart());
            if (job.windowEnd() != null && clock > job.windowEnd()) return false;
            clock += job.serviceSeconds();
            at = j + 1;
        }
        clock += durations[at][0];
        return clock <= vehicle.shiftEnd();
    }
}
