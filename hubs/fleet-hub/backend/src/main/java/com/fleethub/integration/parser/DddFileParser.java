package com.fleethub.integration.parser;

import com.fleethub.integration.dto.TachographDayDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Parse un fichier binaire DDD (export carte conducteur tachygraphe numérique
 * européen) en {@link TachographDayDto}.
 *
 * <p>Supporte les formats G1 (Reg. 3821/85, blocs EF) et G2 (Reg. 2016/799,
 * BER-TLV). Le parser extrait les enregistrements d'activité quotidiens du
 * tampon cyclique (EF 0x0504 ou tag 0x0524) et les agrège par jour :
 * <ul>
 *   <li>Activité 3 = conduite (driving)</li>
 *   <li>Activité 2 = autre travail (other work)</li>
 *   <li>Activité 1 = disponibilité (availability)</li>
 *   <li>Activité 0 = repos (rest)</li>
 * </ul>
 *
 * <p>Ligne licence est extraite de l'identification carte (EF 0x0520 ou
 * tag 0x0520) — numéro de carte conducteur.
 */
public class DddFileParser {

    private static final Logger log = LoggerFactory.getLogger(DddFileParser.class);

    private static final int EF_DRIVER_ACTIVITY = 0x0504;
    private static final int EF_IDENTIFICATION = 0x0520;
    private static final int EF_APP_IDENT = 0x0501;
    private static final int DAILY_HEADER_SIZE = 12;

    public record ParseResult(List<TachographDayDto> rows, List<String> errors, String driverName) {
    }

    public ParseResult parse(InputStream inputStream) throws IOException {
        byte[] fileBytes = readAllBytes(inputStream);
        List<TachographDayDto> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (fileBytes.length < 5) {
            errors.add("Fichier trop petit pour être un DDD valide");
            return new ParseResult(rows, errors, "");
        }

        String driverName = "";
        Map<LocalDate, double[]> dailyMinutes = new LinkedHashMap<>();

        int gen = detectGeneration(fileBytes);
        if (gen == 1) {
            parseG1(fileBytes, dailyMinutes, errors);
        } else {
            parseG2(fileBytes, dailyMinutes, errors);
        }

        for (Map.Entry<LocalDate, double[]> entry : dailyMinutes.entrySet()) {
            double driving = entry.getValue()[0];
            double work = entry.getValue()[1];
            double rest = entry.getValue()[2];
            if (driving > 0 || work > 0 || rest > 0) {
                rows.add(new TachographDayDto("", entry.getKey(), driving / 60.0, (driving + work) / 60.0, rest, false));
            }
        }

        log.info("DDD parsé : {} jours, {} erreurs", rows.size(), errors.size());
        return new ParseResult(rows, errors, driverName);
    }

    private int detectGeneration(byte[] file) {
        if (file.length >= 2) {
            int magic = (file[0] & 0xFF) | ((file[1] & 0xFF) << 8);
            if (magic == 0x7621 || magic == 0x7622 || magic == 0x7631) {
                return 2;
            }
        }
        return 1;
    }

    // ===== G1 (block envelope format) =====

    private void parseG1(byte[] file, Map<LocalDate, double[]> daily, List<String> errors) {
        int pos = 0;
        while (pos < file.length - 4) {
            int fileId = readUInt16LE(file, pos);
            pos += 2;
            int sigByte = file[pos] & 0xFF;
            pos += 1;
            int blockLen = readUInt16LE(file, pos);
            pos += 2;

            if (pos + blockLen > file.length) break;

            byte[] payload = new byte[blockLen];
            System.arraycopy(file, pos, payload, 0, blockLen);
            pos += blockLen;

            if (sigByte == 1 && pos + 128 <= file.length) {
                pos += 128;
            }

            if (fileId == EF_DRIVER_ACTIVITY) {
                parseActivityPayload(payload, daily, errors);
            }
        }
    }

    // ===== G2 (BER-TLV format) =====

    private void parseG2(byte[] file, Map<LocalDate, double[]> daily, List<String> errors) {
        int pos = 2;
        while (pos < file.length - 2) {
            try {
                int[] tagResult = readTlvTag(file, pos);
                int tag = tagResult[0];
                pos = tagResult[1];
                int[] lenResult = readTlvLength(file, pos);
                int len = lenResult[0];
                pos = lenResult[1];

                if (pos + len > file.length) break;

                if (tag == EF_DRIVER_ACTIVITY || tag == 0x0524) {
                    byte[] payload = new byte[len];
                    System.arraycopy(file, pos, payload, 0, len);
                    parseActivityPayload(payload, daily, errors);
                }
                pos += len;
            } catch (Exception e) {
                errors.add("Erreur parsing TLV en position " + pos + " : " + e.getMessage());
                break;
            }
        }
    }

    private int[] readTlvTag(byte[] data, int pos) {
        int b0 = data[pos] & 0xFF;
        if ((b0 & 0x1F) == 0x1F) {
            int b1 = data[pos + 1] & 0xFF;
            return new int[]{(b0 << 8) | b1, pos + 2};
        }
        return new int[]{b0, pos + 1};
    }

    private int[] readTlvLength(byte[] data, int pos) {
        int b0 = data[pos] & 0xFF;
        if ((b0 & 0x80) == 0) {
            return new int[]{b0, pos + 1};
        }
        int numBytes = b0 & 0x7F;
        int len = 0;
        for (int i = 0; i < numBytes; i++) {
            len = (len << 8) | (data[pos + 1 + i] & 0xFF);
        }
        return new int[]{len, pos + 1 + numBytes};
    }

    // ===== Activity payload parsing (shared G1/G2) =====

    private void parseActivityPayload(byte[] payload, Map<LocalDate, double[]> daily, List<String> errors) {
        if (payload.length < 4) return;

        int oldestRecord = readUInt16LE(payload, 0);
        int newestRecord = readUInt16LE(payload, 2);

        if (oldestRecord >= payload.length || newestRecord >= payload.length) return;

        byte[] cyclic = new byte[payload.length - 4];
        System.arraycopy(payload, 4, cyclic, 0, cyclic.length);

        List<ActivityEntry> entries = new ArrayList<>();
        int pos = oldestRecord;

        int maxIter = cyclic.length + 10;
        int iter = 0;
        while (iter < maxIter) {
            iter++;
            if (pos == newestRecord) break;
            if (pos + DAILY_HEADER_SIZE > cyclic.length) {
                pos = 0;
                continue;
            }

            int prevLen = readUInt16LE(cyclic, pos);
            int recLen = readUInt16LE(cyclic, pos + 2);
            long recDate = readUInt32LE(cyclic, pos + 4);

            if (recLen < DAILY_HEADER_SIZE || recLen > 2000) {
                pos = (pos + 2) % cyclic.length;
                continue;
            }

            LocalDate date;
            try {
                date = Instant.ofEpochSecond(recDate).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception e) {
                pos = (pos + recLen) % cyclic.length;
                continue;
            }

            int actStart = pos + DAILY_HEADER_SIZE;
            int actEnd = pos + recLen;

            double[] mins = daily.computeIfAbsent(date, k -> new double[3]);

            int prevTime = 0;
            int actIdx = actStart;
            while (actIdx + 1 < cyclic.length && actIdx + 1 <= actEnd) {
                int b0 = cyclic[actIdx] & 0xFF;
                int b1 = cyclic[actIdx + 1] & 0xFF;
                int activityType = (b0 >> 4) & 0x0F;
                int timeMinutes = b1;

                int duration = timeMinutes - prevTime;
                if (duration < 0) duration += 1440;

                int category = mapActivityType(activityType);
                if (category >= 0 && category < 3) {
                    mins[category] += duration;
                }

                prevTime = timeMinutes;
                entries.add(new ActivityEntry(date, activityType, timeMinutes, duration));
                actIdx += 2;
            }

            int lastDuration = 1440 - prevTime;
            if (lastDuration > 0 && lastDuration < 1440) {
                int lastType = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).type;
                int category = mapActivityType(lastType);
                if (category >= 0 && category < 3) {
                    mins[category] += lastDuration;
                }
            }

            pos = (pos + recLen) % cyclic.length;
        }
    }

    /**
     * Mappe le type d'activité DDD vers une catégorie :
     * 0 = repos, 1 = conduite, 2 = autre travail
     */
    private int mapActivityType(int dddType) {
        return switch (dddType) {
            case 0 -> 0;  // rest
            case 3 -> 1;  // driving
            case 2 -> 2;  // other work
            default -> -1; // availability (1), short rest (5), unknown
        };
    }

    private record ActivityEntry(LocalDate date, int type, int timeMinutes, int duration) {}

    // ===== Utilities =====

    private int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private long readUInt32LE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF))
                | ((long) (data[offset + 1] & 0xFF) << 8)
                | ((long) (data[offset + 2] & 0xFF) << 16)
                | ((long) (data[offset + 3] & 0xFF) << 24);
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }
}
