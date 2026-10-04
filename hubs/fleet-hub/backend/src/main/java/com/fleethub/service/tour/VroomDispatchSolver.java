package com.fleethub.service.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Répartition par VROOM (solveur open source de référence, conteneur
 * vroom-express) avec nos propres matrices de temps/distances : pas besoin
 * de moteur cartographique. Activé si {@code app.routing.vroom-url} est
 * renseigné ; en cas d'indisponibilité, bascule sur le solveur intégré.
 */
@Component
public class VroomDispatchSolver {

    private static final Logger log = LoggerFactory.getLogger(VroomDispatchSolver.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String url;

    public VroomDispatchSolver(RestClient restClient, ObjectMapper objectMapper,
                               @Value("${app.routing.vroom-url:}") String url) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.url = url;
    }

    public boolean isEnabled() {
        return url != null && !url.isBlank();
    }

    /** Requête VROOM (exposée pour les tests). */
    ObjectNode buildRequest(long[][] durations, long[][] distances,
                            List<DispatchSolver.Job> jobs, List<DispatchSolver.Vehicle> vehicles) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode vs = root.putArray("vehicles");
        for (int v = 0; v < vehicles.size(); v++) {
            DispatchSolver.Vehicle veh = vehicles.get(v);
            ObjectNode n = vs.addObject();
            n.put("id", v);
            n.put("start_index", 0);
            n.put("end_index", 0);
            n.putArray("capacity").add(veh.capacity() != null ? veh.capacity() : 1_000_000);
            n.putArray("time_window").add(veh.shiftStart()).add(veh.shiftEnd());
        }
        ArrayNode js = root.putArray("jobs");
        for (int j = 0; j < jobs.size(); j++) {
            DispatchSolver.Job job = jobs.get(j);
            ObjectNode n = js.addObject();
            n.put("id", j);
            n.put("location_index", j + 1);
            n.put("service", job.serviceSeconds());
            n.putArray("delivery").add(job.quantity());
            if (job.windowStart() != null || job.windowEnd() != null) {
                n.putArray("time_windows").addArray()
                        .add(job.windowStart() != null ? job.windowStart() : 0)
                        .add(job.windowEnd() != null ? job.windowEnd() : 86_399);
            }
        }
        ObjectNode car = root.putObject("matrices").putObject("car");
        car.set("durations", objectMapper.valueToTree(durations));
        car.set("distances", objectMapper.valueToTree(distances));
        return root;
    }

    /** Lecture de la réponse VROOM ; null si elle est inexploitable. */
    DispatchSolver.Result parse(JsonNode body, int jobCount, int vehicleCount) {
        if (body == null || body.path("code").asInt(-1) != 0) {
            return null;
        }
        List<List<Integer>> routes = new ArrayList<>();
        for (int v = 0; v < vehicleCount; v++) routes.add(new ArrayList<>());
        Set<Integer> placed = new HashSet<>();
        for (JsonNode r : body.path("routes")) {
            int v = r.path("vehicle").asInt();
            for (JsonNode step : r.path("steps")) {
                if ("job".equals(step.path("type").asText())) {
                    int j = step.path("job").asInt();
                    routes.get(v).add(j);
                    placed.add(j);
                }
            }
        }
        List<Integer> unassigned = new ArrayList<>();
        for (int j = 0; j < jobCount; j++) if (!placed.contains(j)) unassigned.add(j);
        return new DispatchSolver.Result(routes, unassigned, "VROOM");
    }

    /** Résout via VROOM ; null en cas d'échec (l'appelant bascule alors sur le solveur intégré). */
    public DispatchSolver.Result solve(long[][] durations, long[][] distances,
                                       List<DispatchSolver.Job> jobs, List<DispatchSolver.Vehicle> vehicles) {
        try {
            JsonNode body = restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(durations, distances, jobs, vehicles))
                    .retrieve()
                    .body(JsonNode.class);
            DispatchSolver.Result result = parse(body, jobs.size(), vehicles.size());
            if (result == null) {
                log.warn("Réponse VROOM inexploitable : {}", body);
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("VROOM indisponible ({}), bascule sur le solveur intégré", e.getMessage());
            return null;
        }
    }
}
