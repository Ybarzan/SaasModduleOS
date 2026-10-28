package com.fleethub.service.tracking;

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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client WebSocket pour AISStream.io (https://aisstream.io) — flux mondial de
 * positions AIS gratuit, en temps réel. Maintient un cache en mémoire des
 * dernières positions connues par MMSI, servi ensuite via une simple requête
 * REST filtrée par zone (même schéma que le proxy OpenSky pour les vols).
 * <p>
 * N'établit aucune connexion tant qu'aucune clé API n'est configurée
 * (fleethub.tracking.aisstream.api-key) — comportement identique aux autres
 * providers de suivi de ce module.
 */
@Slf4j
@Service
public class AisStreamService {

    private static final String STREAM_URL = "wss://stream.aisstream.io/v0/stream";
    private static final long STALE_AFTER_MINUTES = 20;
    /** Les messages ShipStaticData (type 5) sont diffusés beaucoup moins
     *  souvent que les positions (toutes les ~6 minutes selon le standard AIS,
     *  parfois plus espacé en pratique) -- fenêtre de péremption plus large
     *  que STALE_AFTER_MINUTES pour ne pas jeter une ETA valide simplement
     *  parce que le navire n'a pas rediffusé ses données statiques récemment. */
    private static final long VOYAGE_INFO_STALE_AFTER_HOURS = 6;

    @Value("${fleethub.tracking.aisstream.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LiveVesselPosition> positions = new ConcurrentHashMap<>();
    private final Map<String, VesselVoyageInfo> voyageInfo = new ConcurrentHashMap<>();
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

    /** Infos de voyage diffusées par le navire lui-même (message ShipStaticData,
     *  AIS type 5) -- destination et ETA telles que RAPPORTÉES par le navire,
     *  jamais calculées par ce système (voir javadoc d'EtaReportedSource.Source.AIS_BROADCAST).
     *  broadcastEta est déjà résolue en date complète (voir resolveBroadcastEta) --
     *  null si l'ETA diffusée est absente ou vaut le sentinel AIS "non disponible". */
    public record VesselVoyageInfo(
            String mmsi,
            String destination,
            LocalDateTime broadcastEta,
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

        LocalDateTime voyageCutoff = LocalDateTime.now().minus(VOYAGE_INFO_STALE_AFTER_HOURS, ChronoUnit.HOURS);
        voyageInfo.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(voyageCutoff));
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

    /** ETA que le navire identifié par ce MMSI diffuse lui-même via AIS, si
     *  connue et non périmée -- voir VesselVoyageInfo. Utilisé par
     *  AisEtaCaptureService pour les expéditions liées à un navire suivi
     *  (ShipmentOrder.trackedVesselMmsi). */
    public Optional<LocalDateTime> getBroadcastEta(String mmsi) {
        if (mmsi == null) return Optional.empty();
        VesselVoyageInfo info = voyageInfo.get(mmsi);
        return info != null ? Optional.ofNullable(info.broadcastEta()) : Optional.empty();
    }

    /**
     * Résout l'ETA diffusée par un navire (AIS type 5, champs Month/Day/Hour/Minute
     * -- pas d'année, limite du standard AIS ITU) en une date complète. Les
     * sentinelles AIS "non disponible" (Month=0, Day=0, Hour=24, Minute=60) et
     * toute valeur hors plage renvoient null plutôt qu'une date fausse. Comme
     * l'année n'est jamais diffusée, suppose l'occurrence la plus proche dans le
     * futur : si le mois/jour semble déjà passé de plus d'une semaine cette
     * année (marge pour tolérer un peu de retard de mise à jour), on suppose
     * l'année suivante.
     */
    static LocalDateTime resolveBroadcastEta(JsonNode etaNode) {
        if (etaNode == null || etaNode.isMissingNode()) return null;
        int month = etaNode.path("Month").asInt(-1);
        int day = etaNode.path("Day").asInt(-1);
        int hour = etaNode.path("Hour").asInt(-1);
        int minute = etaNode.path("Minute").asInt(-1);

        if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate candidate;
        try {
            candidate = LocalDate.of(today.getYear(), month, day);
        } catch (Exception e) {
            return null; // date invalide (ex. 30 février) -- jamais une ETA fausse
        }
        long diffDays = ChronoUnit.DAYS.between(today, candidate);
        if (diffDays < -7) {
            // Mois/jour déjà passés cette année (hors marge) -- le navire parle probablement
            // de l'année prochaine.
            candidate = candidate.plusYears(1);
        } else if (diffDays > 300) {
            // À l'inverse, début janvier avec une ETA de fin décembre : interprétée avec
            // l'année courante, la date tomberait presque un an dans le futur -- elle
            // parlait en fait de l'année précédente (ex. aujourd'hui 2 janvier, ETA 30 décembre).
            candidate = candidate.minusYears(1);
        }
        return LocalDateTime.of(candidate, LocalTime.of(hour, minute));
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
                    "FilterMessageTypes", new String[]{"PositionReport", "ShipStaticData"}
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
                if (meta.isMissingNode()) return;

                String mmsi = meta.path("MMSI").asText(null);
                if (mmsi == null || mmsi.isBlank()) return;

                JsonNode positionReport = root.path("Message").path("PositionReport");
                if (!positionReport.isMissingNode()) {
                    processPositionReport(mmsi, meta, positionReport);
                }

                JsonNode shipStaticData = root.path("Message").path("ShipStaticData");
                if (!shipStaticData.isMissingNode()) {
                    processShipStaticData(mmsi, shipStaticData);
                }
            } catch (Exception e) {
                log.warn("[AisStream] Message illisible ignoré: {}", e.getMessage());
            }
        }

        private void processPositionReport(String mmsi, JsonNode meta, JsonNode report) {
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
        }

        private void processShipStaticData(String mmsi, JsonNode shipStaticData) {
            String destination = shipStaticData.path("Destination").asText(null);
            LocalDateTime broadcastEta = AisStreamService.resolveBroadcastEta(shipStaticData.path("Eta"));
            voyageInfo.put(mmsi, new VesselVoyageInfo(
                    mmsi,
                    destination != null && !destination.isBlank() ? destination.trim() : null,
                    broadcastEta,
                    LocalDateTime.now()));
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
