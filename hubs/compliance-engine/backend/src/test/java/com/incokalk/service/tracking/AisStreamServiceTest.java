package com.incokalk.service.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AisStreamService — Tests unitaires")
class AisStreamServiceTest {

    AisStreamService service;

    @BeforeEach
    void setUp() {
        service = new AisStreamService();
    }

    @Test
    @DisplayName("isConfigured retourne false quand la clé API est vide")
    void isConfigured_falseWhenBlank() {
        ReflectionTestUtils.setField(service, "apiKey", "");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retourne false quand la clé API est nulle")
    void isConfigured_falseWhenNull() {
        ReflectionTestUtils.setField(service, "apiKey", null);
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured retourne true quand la clé API est renseignée")
    void isConfigured_trueWhenPresent() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("connect() ne tente aucune connexion sans clé API configurée")
    void connect_noOpWhenNotConfigured() {
        ReflectionTestUtils.setField(service, "apiKey", "");
        service.connect();
        assertThat(service.getPositions(-90, 90, -180, 180)).isEmpty();
    }

    @Test
    @DisplayName("isConnected retourne false quand aucune session n'a jamais été ouverte")
    void isConnected_falseWhenNoSession() {
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    @DisplayName("isConnected retourne false quand la session existe mais est fermée -- distinct d'une clé simplement absente")
    void isConnected_falseWhenSessionClosed() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        ReflectionTestUtils.setField(service, "session", session);

        assertThat(service.isConnected()).isFalse();
    }

    @Test
    @DisplayName("isConnected retourne true quand la session est ouverte")
    void isConnected_trueWhenSessionOpen() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        ReflectionTestUtils.setField(service, "session", session);

        assertThat(service.isConnected()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private void putPosition(String mmsi, Double lat, Double lon, LocalDateTime updatedAt) {
        Map<String, AisStreamService.LiveVesselPosition> positions =
                (Map<String, AisStreamService.LiveVesselPosition>) ReflectionTestUtils.getField(service, "positions");
        positions.put(mmsi, new AisStreamService.LiveVesselPosition(
                mmsi, "Test Ship", lat, lon, 12.0, 90.0, 90, updatedAt));
    }

    @Test
    @DisplayName("getPositions filtre les navires par zone géographique")
    void getPositions_filtersByBoundingBox() {
        ReflectionTestUtils.setField(service, "positions", new ConcurrentHashMap<>());
        putPosition("111", 40.0, 5.0, LocalDateTime.now());
        putPosition("222", 60.0, 5.0, LocalDateTime.now());

        Map<String, AisStreamService.LiveVesselPosition> result = service.getPositions(35, 55, -5, 15);

        assertThat(result).containsOnlyKeys("111");
    }

    @Test
    @DisplayName("getPositions exclut les navires sans position connue")
    void getPositions_excludesMissingCoordinates() {
        ReflectionTestUtils.setField(service, "positions", new ConcurrentHashMap<>());
        putPosition("333", null, null, LocalDateTime.now());

        Map<String, AisStreamService.LiveVesselPosition> result = service.getPositions(-90, 90, -180, 180);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pruneStale supprime les positions trop anciennes")
    void pruneStale_removesOldEntries() {
        ReflectionTestUtils.setField(service, "positions", new ConcurrentHashMap<>());
        putPosition("old", 40.0, 5.0, LocalDateTime.now().minusMinutes(30));
        putPosition("fresh", 40.0, 5.0, LocalDateTime.now());

        service.pruneStale();

        Map<String, AisStreamService.LiveVesselPosition> result = service.getPositions(-90, 90, -180, 180);
        assertThat(result).containsOnlyKeys("fresh");
    }
}
