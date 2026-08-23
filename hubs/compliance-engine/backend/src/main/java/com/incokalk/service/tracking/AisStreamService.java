package com.incokalk.service.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client WebSocket pour AISStream.io (https://aisstream.io) — flux mondial de
 * positions AIS gratuit, en temps réel. Maintient un cache en mémoire des
 * dernières positions connues par MMSI, servi ensuite via une simple requête
 * REST filtrée par zone (même schéma que le proxy OpenSky pour les vols).
 *
 * N'établit aucune connexion tant qu'aucune clé API n'est configurée
 * (incokalk.tracking.aisstream.api-key) — comportement identique aux autres
 * providers de suivi de ce module.
 */
@Slf4j
@Service
public class AisStreamService {

    private static final String STREAM_URL = "wss://stream.aisstream.io/v0/stream";
    private static final long STALE_AFTER_MINUTES = 20;

    @Value("${incokalk.tracking.aisstream.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LiveVesselPosition> positions = new ConcurrentHashMap<>();
    private volatile WebSocketSession session;

    public record LiveVesselPosition(
            String mmsi,
            String shipName,
            Double latitude,
            Double longitude,
            Double speedKnots,
            Double course,
            Integer heading,
            LocalDateTime updatedAt
    ) {}

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Distinct de isConfigured() : une clé présente mais invalide/expirée laisse le
     * WebSocket ouvert côté AISStream.io sans jamais transmettre de PositionReport,
     * sans frame d'erreur ni fermeture -- échec silencieux indiscernable de "connecté
     * mais aucun navire dans la zone" sans ce signal séparé. */
    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    @PostConstruct
    public void connect() {
        if (!isConfigured()) {
            log.info("[AisStream] Pas de clé API configurée — flux AIS désactivé");
            return;
        }
        openConnection();
    }

    @PreDestroy
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception ignored) {
                // fermeture best-effort à l'arrêt de l'application
            }
        }
    }

    /** Reconnecte automatiquement si la session s'est fermée (réseau, expiration du flux, etc.). */
    @Scheduled(fixedDelay = 30000)
    public void ensureConnected() {
        if (isConfigured() && (session == null || !session.isOpen())) {
            log.info("[AisStream] Reconnexion au flux AIS...");
            openConnection();
        }
    }

    /** Purge les positions trop anciennes pour ne pas afficher des navires fantômes sur la carte. */
    @Scheduled(fixedDelay = 60000)
    public void pruneStale() {
        LocalDateTime cutoff = LocalDateTime.now().minus(STALE_AFTER_MINUTES, ChronoUnit.MINUTES);
        positions.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(cutoff));
    }

    public Map<String, LiveVesselPosition> getPositions(double latMin, double latMax, double lonMin, double lonMax) {
        Map<String, LiveVesselPosition> result = new ConcurrentHashMap<>();
        positions.forEach((mmsi, pos) -> {
            if (pos.latitude() != null && pos.longitude() != null
                    && pos.latitude() >= latMin && pos.latitude() <= latMax
                    && pos.longitude() >= lonMin && pos.longitude() <= lonMax) {
                result.put(mmsi, pos);
            }
        });
        return result;
    }

    private void openConnection() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.execute(new AisStreamHandler(), STREAM_URL)
                    .whenComplete((s, ex) -> {
                        if (ex != null) {
                            log.warn("[AisStream] Échec de connexion: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("[AisStream] Erreur d'ouverture de connexion: {}", e.getMessage());
        }
    }

    // Etend AbstractWebSocketHandler (pas TextWebSocketHandler) car AISStream.io envoie
    // certains messages en frame binaire (payload JSON encode en UTF-8) — TextWebSocketHandler
    // fermait la connexion avec le code 1003 "Binary messages not supported" des qu'une frame
    // binaire arrivait, empechant tout flux de donnees malgre une cle API valide.
    private class AisStreamHandler extends AbstractWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession newSession) throws Exception {
            session = newSession;
            log.info("[AisStream] Connecté au flux AIS mondial");

            Map<String, Object> subscription = Map.of(
                    "APIKey", apiKey,
                    "BoundingBoxes", new double[][][]{{{-90, -180}, {90, 180}}},
                    "FilterMessageTypes", new String[]{"PositionReport"}
            );
            newSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(subscription)));
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) {
            processPayload(message.getPayload());
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession s, BinaryMessage message) {
            processPayload(new String(message.getPayload().array(), StandardCharsets.UTF_8));
        }

        private void processPayload(String payload) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                JsonNode meta = root.path("MetaData");
                JsonNode report = root.path("Message").path("PositionReport");
                if (meta.isMissingNode() || report.isMissingNode()) return;

                String mmsi = meta.path("MMSI").asText(null);
                if (mmsi == null || mmsi.isBlank()) return;

                LiveVesselPosition pos = new LiveVesselPosition(
                        mmsi,
                        meta.path("ShipName").asText("").trim(),
                        report.has("Latitude") ? report.get("Latitude").asDouble() : null,
                        report.has("Longitude") ? report.get("Longitude").asDouble() : null,
                        report.has("Sog") ? report.get("Sog").asDouble() : null,
                        report.has("Cog") ? report.get("Cog").asDouble() : null,
                        report.has("TrueHeading") ? report.get("TrueHeading").asInt() : null,
                        LocalDateTime.now()
                );
                positions.put(mmsi, pos);
            } catch (Exception e) {
                log.warn("[AisStream] Message illisible ignoré: {}", e.getMessage());
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            log.warn("[AisStream] Connexion fermée: {}", status);
            if (session == s) {
                session = null;
            }
        }

        @Override
        public void handleTransportError(WebSocketSession s, Throwable exception) {
            log.warn("[AisStream] Erreur de transport: {}", exception.getMessage());
        }
    }
}
