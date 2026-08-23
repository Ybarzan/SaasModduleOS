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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MaritimeTrackingProvider — Tests unitaires")
class MaritimeTrackingProviderTest {

    RestTemplate restTemplate;
    MaritimeTrackingProvider provider;
    UUID companyId;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        provider = new MaritimeTrackingProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.vesselapi.com");
        companyId = UUID.randomUUID();
    }

    private void mockResponse(Map<String, Object> body, HttpStatus status) {
        ResponseEntity<Map> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(response);
    }

    private Map<String, Object> position(Object lat, Object lon, Object speed, Object course,
                                          String createdAt, String vesselName) {
        Map<String, Object> map = new HashMap<>();
        if (lat != null) map.put("lat", lat);
        if (lon != null) map.put("lon", lon);
        if (speed != null) map.put("speed", speed);
        if (course != null) map.put("course", course);
        if (createdAt != null) map.put("created_at", createdAt);
        if (vesselName != null) map.put("vessel_name", vesselName);
        return map;
    }

    // ---------- getProviderType / getName ----------

    @Test
    @DisplayName("getProviderType → MARITIME")
    void getProviderType() {
        assertThat(provider.getProviderType()).isEqualTo("MARITIME");
    }

    @Test
    @DisplayName("getName → Vessel API (AIS)")
    void getName() {
        assertThat(provider.getName()).isEqualTo("Vessel API (AIS)");
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
        ReflectionTestUtils.setField(provider, "apiKey", "   ");
        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getTrackingInfo → succès, position complète → mise à jour construite")
    void getTrackingInfo_success() {
        Map<String, Object> pos = position(49.1234, 2.5678, 12.5, 90.0,
                "2026-08-11T10:00:00Z", "MSC AURORA");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);

        assertThat(updates).hasSize(1);
        TrackingUpdate update = updates.get(0);
        assertThat(update.getStatus()).isEqualTo("POSITION_REPORT");
        assertThat(update.getLocation()).isEqualTo(String.format("%.4f, %.4f", 49.1234, 2.5678));
        assertThat(update.getLatitude()).isEqualTo(49.1234);
        assertThat(update.getLongitude()).isEqualTo(2.5678);
        assertThat(update.getDescription()).isEqualTo("Position AIS du navire");
        assertThat(update.getEventTime()).isNotNull();
        assertThat(update.getSource()).isEqualTo("VesselAPI");
    }

    @Test
    @DisplayName("getTrackingInfo → plusieurs positions → plusieurs mises à jour")
    void getTrackingInfo_multiplePositions() {
        Map<String, Object> pos1 = position(1.0, 2.0, null, null, "2026-08-11T10:00:00Z", null);
        Map<String, Object> pos2 = position(3.0, 4.0, null, null, "2026-08-11T11:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos1, pos2)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).hasSize(2);
    }

    @Test
    @DisplayName("getTrackingInfo → coordonnées manquantes → 'Position inconnue', lat/lon null")
    void getTrackingInfo_missingCoordinates() {
        Map<String, Object> pos = position(null, null, null, null, "2026-08-11T10:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getLocation()).isEqualTo("Position inconnue");
        assertThat(updates.get(0).getLatitude()).isNull();
        assertThat(updates.get(0).getLongitude()).isNull();
    }

    @Test
    @DisplayName("getTrackingInfo → seule la latitude est présente → 'Position inconnue'")
    void getTrackingInfo_onlyLatitudePresent() {
        Map<String, Object> pos = position(49.0, null, null, null, "2026-08-11T10:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getLocation()).isEqualTo("Position inconnue");
        assertThat(updates.get(0).getLatitude()).isEqualTo(49.0);
        assertThat(updates.get(0).getLongitude()).isNull();
    }

    @Test
    @DisplayName("getTrackingInfo → coordonnées en String numérique → converties")
    void getTrackingInfo_stringCoordinates() {
        Map<String, Object> pos = position("49.5", "2.55", null, null, "2026-08-11T10:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getLatitude()).isEqualTo(49.5);
        assertThat(updates.get(0).getLongitude()).isEqualTo(2.55);
    }

    @Test
    @DisplayName("getTrackingInfo → coordonnées en String non-numérique → null")
    void getTrackingInfo_invalidStringCoordinates() {
        Map<String, Object> pos = position("not-a-number", "also-bad", null, null,
                "2026-08-11T10:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getLatitude()).isNull();
        assertThat(updates.get(0).getLongitude()).isNull();
    }

    @Test
    @DisplayName("getTrackingInfo → created_at invalide → eventTime = now (fallback)")
    void getTrackingInfo_invalidTimeString() {
        Map<String, Object> pos = position(1.0, 2.0, null, null, "not-a-valid-date", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("getTrackingInfo → created_at absent → eventTime = now (fallback)")
    void getTrackingInfo_missingTime() {
        Map<String, Object> pos = position(1.0, 2.0, null, null, null, null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates.get(0).getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("getTrackingInfo → item de la liste n'est pas une Map → ignoré")
    void getTrackingInfo_nonMapItemIgnored() {
        List<Object> data = new java.util.ArrayList<>();
        data.add("not-a-map");
        mockResponse(Map.of("data", data), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → data n'est pas une liste → liste vide")
    void getTrackingInfo_dataNotList() {
        mockResponse(Map.of("data", "unexpected-string"), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → data absent → liste vide")
    void getTrackingInfo_missingDataKey() {
        mockResponse(Map.of("other", "value"), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → data vide → liste vide")
    void getTrackingInfo_emptyData() {
        mockResponse(Map.of("data", List.of()), HttpStatus.OK);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → body null → liste vide")
    void getTrackingInfo_nullBody() {
        ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(response);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → statut HTTP erreur → liste vide")
    void getTrackingInfo_httpError() {
        mockResponse(Map.of("data", List.of()), HttpStatus.INTERNAL_SERVER_ERROR);

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    @Test
    @DisplayName("getTrackingInfo → exception réseau → liste vide, pas de propagation")
    void getTrackingInfo_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<TrackingUpdate> updates = provider.getTrackingInfo("MMSI123", companyId);
        assertThat(updates).isEmpty();
    }

    // ---------- getCurrentPosition ----------

    @Test
    @DisplayName("getCurrentPosition → provider indisponible → null, pas d'appel HTTP")
    void getCurrentPosition_notAvailable() {
        ReflectionTestUtils.setField(provider, "apiKey", "");
        LivePosition position = provider.getCurrentPosition("MMSI200", companyId);
        assertThat(position).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("getCurrentPosition → succès complet → position construite depuis la dernière entrée")
    void getCurrentPosition_success() {
        Map<String, Object> pos = position(49.1234, 2.5678, 15.2, 90.0,
                "2026-08-11T10:00:00Z", "MSC AURORA");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI200", companyId);

        assertThat(position).isNotNull();
        assertThat(position.getLatitude()).isEqualTo(49.1234);
        assertThat(position.getLongitude()).isEqualTo(2.5678);
        assertThat(position.getSpeed()).isEqualTo(15.2);
        assertThat(position.getCourse()).isEqualTo(90.0);
        assertThat(position.getHeading()).isEqualTo("E");
        assertThat(position.getTimestamp()).isNotNull();
        assertThat(position.getSource()).isEqualTo("VesselAPI");
        assertThat(position.getVesselName()).isEqualTo("MSC AURORA");
    }

    @Test
    @DisplayName("getCurrentPosition → vessel_name absent → 'N/A'")
    void getCurrentPosition_missingVesselName() {
        Map<String, Object> pos = position(1.0, 2.0, null, null, "2026-08-11T10:00:00Z", null);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI201", companyId);
        assertThat(position.getVesselName()).isEqualTo("N/A");
    }

    @Test
    @DisplayName("getCurrentPosition → vessel_name n'est pas une String → 'N/A'")
    void getCurrentPosition_nonStringVesselName() {
        Map<String, Object> pos = position(1.0, 2.0, null, null, "2026-08-11T10:00:00Z", null);
        pos.put("vessel_name", 12345);
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI202", companyId);
        assertThat(position.getVesselName()).isEqualTo("N/A");
    }

    @Test
    @DisplayName("getCurrentPosition → course absent → heading 'N/A'")
    void getCurrentPosition_missingCourse() {
        Map<String, Object> pos = position(1.0, 2.0, null, null, "2026-08-11T10:00:00Z", "SHIP");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI203", companyId);
        assertThat(position.getCourse()).isNull();
        assertThat(position.getHeading()).isEqualTo("N/A");
    }

    @Test
    @DisplayName("getCurrentPosition → course 0 → heading 'N'")
    void getCurrentPosition_headingNorth() {
        Map<String, Object> pos = position(1.0, 2.0, null, 0.0, "2026-08-11T10:00:00Z", "SHIP");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI204", companyId);
        assertThat(position.getHeading()).isEqualTo("N");
    }

    @Test
    @DisplayName("getCurrentPosition → course 359 (proche 360) → heading 'N' (modulo 8)")
    void getCurrentPosition_headingWrapsAround() {
        Map<String, Object> pos = position(1.0, 2.0, null, 359.0, "2026-08-11T10:00:00Z", "SHIP");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI205", companyId);
        assertThat(position.getHeading()).isEqualTo("N");
    }

    @Test
    @DisplayName("getCurrentPosition → course 225 → heading 'SO'")
    void getCurrentPosition_headingSouthWest() {
        Map<String, Object> pos = position(1.0, 2.0, null, 225.0, "2026-08-11T10:00:00Z", "SHIP");
        mockResponse(Map.of("data", List.of(pos)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI206", companyId);
        assertThat(position.getHeading()).isEqualTo("SO");
    }

    @Test
    @DisplayName("getCurrentPosition → utilise la première position de la liste (list.get(0))")
    void getCurrentPosition_usesFirstEntry() {
        Map<String, Object> pos1 = position(1.0, 2.0, null, null, "2026-08-11T10:00:00Z", "FIRST");
        Map<String, Object> pos2 = position(3.0, 4.0, null, null, "2026-08-11T11:00:00Z", "SECOND");
        mockResponse(Map.of("data", List.of(pos1, pos2)), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI207", companyId);
        assertThat(position.getVesselName()).isEqualTo("FIRST");
    }

    @Test
    @DisplayName("getCurrentPosition → data vide → null")
    void getCurrentPosition_emptyData() {
        mockResponse(Map.of("data", List.of()), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI208", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → data n'est pas une liste → null")
    void getCurrentPosition_dataNotList() {
        mockResponse(Map.of("data", "oops"), HttpStatus.OK);

        LivePosition position = provider.getCurrentPosition("MMSI209", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → body null → null")
    void getCurrentPosition_nullBody() {
        ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(response);

        LivePosition position = provider.getCurrentPosition("MMSI210", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → statut HTTP erreur → null")
    void getCurrentPosition_httpError() {
        mockResponse(Map.of("data", List.of()), HttpStatus.BAD_GATEWAY);

        LivePosition position = provider.getCurrentPosition("MMSI211", companyId);
        assertThat(position).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition → exception réseau → null, pas de propagation")
    void getCurrentPosition_networkException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        LivePosition position = provider.getCurrentPosition("MMSI212", companyId);
        assertThat(position).isNull();
    }
}
