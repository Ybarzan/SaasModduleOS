package com.fleethub.service.tour;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteOptimizerTest {

    private final RouteOptimizer optimizer = new RouteOptimizer(30, 1.0);
    private static final RouteOptimizer.Depot DEPOT = new RouteOptimizer.Depot(45.0, 5.0);

    /** Points alignés vers l'est, donnés dans le désordre. */
    private static RouteOptimizer.Node east(double lonOffset) {
        return new RouteOptimizer.Node(45.0, 5.0 + lonOffset, null, null, 5);
    }

    @Test
    void optimize_visitsAlignedPointsInGeographicOrder() {
        List<RouteOptimizer.Node> nodes = List.of(east(0.30), east(0.10), east(0.40), east(0.20));
        RouteOptimizer.Plan plan = optimizer.optimize(DEPOT, nodes, LocalTime.of(8, 0));
        assertEquals(List.of(1, 3, 0, 2), plan.order());
    }

    @Test
    void optimize_neverWorseThanInputOrder() {
        List<RouteOptimizer.Node> nodes = new ArrayList<>();
        double[][] pts = {{45.10, 5.20}, {44.90, 4.80}, {45.20, 4.90}, {44.80, 5.30}, {45.05, 5.05}, {44.95, 4.95}};
        for (double[] p : pts) nodes.add(new RouteOptimizer.Node(p[0], p[1], null, null, 5));
        List<Integer> naive = List.of(0, 1, 2, 3, 4, 5);
        double naiveKm = optimizer.schedule(DEPOT, nodes, naive, LocalTime.of(8, 0)).distanceKm();
        double optimizedKm = optimizer.optimize(DEPOT, nodes, LocalTime.of(8, 0)).distanceKm();
        assertTrue(optimizedKm <= naiveKm, optimizedKm + " > " + naiveKm);
    }

    @Test
    void optimize_respectsTimeWindowsOverDistance() {
        // Le point le plus éloigné doit être servi avant 08:20 : il passe en premier
        List<RouteOptimizer.Node> nodes = List.of(
                east(0.05),
                new RouteOptimizer.Node(45.0, 5.15, null, LocalTime.of(8, 25), 5),
                east(0.10));
        RouteOptimizer.Plan plan = optimizer.optimize(DEPOT, nodes, LocalTime.of(8, 0));
        assertEquals(1, plan.order().get(0));
        assertEquals(0, plan.totalLatenessMinutes());
    }

    @Test
    void schedule_waitsForWindowStart_andReportsLateness() {
        List<RouteOptimizer.Node> nodes = List.of(
                new RouteOptimizer.Node(45.0, 5.0, LocalTime.of(9, 0), LocalTime.of(10, 0), 10),
                new RouteOptimizer.Node(45.0, 5.0, null, LocalTime.of(9, 5), 5));
        RouteOptimizer.Plan plan = optimizer.schedule(DEPOT, nodes, List.of(0, 1), LocalTime.of(8, 0));
        assertEquals(LocalTime.of(9, 0), plan.arrivals().get(0));
        assertEquals(LocalTime.of(9, 10), plan.arrivals().get(1));
        assertEquals(5, plan.latenessMinutes().get(1));
    }

    @Test
    void optimize_keepsUnlocatedStopsAtTheEnd() {
        List<RouteOptimizer.Node> nodes = List.of(
                new RouteOptimizer.Node(null, null, null, null, 5),
                east(0.2),
                east(0.1));
        RouteOptimizer.Plan plan = optimizer.optimize(DEPOT, nodes, LocalTime.of(8, 0));
        assertEquals(List.of(2, 1, 0), plan.order());
    }

    @Test
    void distance_parisLyon_isRealistic() {
        double km = new RouteOptimizer(35, 1.0).distanceKm(48.8566, 2.3522, 45.7640, 4.8357);
        assertTrue(km > 380 && km < 410, "Paris–Lyon à vol d'oiseau ≈ 392 km, obtenu " + km);
    }
}
