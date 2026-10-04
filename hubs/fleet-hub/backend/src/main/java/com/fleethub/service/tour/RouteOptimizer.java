package com.fleethub.service.tour;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimisation de l'ordre de passage d'une tournée (problème de tournée de
 * véhicule à fenêtres horaires, version mono-véhicule).
 * <p>
 * Heuristique intégrée, sans dépendance externe : construction « plus proche
 * voisin » puis amélioration 2-opt, la fonction de coût combinant la distance
 * et une pénalité par minute de retard sur la fin de créneau. Les distances
 * sont à vol d'oiseau corrigées d'un facteur routier ; un moteur d'itinéraire
 * (OSRM / VROOM) pourra remplacer ce calcul derrière la même interface.
 * <p>
 * Les arrêts sans coordonnées GPS ne peuvent pas être optimisés : ils gardent
 * leur ordre relatif et sont placés en fin de tournée.
 */
@Component
public class RouteOptimizer {

    /** Coût d'une minute de retard, exprimé en km équivalents. */
    private static final double LATENESS_PENALTY_KM_PER_MIN = 2.0;
    private static final int MAX_2OPT_PASSES = 50;

    private final double averageSpeedKmh;
    private final double roadFactor;

    public RouteOptimizer(@Value("${app.tours.average-speed-kmh:35}") double averageSpeedKmh,
                          @Value("${app.tours.road-factor:1.3}") double roadFactor) {
        this.averageSpeedKmh = averageSpeedKmh;
        this.roadFactor = roadFactor;
    }

    /** Point de passage : coordonnées (nullables), créneau et durée de service. */
    public record Node(Double lat, Double lon, LocalTime windowStart, LocalTime windowEnd, int serviceMinutes) {
        boolean located() {
            return lat != null && lon != null;
        }
    }

    /** Dépôt de départ/retour (null = départ du premier arrêt, pas de retour). */
    public record Depot(double lat, double lon) {}

    /**
     * Plan calculé : {@code order} indexe la liste d'entrée ; les autres listes
     * suivent l'ordre de passage.
     */
    public record Plan(List<Integer> order, List<LocalTime> arrivals, List<Integer> latenessMinutes,
                       double distanceKm, int durationMinutes, int totalLatenessMinutes) {}

    /** Calcule le meilleur ordre trouvé puis son planning. */
    public Plan optimize(Depot depot, List<Node> nodes, LocalTime start) {
        List<Integer> located = new ArrayList<>();
        List<Integer> unlocated = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            (nodes.get(i).located() ? located : unlocated).add(i);
        }
        List<Integer> order = nearestNeighbour(depot, nodes, located);
        order = twoOpt(depot, nodes, order, start);
        order.addAll(unlocated);
        return schedule(depot, nodes, order, start);
    }

    /** Planning (heures d'arrivée, retards, distance) pour un ordre donné, sans réordonner. */
    public Plan schedule(Depot depot, List<Node> nodes, List<Integer> order, LocalTime start) {
        double clock = start.toSecondOfDay() / 60.0;
        double startClock = clock;
        double km = 0;
        int totalLateness = 0;
        Double prevLat = depot != null ? depot.lat() : null;
        Double prevLon = depot != null ? depot.lon() : null;
        List<LocalTime> arrivals = new ArrayList<>();
        List<Integer> lateness = new ArrayList<>();

        for (int idx : order) {
            Node n = nodes.get(idx);
            if (n.located() && prevLat != null) {
                double leg = distanceKm(prevLat, prevLon, n.lat(), n.lon());
                km += leg;
                clock += travelMinutes(leg);
            }
            if (n.windowStart() != null) {
                clock = Math.max(clock, minutes(n.windowStart()));
            }
            arrivals.add(toTime(clock));
            int late = n.windowEnd() != null ? (int) Math.max(0, Math.round(clock - minutes(n.windowEnd()))) : 0;
            lateness.add(late);
            totalLateness += late;
            clock += n.serviceMinutes();
            if (n.located()) {
                prevLat = n.lat();
                prevLon = n.lon();
            }
        }
        if (depot != null && prevLat != null && !order.isEmpty()) {
            double back = distanceKm(prevLat, prevLon, depot.lat(), depot.lon());
            km += back;
            clock += travelMinutes(back);
        }
        return new Plan(List.copyOf(order), arrivals, lateness,
                Math.round(km * 10) / 10.0, (int) Math.round(clock - startClock), totalLateness);
    }

    private List<Integer> nearestNeighbour(Depot depot, List<Node> nodes, List<Integer> candidates) {
        List<Integer> remaining = new ArrayList<>(candidates);
        List<Integer> route = new ArrayList<>();
        if (remaining.isEmpty()) {
            return route;
        }
        double curLat;
        double curLon;
        if (depot != null) {
            curLat = depot.lat();
            curLon = depot.lon();
        } else {
            // Sans dépôt : on part de l'arrêt au créneau le plus précoce
            Integer first = remaining.stream()
                    .min((a, b) -> compareWindows(nodes.get(a), nodes.get(b)))
                    .orElseThrow();
            remaining.remove(first);
            route.add(first);
            curLat = nodes.get(first).lat();
            curLon = nodes.get(first).lon();
        }
        while (!remaining.isEmpty()) {
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int idx : remaining) {
                Node n = nodes.get(idx);
                double d = distanceKm(curLat, curLon, n.lat(), n.lon());
                if (d < bestDist) {
                    bestDist = d;
                    best = idx;
                }
            }
            remaining.remove(Integer.valueOf(best));
            route.add(best);
            curLat = nodes.get(best).lat();
            curLon = nodes.get(best).lon();
        }
        return route;
    }

    private List<Integer> twoOpt(Depot depot, List<Node> nodes, List<Integer> route, LocalTime start) {
        List<Integer> best = new ArrayList<>(route);
        if (best.size() < 3) {
            return best;
        }
        double bestCost = cost(schedule(depot, nodes, best, start));
        boolean improved = true;
        int passes = 0;
        while (improved && passes++ < MAX_2OPT_PASSES) {
            improved = false;
            for (int i = 0; i < best.size() - 1; i++) {
                for (int k = i + 1; k < best.size(); k++) {
                    List<Integer> candidate = reverse(best, i, k);
                    double c = cost(schedule(depot, nodes, candidate, start));
                    if (c < bestCost - 1e-6) {
                        best = candidate;
                        bestCost = c;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private static List<Integer> reverse(List<Integer> route, int i, int k) {
        List<Integer> out = new ArrayList<>(route.subList(0, i));
        List<Integer> middle = new ArrayList<>(route.subList(i, k + 1));
        java.util.Collections.reverse(middle);
        out.addAll(middle);
        out.addAll(route.subList(k + 1, route.size()));
        return out;
    }

    private static double cost(Plan plan) {
        return plan.distanceKm() + plan.totalLatenessMinutes() * LATENESS_PENALTY_KM_PER_MIN;
    }

    private static int compareWindows(Node a, Node b) {
        LocalTime wa = a.windowStart() != null ? a.windowStart() : a.windowEnd();
        LocalTime wb = b.windowStart() != null ? b.windowStart() : b.windowEnd();
        if (wa == null && wb == null) return 0;
        if (wa == null) return 1;
        if (wb == null) return -1;
        return wa.compareTo(wb);
    }

    private double travelMinutes(double km) {
        return km / averageSpeedKmh * 60.0;
    }

    /** Distance routière estimée : orthodromie (haversine) × facteur routier. */
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a)) * roadFactor;
    }

    private static double minutes(LocalTime t) {
        return t.toSecondOfDay() / 60.0;
    }

    private static LocalTime toTime(double minutesOfDay) {
        int m = (int) Math.round(Math.min(minutesOfDay, 24 * 60 - 1));
        return LocalTime.of(m / 60, m % 60);
    }
}
