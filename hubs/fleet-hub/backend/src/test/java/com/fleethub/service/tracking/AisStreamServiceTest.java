package com.fleethub.service.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * resolveBroadcastEta() et le routage ShipStaticData/PositionReport de
 * processPayload() -- complément AIS de Phase 2, voir
 * docs/03-plan-migration.md et EtaReportedSource.Source.AIS_BROADCAST.
 */
@DisplayName("AisStreamService — ETA diffusée (ShipStaticData)")
class AisStreamServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode eta(int month, int day, int hour, int minute) throws Exception {
        return objectMapper.readTree(String.format(
                "{\"Month\":%d,\"Day\":%d,\"Hour\":%d,\"Minute\":%d}", month, day, hour, minute));
    }

    private LocalDateTime resolve(JsonNode etaNode) throws Exception {
        Method m = AisStreamService.class.getDeclaredMethod("resolveBroadcastEta", JsonNode.class);
        m.setAccessible(true);
        return (LocalDateTime) m.invoke(null, etaNode);
    }

    @Test
    @DisplayName("ETA valide dans le futur proche → résolue avec l'année courante")
    void resolveBroadcastEta_validFutureDate() throws Exception {
        LocalDate future = LocalDate.now().plusDays(10);
        LocalDateTime result = resolve(eta(future.getMonthValue(), future.getDayOfMonth(), 14, 30));

        assertThat(result).isEqualTo(LocalDateTime.of(future, LocalTime.of(14, 30)));
    }

    @Test
    @DisplayName("mois/jour déjà passés cette année (hors marge) → suppose l'année suivante")
    void resolveBroadcastEta_pastDateThisYear_assumesNextYear() throws Exception {
        LocalDate past = LocalDate.now().minusDays(30);
        LocalDateTime result = resolve(eta(past.getMonthValue(), past.getDayOfMonth(), 8, 0));

        assertThat(result.getYear()).isEqualTo(past.getYear() + 1);
        assertThat(result.getMonthValue()).isEqualTo(past.getMonthValue());
        assertThat(result.getDayOfMonth()).isEqualTo(past.getDayOfMonth());
    }

    @Test
    @DisplayName("mois/jour dans la marge de tolérance (< 7 jours dans le passé) → année courante, pas de rollover")
    void resolveBroadcastEta_withinGracePeriod_staysCurrentYear() throws Exception {
        LocalDate recentPast = LocalDate.now().minusDays(3);
        LocalDateTime result = resolve(eta(recentPast.getMonthValue(), recentPast.getDayOfMonth(), 0, 0));

        assertThat(result.toLocalDate()).isEqualTo(recentPast);
    }

    @Test
    @DisplayName("sentinelle AIS \"non disponible\" (Month=0,Day=0,Hour=24,Minute=60) → null")
    void resolveBroadcastEta_aisSentinelUnavailable_returnsNull() throws Exception {
        assertThat(resolve(eta(0, 0, 24, 60))).isNull();
    }

    @Test
    @DisplayName("mois hors plage (13) → null, jamais une date fausse")
    void resolveBroadcastEta_outOfRangeMonth_returnsNull() throws Exception {
        assertThat(resolve(eta(13, 15, 10, 0))).isNull();
    }

    @Test
    @DisplayName("jour invalide pour le mois (31 février) → null, jamais d'exception propagée")
    void resolveBroadcastEta_invalidDayForMonth_returnsNull() throws Exception {
        assertThat(resolve(eta(2, 31, 10, 0))).isNull();
    }

    @Test
    @DisplayName("nœud Eta absent → null")
    void resolveBroadcastEta_missingNode_returnsNull() throws Exception {
        JsonNode missing = objectMapper.readTree("{}").path("Eta"); // MissingNode
        assertThat(resolve(missing)).isNull();
    }

    // ── processPayload (bout en bout, via réflexion sur la classe interne privée) ──

    private Object newHandler(AisStreamService service) throws Exception {
        Class<?> handlerClass = Class.forName("com.fleethub.service.tracking.AisStreamService$AisStreamHandler");
        Constructor<?> ctor = handlerClass.getDeclaredConstructor(AisStreamService.class);
        ctor.setAccessible(true);
        return ctor.newInstance(service);
    }

    private void processPayload(Object handler, String payload) throws Exception {
        Method m = handler.getClass().getDeclaredMethod("processPayload", String.class);
        m.setAccessible(true);
        m.invoke(handler, payload);
    }

    @Test
    @DisplayName("message ShipStaticData réel → ETA et destination capturées, consultables via getBroadcastEta")
    void processPayload_shipStaticData_capturesBroadcastEta() throws Exception {
        AisStreamService service = new AisStreamService();
        Object handler = newHandler(service);

        LocalDate future = LocalDate.now().plusDays(5);
        String payload = String.format("""
                {"MetaData":{"MMSI":"235012345","ShipName":"TEST VESSEL"},
                 "Message":{"ShipStaticData":{"Destination":"ANTIBES",
                   "Eta":{"Month":%d,"Day":%d,"Hour":12,"Minute":0}}}}
                """, future.getMonthValue(), future.getDayOfMonth());

        processPayload(handler, payload);

        Optional<LocalDateTime> result = service.getBroadcastEta("235012345");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(LocalDateTime.of(future, LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("MMSI inconnu → getBroadcastEta renvoie vide, jamais d'exception")
    void getBroadcastEta_unknownMmsi_returnsEmpty() {
        AisStreamService service = new AisStreamService();
        assertThat(service.getBroadcastEta("999999999")).isEmpty();
    }

    @Test
    @DisplayName("mmsi null → getBroadcastEta renvoie vide")
    void getBroadcastEta_nullMmsi_returnsEmpty() {
        AisStreamService service = new AisStreamService();
        assertThat(service.getBroadcastEta(null)).isEmpty();
    }

    @Test
    @DisplayName("message ShipStaticData sans ETA diffusée (sentinelle) → destination capturée, ETA absente")
    void processPayload_shipStaticDataWithoutEta_capturesDestinationOnly() throws Exception {
        AisStreamService service = new AisStreamService();
        Object handler = newHandler(service);

        String payload = """
                {"MetaData":{"MMSI":"235012345","ShipName":"TEST VESSEL"},
                 "Message":{"ShipStaticData":{"Destination":"ROTTERDAM",
                   "Eta":{"Month":0,"Day":0,"Hour":24,"Minute":60}}}}
                """;

        processPayload(handler, payload);

        assertThat(service.getBroadcastEta("235012345")).isEmpty();
    }

    @Test
    @DisplayName("message PositionReport (pas ShipStaticData) → n'affecte pas getBroadcastEta")
    void processPayload_positionReportOnly_doesNotAffectBroadcastEta() throws Exception {
        AisStreamService service = new AisStreamService();
        Object handler = newHandler(service);

        String payload = """
                {"MetaData":{"MMSI":"235012345","ShipName":"TEST VESSEL"},
                 "Message":{"PositionReport":{"Latitude":51.9,"Longitude":4.5}}}
                """;

        processPayload(handler, payload);

        assertThat(service.getBroadcastEta("235012345")).isEmpty();
        assertThat(ReflectionTestUtils.getField(service, "positions")).isNotNull();
    }
}
