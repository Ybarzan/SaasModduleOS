package com.fleethub.service.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchSolverTest {

    private static final int H8 = 8 * 3600;
    private static final int H18 = 18 * 3600;

    /** Points sur une ligne : distance = |i - j| km, 1 km = 120 s. Indice 0 = dépôt. */
    private static long[][][] lineMatrices(int n) {
        long[][] dist = new long[n + 1][n + 1];
        long[][] dur = new long[n + 1][n + 1];
        for (int a = 0; a <= n; a++) {
            for (int b = 0; b <= n; b++) {
                dist[a][b] = Math.abs(a - b) * 1000L;
                dur[a][b] = Math.abs(a - b) * 120L;
            }
        }
        return new long[][][]{dur, dist};
    }

    private final InsertionDispatchSolver solver = new InsertionDispatchSolver();

    @Test
    void respectsCapacity_andPlacesEverything() {
        long[][][] m = lineMatrices(6);
        List<DispatchSolver.Job> jobs = new ArrayList<>();
        for (int i = 0; i < 6; i++) jobs.add(new DispatchSolver.Job(4, null, null, 300));
        List<DispatchSolver.Vehicle> vehicles = List.of(
                new DispatchSolver.Vehicle(12, H8, H18), new DispatchSolver.Vehicle(12, H8, H18));

        DispatchSolver.Result r = solver.solve(m[0], m[1], jobs, vehicles);

        assertTrue(r.unassigned().isEmpty());
        Set<Integer> all = new HashSet<>();
        for (List<Integer> route : r.routes()) {
            assertTrue(route.size() * 4 <= 12, "capacité dépassée : " + route);
            all.addAll(route);
        }
        assertEquals(6, all.size(), "chaque commande est placée une seule fois");
    }

    @Test
    void reportsUnassigned_whenCapacityIsInsufficient() {
        long[][][] m = lineMatrices(3);
        List<DispatchSolver.Job> jobs = List.of(
                new DispatchSolver.Job(5, null, null, 300),
                new DispatchSolver.Job(5, null, null, 300),
                new DispatchSolver.Job(5, null, null, 300));
        DispatchSolver.Result r = solver.solve(m[0], m[1], jobs, List.of(new DispatchSolver.Vehicle(10, H8, H18)));
        assertEquals(2, r.routes().get(0).size());
        assertEquals(1, r.unassigned().size());
    }

    @Test
    void respectsTimeWindows() {
        long[][][] m = lineMatrices(2);
        // Le point le plus loin ferme à 08:06 : il doit être servi en premier (240 s de trajet)
        List<DispatchSolver.Job> jobs = List.of(
                new DispatchSolver.Job(1, null, null, 600),
                new DispatchSolver.Job(1, null, H8 + 360, 60));
        DispatchSolver.Result r = solver.solve(m[0], m[1], jobs, List.of(new DispatchSolver.Vehicle(null, H8, H18)));
        assertEquals(List.of(1, 0), r.routes().get(0));
        assertTrue(r.unassigned().isEmpty());
    }

    @Test
    void respectsShiftEnd() {
        long[][][] m = lineMatrices(1);
        // 10 h sur place : impossible dans une amplitude de 08:00 à 10:00
        List<DispatchSolver.Job> jobs = List.of(new DispatchSolver.Job(1, null, null, 10 * 3600));
        DispatchSolver.Result r = solver.solve(m[0], m[1], jobs, List.of(new DispatchSolver.Vehicle(null, H8, 10 * 3600)));
        assertEquals(List.of(0), r.unassigned());
    }

    @Test
    void vroomRequest_andResponseMapping() throws Exception {
        ObjectMapper om = new ObjectMapper();
        VroomDispatchSolver vroom = new VroomDispatchSolver(RestClient.create(), om, "http://vroom:3000");
        long[][][] m = lineMatrices(2);
        JsonNode req = vroom.buildRequest(m[0], m[1],
                List.of(new DispatchSolver.Job(2, H8, H8 + 3600, 300), new DispatchSolver.Job(1, null, null, 60)),
                List.of(new DispatchSolver.Vehicle(null, H8, H18)));
        assertEquals(1, req.get("vehicles").size());
        assertEquals(1, req.get("jobs").get(0).get("location_index").asInt());
        assertTrue(req.get("jobs").get(0).has("time_windows"));
        assertFalse(req.get("jobs").get(1).has("time_windows"));
        assertEquals(3, req.get("matrices").get("car").get("durations").size());

        JsonNode resp = om.readTree("{\"code\":0,\"routes\":[{\"vehicle\":0,\"steps\":["
                + "{\"type\":\"start\"},{\"type\":\"job\",\"job\":1},{\"type\":\"end\"}]}],\"unassigned\":[{\"id\":0}]}");
        DispatchSolver.Result r = vroom.parse(resp, 2, 1);
        assertEquals(List.of(1), r.routes().get(0));
        assertEquals(List.of(0), r.unassigned());
        assertEquals("VROOM", r.solver());
        assertEquals(null, vroom.parse(om.readTree("{\"code\":3,\"error\":\"x\"}"), 2, 1));
    }
}
