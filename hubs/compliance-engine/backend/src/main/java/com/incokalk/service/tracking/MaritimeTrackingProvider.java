package com.incokalk.service.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Component
public class MaritimeTrackingProvider implements TrackingProvider {

    private final RestTemplate restTemplate;

    @Value("${incokalk.tracking.vessel.api-key:}")
    private String apiKey;

    @Value("${incokalk.tracking.vessel.base-url:https://api.vesselapi.com}")
    private String baseUrl;

    public MaritimeTrackingProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProviderType() {
        return "MARITIME";
    }

    @Override
    public String getName() {
        return "Vessel API (AIS)";
    }

    @Override
    public List<TrackingUpdate> getTrackingInfo(String trackingNumber, UUID companyId) {
        if (!isAvailable(companyId)) {
            return List.of();
        }
        try {
            String url = baseUrl + "/v1/vessels/" + trackingNumber + "/positions";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Accept", "application/json");

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                List<TrackingUpdate> updates = new ArrayList<>();
                Object data = body.get("data");
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            updates.add(TrackingUpdate.builder()
                                    .status("POSITION_REPORT")
                                    .location(formatCoords(map))
                                    .latitude(extractDouble(map, "lat"))
                                    .longitude(extractDouble(map, "lon"))
                                    .description("Position AIS du navire")
                                    .eventTime(extractTime(map, "created_at"))
                                    .source("VesselAPI")
                                    .build());
                        }
                    }
                }
                return updates;
            }
        } catch (Exception e) {
            log.warn("Maritime tracking failed for MMSI {}: {}", trackingNumber, e.getMessage());
        }
        return List.of();
    }

    @Override
    public LivePosition getCurrentPosition(String trackingNumber, UUID companyId) {
        if (!isAvailable(companyId)) {
            return null;
        }
        try {
            String url = baseUrl + "/v1/vessels/" + trackingNumber + "/positions";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Accept", "application/json");

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object data = body.get("data");
                if (data instanceof List<?> list && !list.isEmpty()) {
                    Map<?, ?> latest = (Map<?, ?>) list.get(0);
                    return LivePosition.builder()
                            .latitude(extractDouble(latest, "lat"))
                            .longitude(extractDouble(latest, "lon"))
                            .speed(extractDouble(latest, "speed"))
                            .course(extractDouble(latest, "course"))
                            .heading(formatHeading(extractDouble(latest, "course")))
                            .timestamp(extractTime(latest, "created_at"))
                            .source("VesselAPI")
                            .vesselName(latest.get("vessel_name") instanceof String s ? s : "N/A")
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Maritime position failed for MMSI {}: {}", trackingNumber, e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isAvailable(UUID companyId) {
        return apiKey != null && !apiKey.isBlank();
    }

    private Double extractDouble(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private LocalDateTime extractTime(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof String s) {
            try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception e) { return LocalDateTime.now(); }
        }
        return LocalDateTime.now();
    }

    private String formatCoords(Map<?, ?> map) {
        Double lat = extractDouble(map, "lat");
        Double lon = extractDouble(map, "lon");
        if (lat != null && lon != null) return String.format("%.4f, %.4f", lat, lon);
        return "Position inconnue";
    }

    private String formatHeading(Double course) {
        if (course == null) return "N/A";
        String[] dirs = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        int idx = (int) Math.round(course / 45.0) % 8;
        return dirs[idx];
    }
}
