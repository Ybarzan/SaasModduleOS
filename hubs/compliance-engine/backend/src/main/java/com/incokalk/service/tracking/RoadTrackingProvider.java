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
public class RoadTrackingProvider implements TrackingProvider {

    private final RestTemplate restTemplate;

    @Value("${incokalk.tracking.ship24.api-key:}")
    private String apiKey;

    @Value("${incokalk.tracking.ship24.base-url:https://api.ship24.com/public/v1}")
    private String baseUrl;

    public RoadTrackingProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProviderType() {
        return "ROAD";
    }

    @Override
    public String getName() {
        return "Ship24";
    }

    @Override
    public List<TrackingUpdate> getTrackingInfo(String trackingNumber, UUID companyId) {
        if (!isAvailable(companyId)) {
            return List.of();
        }
        try {
            String url = baseUrl + "/trackers/" + trackingNumber;
            HttpHeaders headers = new HttpHeaders();
            headers.set("apiKey", apiKey);
            headers.set("Accept", "application/json");

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            List<TrackingUpdate> updates = new ArrayList<>();
            Map body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object data = body.get("data");
                if (data instanceof Map<?, ?> dataMap) {
                    Object tracking = dataMap.get("tracking");
                    if (tracking instanceof Map<?, ?> trackMap) {
                        Object events = trackMap.get("events");
                        if (events instanceof List<?> eventList) {
                            for (Object item : eventList) {
                                if (item instanceof Map<?, ?> evt) {
                                    String status = evt.get("status") instanceof String s ? s : "UNKNOWN";
                                    String location = evt.get("location") instanceof String s ? s : "";
                                    String description = evt.get("description") instanceof String s ? s : "";
                                    updates.add(TrackingUpdate.builder()
                                            .status(status)
                                            .location(location)
                                            .latitude(extractDouble(evt, "latitude"))
                                            .longitude(extractDouble(evt, "longitude"))
                                            .description(description)
                                            .eventTime(extractTime(evt, "eventTime"))
                                            .source("Ship24")
                                            .build());
                                }
                            }
                        }
                    }
                }
            }
            return updates;
        } catch (Exception e) {
            log.warn("Road tracking failed for {}: {}", trackingNumber, e.getMessage());
        }
        return List.of();
    }

    @Override
    public LivePosition getCurrentPosition(String trackingNumber, UUID companyId) {
        List<TrackingUpdate> updates = getTrackingInfo(trackingNumber, companyId);
        if (!updates.isEmpty()) {
            TrackingUpdate latest = updates.get(0);
            if (latest.getLatitude() != null && latest.getLongitude() != null) {
                return LivePosition.builder()
                        .latitude(latest.getLatitude())
                        .longitude(latest.getLongitude())
                        .speed(null)
                        .course(null)
                        .heading("N/A")
                        .timestamp(latest.getEventTime() != null ? latest.getEventTime() : LocalDateTime.now())
                        .source("Ship24")
                        .vesselName(trackingNumber)
                        .build();
            }
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
}
