package com.incokalk.controller.shipment;

import com.incokalk.model.Company;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.tracking.AisStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/v1/tracking-map")
@RequiredArgsConstructor
@Tag(name = "Tracking Map", description = "Proxy pour cartes de suivi vaisseaux et vols")
@RequiresPlan(Company.Plan.STARTER)
public class TrackingMapController {

    private final RestTemplate restTemplate;
    private final AisStreamService aisStreamService;

    @Value("${incokalk.tracking.vessel.api-key:}")
    private String vesselApiKey;

    @Value("${incokalk.tracking.vessel.base-url:https://api.vesselapi.com}")
    private String vesselBaseUrl;

    @GetMapping("/flights")
    @Operation(summary = "Vols en temps réel (proxy OpenSky Network)")
    public ResponseEntity<Object> getFlights(
            @RequestParam(defaultValue = "25") double lamin,
            @RequestParam(defaultValue = "70") double lamax,
            @RequestParam(defaultValue = "-15") double lomin,
            @RequestParam(defaultValue = "30") double lomax) {
        try {
            String url = String.format(Locale.ROOT,
                "https://opensky-network.org/api/states/all?lamin=%.4f&lamax=%.4f&lomin=%.4f&lomax=%.4f",
                lamin, lamax, lomin, lomax);
            ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("[TrackingMap] OpenSky error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("states", List.of()));
        }
    }

    @GetMapping("/flights/aircraft/{icao24}")
    @Operation(summary = "Détail d'un avion par ICAO24")
    public ResponseEntity<Object> getAircraft(@PathVariable String icao24) {
        try {
            String url = "https://opensky-network.org/api/states/all?icao24=" + icao24;
            ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("[TrackingMap] OpenSky aircraft error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("states", List.of()));
        }
    }

    @GetMapping("/vessels/live")
    @Operation(summary = "Navires en direct dans une zone (flux AISStream.io)")
    public ResponseEntity<Object> getLiveVessels(
            @RequestParam(defaultValue = "35") double latMin,
            @RequestParam(defaultValue = "55") double latMax,
            @RequestParam(defaultValue = "-5") double lonMin,
            @RequestParam(defaultValue = "15") double lonMax) {
        if (!aisStreamService.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", false, "connected", false, "vessels", List.of()));
        }
        return ResponseEntity.ok(Map.of(
                "configured", true,
                "connected", aisStreamService.isConnected(),
                "vessels", aisStreamService.getPositions(latMin, latMax, lonMin, lonMax).values()));
    }

    @GetMapping("/vessels/search")
    @Operation(summary = "Recherche de navire par MMSI/nom")
    public ResponseEntity<Object> searchVessels(@RequestParam String query) {
        if (vesselApiKey == null || vesselApiKey.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        try {
            String url = vesselBaseUrl + "/v1/vessels?name=" + query;
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(vesselApiKey);
            var request = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Object> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, request, Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("[TrackingMap] Vessel search error: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/vessels/position/{mmsi}")
    @Operation(summary = "Position d'un navire par MMSI")
    public ResponseEntity<Object> getVesselPosition(@PathVariable String mmsi) {
        if (vesselApiKey == null || vesselApiKey.isBlank()) {
            return ResponseEntity.ok(Map.of());
        }
        try {
            String url = vesselBaseUrl + "/v1/vessels/" + mmsi + "/positions";
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(vesselApiKey);
            var request = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Object> response = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, request, Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.warn("[TrackingMap] Vessel position error: {}", e.getMessage());
            return ResponseEntity.ok(Map.of());
        }
    }
}
