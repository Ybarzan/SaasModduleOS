package com.fleethub.integration.parser;

import com.fleethub.integration.dto.TachographDayDto;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ces tests construisent des fichiers G1 à la main, octet par octet, en
 * suivant la structure vérifiée dans la javadoc de {@link DddFileParser}
 * (texte réglementaire + implémentation de référence jugglingcats/tachograph-reader).
 * Ils prouvent que le décodeur applique correctement ce format documenté —
 * ils ne remplacent pas un test contre un vrai fichier téléchargé, qui reste
 * à faire dès qu'un accès AS24 Tak&amp;drive sera disponible (voir
 * {@code source = "FILE_DDD_UNVALIDATED"} sur chaque ligne produite).
 */
class DddFileParserTest {

    private final DddFileParser parser = new DddFileParser();

    /** ActivityChangeInfo (2 octets) : scpaattttttttttt, poids fort en premier. */
    private static byte[] activityChangeInfo(int activityType, int timeMinutes) {
        int b0 = ((activityType & 0x03) << 3) | ((timeMinutes >> 8) & 0x07);
        int b1 = timeMinutes & 0xFF;
        return new byte[]{(byte) b0, (byte) b1};
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >> 24) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    /** Un enregistrement journalier complet : en-tête 12 octets + N ActivityChangeInfo. */
    private static byte[] dailyRecord(LocalDate date, byte[]... activityChanges) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int recLen = 12 + activityChanges.length * 2;
        writeU16(out, 0);              // previousRecordLength (non exploité)
        writeU16(out, recLen);         // currentRecordLength
        writeU32(out, date.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        writeU16(out, 0);              // dailyPresenceCounter (BCD, non exploité ici)
        writeU16(out, 0);              // distance (non exploité)
        for (byte[] change : activityChanges) out.writeBytes(change);
        return out.toByteArray();
    }

    /** Enveloppe TLV G1 complète : FID (2, gros-boutiste) + type (1) + longueur (2) + valeur. */
    private static byte[] ddd(int fileId, int type, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU16(out, fileId);
        out.write(type);
        writeU16(out, value.length);
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] activityBlock(byte[]... dailyRecords) {
        ByteArrayOutputStream cyclic = new ByteArrayOutputStream();
        int newestOffset = 0;
        for (int i = 0; i < dailyRecords.length; i++) {
            if (i == dailyRecords.length - 1) newestOffset = cyclic.size();
            cyclic.writeBytes(dailyRecords[i]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU16(out, 0);              // oldestRecord : début du tampon
        writeU16(out, newestOffset);   // newestRecord : DÉBUT du dernier enregistrement écrit
        out.writeBytes(cyclic.toByteArray());
        return out.toByteArray();
    }

    @Test
    void parse_singleDay_attributesDurationsForwardToNextChange() throws Exception {
        LocalDate day = LocalDate.of(2026, 1, 15);
        byte[] record = dailyRecord(day,
                activityChangeInfo(0, 0),     // repos à partir de 00h00
                activityChangeInfo(3, 480),   // conduite à partir de 08h00
                activityChangeInfo(2, 720));  // travail à partir de 12h00
        byte[] file = ddd(0x0504, 0x00, activityBlock(record));

        DddFileParser.ParseResult result = parser.parse(new ByteArrayInputStream(file));

        assertTrue(result.errors().isEmpty(), "Erreurs inattendues : " + result.errors());
        assertEquals(1, result.rows().size());
        TachographDayDto row = result.rows().get(0);
        assertEquals(day, row.date());
        // Repos 00h00->08h00 (480 min), conduite 08h00->12h00 (240 min = 4h),
        // travail 12h00->24h00 (720 min) ; heures de travail = conduite + autre travail (16h).
        assertEquals(480.0, row.restMinutes(), 0.01);
        assertEquals(4.0, row.drivingHours(), 0.01);
        assertEquals(16.0, row.workHours(), 0.01);
        assertEquals("FILE_DDD_UNVALIDATED", row.source());
    }

    @Test
    void parse_activityAfter4h15AM_isNotTruncatedTo8Bits() throws Exception {
        // Avant correction, l'heure était lue sur un seul octet (0-255) : tout
        // changement après 4h15 (255 min) était tronqué. On vérifie ici un
        // changement à 16h40 (1000 min), largement au-delà de cette limite.
        LocalDate day = LocalDate.of(2026, 2, 1);
        byte[] record = dailyRecord(day,
                activityChangeInfo(3, 0),      // conduite dès minuit
                activityChangeInfo(0, 1000));  // repos à partir de 16h40
        byte[] file = ddd(0x0504, 0x00, activityBlock(record));

        DddFileParser.ParseResult result = parser.parse(new ByteArrayInputStream(file));

        TachographDayDto row = result.rows().get(0);
        // Conduite 00h00->16h40 = 1000 min = 16h40 ; repos 16h40->24h00 = 440 min.
        assertEquals(1000.0 / 60.0, row.drivingHours(), 0.01);
        assertEquals(440.0, row.restMinutes(), 0.01);
    }

    @Test
    void parse_signatureBlock_isNeverReadAsActivityData() throws Exception {
        LocalDate day = LocalDate.of(2026, 3, 10);
        // Seule entrée du jour : conduite à partir de 10h00. Le segment [00h00,10h00)
        // n'a pas d'activité connue plus tôt — il est supposé prolonger celle de
        // cette première entrée (voir le commentaire dans parseActivityPayload) :
        // la journée entière (24h) est donc comptée en conduite.
        byte[] record = dailyRecord(day, activityChangeInfo(3, 600));
        byte[] dataBlock = ddd(0x0504, 0x00, activityBlock(record));

        // Bloc de signature (type=1) juste après, même FID, contenu arbitraire qui
        // produirait un décodage n'importe-quoi s'il était traité comme des données
        // d'activité (avant correction, un octet fixe de 128 était de toute façon
        // sauté au mauvais endroit, désynchronisant la lecture du reste du fichier).
        byte[] signature = new byte[64];
        for (int i = 0; i < signature.length; i++) signature[i] = (byte) 0xFF;
        byte[] signatureBlock = ddd(0x0504, 0x01, signature);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(dataBlock);
        file.writeBytes(signatureBlock);

        DddFileParser.ParseResult result = parser.parse(new ByteArrayInputStream(file.toByteArray()));

        assertEquals(1, result.rows().size(), "Le bloc de signature ne doit produire aucune ligne");
        assertEquals(24.0, result.rows().get(0).drivingHours(), 0.01);
    }

    @Test
    void parse_multipleDailyRecords_areAllExtracted() throws Exception {
        LocalDate day1 = LocalDate.of(2026, 4, 1);
        LocalDate day2 = LocalDate.of(2026, 4, 2);
        byte[] record1 = dailyRecord(day1, activityChangeInfo(3, 0)); // conduite toute la journée
        byte[] record2 = dailyRecord(day2, activityChangeInfo(0, 0)); // repos toute la journée
        byte[] file = ddd(0x0504, 0x00, activityBlock(record1, record2));

        DddFileParser.ParseResult result = parser.parse(new ByteArrayInputStream(file));

        List<TachographDayDto> rows = result.rows();
        assertEquals(2, rows.size());
        assertEquals(24.0, rows.stream().filter(r -> r.date().equals(day1)).findFirst().orElseThrow().drivingHours(), 0.01);
        assertEquals(1440.0, rows.stream().filter(r -> r.date().equals(day2)).findFirst().orElseThrow().restMinutes(), 0.01);
    }

    @Test
    void parse_tooSmallFile_reportsErrorWithoutThrowing() throws Exception {
        DddFileParser.ParseResult result = parser.parse(new ByteArrayInputStream(new byte[]{0x01, 0x02}));

        assertTrue(result.rows().isEmpty());
        assertFalse(result.errors().isEmpty());
    }
}
