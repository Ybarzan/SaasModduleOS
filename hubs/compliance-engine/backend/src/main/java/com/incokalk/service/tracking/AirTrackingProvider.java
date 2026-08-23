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
public class AirTrackingProvider implements TrackingProvider {

    private final RestTemplate restTemplate;

    @Value("${incokalk.tracking.aviationstack.api-key:}")
    private String apiKey;

    @Value("${incokalk.tracking.aviationstack.base-url:http://api.aviationstack.com/v1}")
    private String baseUrl;

    public AirTrackingProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProviderType() {
        return "AIR";
    }

    @Override
    public String getName() {
        return "AviationStack";
    }

    @Override
    public List<TrackingUpdate> getTrackingInfo(String trackingNumber, UUID companyId) {
        if (!isAvailable(companyId)) {
            return List.of();
        }
        try {
            String url = baseUrl + "/flights?access_key=" + apiKey + "&flight_iata=" + trackingNumber;

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, Map.class);

            List<TrackingUpdate> updates = new ArrayList<>();
            Map body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object data = body.get("data");
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> flight) {
                            String flightStatus = flight.get("flight_status") instanceof String s ? s : "unknown";

                            Map<?, ?> departure = flight.get("departure") instanceof Map ? (Map<?, ?>) flight.get("departure") : Map.of();
                            Map<?, ?> arrival = flight.get("arrival") instanceof Map ? (Map<?, ?>) flight.get("arrival") : Map.of();

                            String depAirport = departure.get("airport") instanceof String s ? s : "N/A";
                            String arrAirport = arrival.get("airport") instanceof String s ? s : "N/A";
                            Object depEst = departure.get("estimated") != null ? departure.get("estimated") : departure.get("scheduled");
                            Object arrEst = arrival.get("estimated") != null ? arrival.get("estimated") : arrival.get("scheduled");
                            String depTime = depEst instanceof String s ? s : "";
                            String arrTime = arrEst instanceof String s ? s : "";

                            updates.add(TrackingUpdate.builder()
                                    .status(mapFlightStatus(flightStatus))
                                    .location(depAirport + " → " + arrAirport)
                                    .latitude(extractDouble(departure, "latitude"))
                                    .longitude(extractDouble(departure, "longitude"))
                                    .description(String.format("Vol %s — %s → %s",
                                            flight.get("flight_iata") instanceof String f ? f : trackingNumber,
                                            depAirport, arrAirport))
                                    .eventTime(parseTime(depTime))
                                    .source("AviationStack")
                                    .build());

                            updates.add(TrackingUpdate.builder()
                                    .status("ARRIVAL_SCHEDULED")
                                    .location(arrAirport)
                                    .latitude(extractDouble(arrival, "latitude"))
                                    .longitude(extractDouble(arrival, "longitude"))
                                    .description("Arrivée estimée: " + arrTime)
                                    .eventTime(parseTime(arrTime))
                                    .source("AviationStack")
                                    .build());
                        }
                    }
                }
            }
            return updates;
        } catch (Exception e) {
            log.warn("Air tracking failed for flight {}: {}", trackingNumber, e.getMessage());
        }
        return List.of();
    }

    @Override
    public LivePosition getCurrentPosition(String trackingNumber, UUID companyId) {
        if (!isAvailable(companyId)) {
            return null;
        }
        try {
            String url = baseUrl + "/flights?access_key=" + apiKey + "&flight_iata=" + trackingNumber;

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, Map.class);

            Map body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object data = body.get("data");
                if (data instanceof List<?> list && !list.isEmpty()) {
                    Map<?, ?> flight = (Map<?, ?>) list.get(0);
                    Map<?, ?> departure = flight.get("departure") instanceof Map ? (Map<?, ?>) flight.get("departure") : Map.of();

                    return LivePosition.builder()
                            .latitude(extractDouble(departure, "latitude"))
                            .longitude(extractDouble(departure, "longitude"))
                            .speed(null)
                            .course(null)
                            .heading("N/A")
                            .timestamp(LocalDateTime.now())
                            .source("AviationStack")
                            .vesselName(flight.get("flight_iata") instanceof String f ? f : trackingNumber)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Air position failed for flight {}: {}", trackingNumber, e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isAvailable(UUID companyId) {
        return apiKey != null && !apiKey.isBlank();
    }

    private String mapFlightStatus(String status) {
        if (status == null) return "UNKNOWN";
        return switch (status.toLowerCase()) {
            case "active" -> "EN_VOL";
            case "scheduled" -> "PROGRAMME";
            case "landed" -> "ATTERRI";
            case "cancelled" -> "ANNULÉ";
            case "delayed" -> "RETARDÉ";
            case "diverted" -> "REDIRIGÉ";
            default -> status.toUpperCase();
        };
    }

    private Double extractDouble(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return LocalDateTime.now();
        try { return OffsetDateTime.parse(timeStr).toLocalDateTime(); } catch (Exception e) { return LocalDateTime.now(); }
    }
}
