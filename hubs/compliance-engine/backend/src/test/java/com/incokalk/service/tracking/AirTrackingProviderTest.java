package com.incokalk.service.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AirTrackingProvider — Tests unitaires")
class AirTrackingProviderTest {

    RestTemplate restTemplate;
    AirTrackingProvider provider;
    UUID companyId;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        provider = new AirTrackingProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", "http://api.aviationstack.com/v1");
        companyId = UUID.randomUUID();
    }

    private void mockResponse(Map<String, Object> body, HttpStatus status) {
        ResponseEntity<Map> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(Map.class)))
                .thenReturn(response);
    }

    private Map<String, Object> flight(String status, Map<String, Object> departure, Map<String, Object> arrival, String flightIata) {
        Map<String, Object> flight = new HashMap<>();
        flight.put("flight_status", status);
        flight.put("departure", departure);
        flight.put("arrival", arrival);
        flight.put("flight_iata", flightIata);
        return flight;
    }

    private Map<String, Object> airport(String airport, String estimated, String scheduled, Object lat, Object lon) {
        Map<String, Object> map = new HashMap<>();
        map.put("airport", airport);
        if (estimated != null) map.put("estimated", estimated);
        if (scheduled != null) map.put("scheduled", scheduled);
        if (lat != null) map.put("latitude", lat);
        if (lon != null) map.put("longitude", lon);
        return map;
    }

    // ---------- getProviderType / getName ----------

    @Test
    @DisplayName("getProviderType → AIR")
    void getProviderType() {
        assertThat(provider.getProviderType()).isEqualTo("AIR");
    }

    @Test
    @DisplayName("getName → AviationStack")
    void getName() {
        assertThat(provider.getName()).isEqualTo("AviationStack");
    }

    // ---------- isAvailable ----------

    @Test
    @DisplayName("isAvailable → clé API présente → true")
    void isAvailable_withKey() {
        assertThat(provider.isAvailable(companyId)).isTrue();
    }

    @Test
    @DisplayName("isAvailable → clé API vide → false")
    void isAvailable_blankKey() {
        ReflectionTestUtils.setField(provider, "apiKey", "");
        assertThat(provider.isAvailable(companyId)).isFalse();
    }

    @Test
    @DisplayName("isAvailable → clé API null → false")
    void isAvailable_nullKey() {
        ReflectionTestUtils.setField(provider, "apiKey", null);
        assertThat(provider.isAvailable(companyId)).isFalse();
    }

    // ---------- getTrackingInfo ----------

    @Test
    @DisplayName("getTrackingInfo → provider indisponible → liste vide, pas d'appel HTTP")
    void getTrackingInfo_notAvailable() {
        ReflectionTestUtils.setField(provider, "apiKey", "");
        List<TrackingUpdate> updates = provider.getTrackingInfo("AF123", companyId);
        assertThat(updates).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getTrackingInfo → succès, vol actif → deux mises à jour (départ + arrivée)")
    void getTrackingInfo_success_active() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF123");
        Map<String, Object> body = Map.of("data", List.of(flight));
        mockResponse(body, HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF123", companyId);

        assertThat(updates).hasSize(2);
        TrackingUpdate dep = updates.get(0);
        assertThat(dep.getStatus()).isEqualTo("EN_VOL");
        assertThat(dep.getLocation()).isEqualTo("CDG → JFK");
        assertThat(dep.getLatitude()).isEqualTo(49.0);
        assertThat(dep.getLongitude()).isEqualTo(2.5);
        assertThat(dep.getDescription()).contains("AF123").contains("CDG").contains("JFK");
        assertThat(dep.getSource()).isEqualTo("AviationStack");
        assertThat(dep.getEventTime()).isNotNull();

        TrackingUpdate arr = updates.get(1);
        assertThat(arr.getStatus()).isEqualTo("ARRIVAL_SCHEDULED");
        assertThat(arr.getLocation()).isEqualTo("JFK");
        assertThat(arr.getLatitude()).isEqualTo(40.6);
        assertThat(arr.getLongitude()).isEqualTo(-73.7);
        assertThat(arr.getDescription()).contains("Arrivée estimée");
    }

    @Test
    @DisplayName("getTrackingInfo → statut scheduled → PROGRAMME")
    void getTrackingInfo_scheduled() {
        Map<String, Object> departure = airport("CDG", null, "2026-08-11T10:00:00Z", 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", null, "2026-08-11T20:00:00Z", 40.6, -73.7);
        Map<String, Object> flight = flight("scheduled", departure, arrival, "AF124");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF124", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("PROGRAMME");
    }

    @Test
    @DisplayName("getTrackingInfo → statut landed → ATTERRI")
    void getTrackingInfo_landed() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("landed", departure, arrival, "AF125");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF125", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("ATTERRI");
    }

    @Test
    @DisplayName("getTrackingInfo → statut cancelled → ANNULÉ")
    void getTrackingInfo_cancelled() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("cancelled", departure, arrival, "AF126");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF126", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("ANNULÉ");
    }

    @Test
    @DisplayName("getTrackingInfo → statut delayed → RETARDÉ")
    void getTrackingInfo_delayed() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("delayed", departure, arrival, "AF127");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF127", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("RETARDÉ");
    }

    @Test
    @DisplayName("getTrackingInfo → statut diverted → REDIRIGÉ")
    void getTrackingInfo_diverted() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("diverted", departure, arrival, "AF128");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF128", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("REDIRIGÉ");
    }

    @Test
    @DisplayName("getTrackingInfo → statut inconnu → renvoyé en majuscules")
    void getTrackingInfo_unknownStatus() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("weird_status", departure, arrival, "AF129");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF129", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("WEIRD_STATUS");
    }

    @Test
    @DisplayName("getTrackingInfo → flight_status absent → UNKNOWN")
    void getTrackingInfo_missingStatus() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = new HashMap<>();
        flight.put("departure", departure);
        flight.put("arrival", arrival);
        flight.put("flight_iata", "AF130");
        // flight_status intentionally omitted -> not a String -> "unknown" -> mapFlightStatus("unknown") -> not null branch but lower-case "unknown" -> default -> "UNKNOWN"
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF130", companyId);
        assertThat(updates.get(0).getStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("getTrackingInfo → estimated absent, utilise scheduled")
    void getTrackingInfo_usesScheduledWhenNoEstimated() {
        Map<String, Object> departure = airport("CDG", null, "2026-08-11T10:00:00Z", 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", null, "2026-08-11T20:00:00Z", 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF131");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF131", companyId);
        assertThat(updates.get(0).getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("getTrackingInfo → departure/arrival manquants → valeurs par défaut N/A")
    void getTrackingInfo_missingDepartureArrival() {
        Map<String, Object> flight = new HashMap<>();
        flight.put("flight_status", "active");
        flight.put("flight_iata", "AF132");
        // no departure/arrival keys at all
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF132", companyId);
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).getLocation()).isEqualTo("N/A → N/A");
        assertThat(updates.get(0).getLatitude()).isNull();
        assertThat(updates.get(0).getLongitude()).isNull();
    }

    @Test
    @DisplayName("getTrackingInfo → coordonnées en String numérique → converties")
    void getTrackingInfo_stringCoordinates() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, "49.5", "2.55");
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF133");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF133", companyId);
        assertThat(updates.get(0).getLatitude()).isEqualTo(49.5);
        assertThat(updates.get(0).getLongitude()).isEqualTo(2.55);
    }

    @Test
    @DisplayName("getTrackingInfo → coordonnées en String non-numérique → null")
    void getTrackingInfo_invalidStringCoordinates() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, "not-a-number", "also-bad");
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF134");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF134", companyId);
        assertThat(updates.get(0).getLatitude()).isNull();
        assertThat(updates.get(0).getLongitude()).isNull();
    }

    @Test
    @DisplayName("getTrackingInfo → flight_iata absent → utilise trackingNumber dans description")
    void getTrackingInfo_missingFlightIata() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = new HashMap<>();
        flight.put("flight_status", "active");
        flight.put("departure", departure);
        flight.put("arrival", arrival);
        // flight_iata intentionally omitted
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF135", companyId);
        assertThat(updates.get(0).getDescription()).contains("AF135");
    }

    @Test
    @DisplayName("getTrackingInfo → temps de vol invalide → eventTime = now (fallback)")
    void getTrackingInfo_invalidTimeString() {
        Map<String, Object> departure = airport("CDG", "not-a-valid-date", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF136");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF136", companyId);
        assertThat(updates.get(0).getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("getTrackingInfo → item de la liste n'est pas une Map → ignoré")
    void getTrackingInfo_nonMapItemIgnored() {
        List<Object> data = new java.util.ArrayList<>();
        data.add("not-a-map");
        mockResponse(Map.of("data", data), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF137", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → data n'est pas une liste → liste vide")
    void getTrackingInfo_dataNotList() {
        mockResponse(Map.of("data", "unexpected-string"), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF138", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → body null → liste vide")
    void getTrackingInfo_nullBody() {
        ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(Map.class)))
                .thenReturn(response);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF139", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → statut HTTP erreur → liste vide")
    void getTrackingInfo_httpError() {
        mockResponse(Map.of("data", List.of()), HttpStatus.INTERNAL_SERVER_ERROR);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF140", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → exception réseau → liste vide, pas de propagation")
    void getTrackingInfo_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF141", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → data vide → liste vide")
    void getTrackingInfo_emptyData() {
        mockResponse(Map.of("data", List.of()), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("AF142", companyId);
        assertThat(updates).isEmpty();
    }

    // ---------- getCurrentPosition ----------

    @Test
    @DisplayName("getCurrentPosition → provider indisponible → null, pas d'appel HTTP")
    void getCurrentPosition_notAvailable() {
        ReflectionTestUtils.setField(provider, "apiKey", "   ");
        var position = provider.getCurrentPosition("AF200", companyId);
        assertThat(position).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getCurrentPosition → succès → position construite depuis le premier vol")
    void getCurrentPosition_success() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> arrival = airport("JFK", "2026-08-11T20:00:00Z", null, 40.6, -73.7);
        Map<String, Object> flight = flight("active", departure, arrival, "AF200");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("AF200", companyId);

        assertThat(position).isNotNull();
        assertThat(position.getLatitude()).isEqualTo(49.0);
        assertThat(position.getLongitude()).isEqualTo(2.5);
        assertThat(position.getSpeed()).isNull();
        assertThat(position.getCourse()).isNull();
        assertThat(position.getHeading()).isEqualTo("N/A");
        assertThat(position.getTimestamp()).isNotNull();
        assertThat(position.getSource()).isEqualTo("AviationStack");
        assertThat(position.getVesselName()).isEqualTo("AF200");
    }

    @Test
    @DisplayName("getCurrentPosition → departure manquant → coordonnées null")
    void getCurrentPosition_missingDeparture() {
        Map<String, Object> flight = new HashMap<>();
        flight.put("flight_status", "active");
        flight.put("flight_iata", "AF201");
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("AF201", companyId);

        assertThat(position).isNotNull();
        assertThat(position.getLatitude()).isNull();
        assertThat(position.getLongitude()).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → flight_iata absent → vesselName = trackingNumber")
    void getCurrentPosition_missingFlightIata() {
        Map<String, Object> departure = airport("CDG", "2026-08-11T10:00:00Z", null, 49.0, 2.5);
        Map<String, Object> flight = new HashMap<>();
        flight.put("flight_status", "active");
        flight.put("departure", departure);
        mockResponse(Map.of("data", List.of(flight)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("AF202", companyId);
        assertThat(position.getVesselName()).isEqualTo("AF202");
    }

    @Test
    @DisplayName("getCurrentPosition → data vide → null")
    void getCurrentPosition_emptyData() {
        mockResponse(Map.of("data", List.of()), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("AF203", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → data n'est pas une liste → null")
    void getCurrentPosition_dataNotList() {
        mockResponse(Map.of("data", "oops"), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("AF204", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → body null → null")
    void getCurrentPosition_nullBody() {
        ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(Map.class)))
                .thenReturn(response);

        LivePosition position = provider.getCurrentPosition("AF205", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → statut HTTP erreur → null")
    void getCurrentPosition_httpError() {
        mockResponse(Map.of("data", List.of()), HttpStatus.BAD_GATEWAY);

        LivePosition position = provider.getCurrentPosition("AF206", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → exception réseau → null, pas de propagation")
    void getCurrentPosition_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        LivePosition position = provider.getCurrentPosition("AF207", companyId);
        assertThat(position).isNull();
    }
}
