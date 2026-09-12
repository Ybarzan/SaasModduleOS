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
 * européen, ex. AS24 Tak&amp;drive) en {@link TachographDayDto}.
 *
 * <p><b>⚠️ Jamais exécuté sur un vrai fichier téléchargé</b> — aucun accès à
 * un lecteur/extranet AS24 n'était disponible au moment de cette révision.
 * Toutes les lignes produites portent {@code source = "FILE_DDD_UNVALIDATED"}
 * (voir {@link TachographDayDto#source()}) et doivent rester visiblement "à
 * vérifier" côté produit tant qu'un vrai fichier n'aura pas confirmé le
 * décodage de bout en bout.
 *
 * <p>La structure ci-dessous est vérifiée contre deux sources indépendantes :
 * le texte réglementaire public (Règlement CEE 3821/85 Annexe I B Appendice 1
 * "ActivityChangeInfo" ; Règlement UE 2016/799 Annexe I C Appendice 7,
 * structure TLV des fichiers EF) et une implémentation de référence
 * open-source qui lit réellement des fichiers de cartes conducteur
 * (<a href="https://github.com/jugglingcats/tachograph-reader">jugglingcats/tachograph-reader</a>,
 * classes {@code ElementaryFileRegion}, {@code DriverCardDailyActivityRegion},
 * {@code ActivityChangeRegion}). Trois erreurs ont été corrigées par rapport
 * à la version précédente de ce fichier, jamais testée :
 * <ol>
 *   <li>tous les entiers 16/32 bits du format sont <b>gros-boutistes</b>
 *       (poids fort en premier) — l'ancienne version les lisait en
 *       petit-boutiste, ce qui aurait empêché de même localiser le bon
 *       bloc EF sur un fichier réel ;</li>
 *   <li>un bloc de signature n'a pas une taille fixe de 128 octets : sa
 *       longueur est son propre champ de longueur (comme n'importe quel
 *       autre bloc), et un bloc de signature ne doit jamais être traité
 *       comme des données d'activité même s'il partage le même identifiant
 *       de fichier que le bloc de données qui le précède ;</li>
 *   <li>l'heure d'un changement d'activité ({@code ActivityChangeInfo}) est
 *       codée sur 11 bits (0-1439 minutes), pas sur un seul octet (0-255) —
 *       l'ancien code tronquait donc tout changement survenant après 4h15
 *       du matin.</li>
 * </ol>
 *
 * <p>Supporte les formats G1 (Reg. 3821/85, blocs EF) et G2 (Reg. 2016/799,
 * BER-TLV) — seule la détection G1/G2 elle-même (2 premiers octets) et
 * l'enveloppe TLV de G2 restent non recroisées avec une implémentation de
 * référence (contrairement à G1 et à {@code ActivityChangeInfo}, communs
 * aux deux générations). Le parser extrait les enregistrements d'activité
 * quotidiens du tampon cyclique (EF 0x0504, "DriverActivityData") et les
 * agrège par jour. Chaque changement d'activité est un
 * {@code ActivityChangeInfo} de 2 octets, format bit
 * {@code scpaattttttttttt} (16 bits, poids fort en premier) :
 * <ul>
 *   <li>bit 15 : créneau (slot) — non exploité ici</li>
 *   <li>bit 14 : statut équipage — non exploité ici</li>
 *   <li>bit 13 : statut carte — non exploité ici</li>
 *   <li>bits 12-11 (aa) : activité — 00=repos, 01=disponibilité, 10=travail, 11=conduite</li>
 *   <li>bits 10-0 (t) : minutes écoulées depuis 00h00 (0-1439)</li>
 * </ul>
 */
public class DddFileParser {

    private static final Logger log = LoggerFactory.getLogger(DddFileParser.class);

    private static final int EF_DRIVER_ACTIVITY = 0x0504;
    private static final int EF_IDENTIFICATION = 0x0520;
    private static final int EF_APP_IDENT = 0x0501;
    private static final int DAILY_HEADER_SIZE = 12;

    /** Type de bloc EF (3e octet de l'enveloppe) : donnée réelle vs. signature. */
    private static final int TYPE_DATA = 0x00;
    private static final int TYPE_SIGNATURE = 0x01;

    /** Indices du tableau {@code double[3]} accumulant les minutes par jour.
     *  La disponibilité (activité 01) n'est comptée dans aucune des trois —
     *  ni conduite, ni travail, ni repos — comme dans l'implémentation de
     *  référence (elle n'entre dans aucun cumul 561/2006 direct). */
    private static final int CAT_REST = 0;
    private static final int CAT_WORK = 1;
    private static final int CAT_DRIVING = 2;

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
            double rest = entry.getValue()[CAT_REST];
            double work = entry.getValue()[CAT_WORK];
            double driving = entry.getValue()[CAT_DRIVING];
            if (driving > 0 || work > 0 || rest > 0) {
                rows.add(new TachographDayDto("", entry.getKey(), driving / 60.0, (driving + work) / 60.0, rest,
                        false, "FILE_DDD_UNVALIDATED"));
            }
        }

        log.info("DDD parsé : {} jours, {} erreurs", rows.size(), errors.size());
        return new ParseResult(rows, errors, driverName);
    }

    /** Non recroisé avec une implémentation de référence — voir javadoc de la classe. */
    private int detectGeneration(byte[] file) {
        if (file.length >= 2) {
            int magic = ((file[0] & 0xFF) << 8) | (file[1] & 0xFF);
            if (magic == 0x7621 || magic == 0x7622 || magic == 0x7631) {
                return 2;
            }
        }
        return 1;
    }

    // ===== G1 (enveloppe TLV : FID gros-boutiste (2) + type (1) + longueur gros-boutiste (2) + valeur) =====

    private void parseG1(byte[] file, Map<LocalDate, double[]> daily, List<String> errors) {
        int pos = 0;
        while (pos < file.length - 4) {
            int fileId = readUInt16BE(file, pos);
            pos += 2;
            int type = file[pos] & 0xFF;
            pos += 1;
            int blockLen = readUInt16BE(file, pos);
            pos += 2;

            if (pos + blockLen > file.length) break;

            // Un bloc de signature suit toujours son bloc de données sous le même
            // FID : ne jamais l'interpréter comme des données d'activité. Sa
            // longueur est celle déclarée ci-dessus, pas une taille fixe.
            if (fileId == EF_DRIVER_ACTIVITY && type == TYPE_DATA) {
                byte[] payload = new byte[blockLen];
                System.arraycopy(file, pos, payload, 0, blockLen);
                parseActivityPayload(payload, daily, errors);
            }
            pos += blockLen;
        }
    }

    // ===== G2 (BER-TLV format) — enveloppe non recroisée avec une implémentation de référence =====

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

        int oldestRecord = readUInt16BE(payload, 0);
        int newestRecord = readUInt16BE(payload, 2);

        if (oldestRecord >= payload.length || newestRecord >= payload.length) return;

        byte[] cyclic = new byte[payload.length - 4];
        System.arraycopy(payload, 4, cyclic, 0, cyclic.length);

        int pos = oldestRecord;

        int maxIter = cyclic.length + 10;
        int iter = 0;
        while (iter < maxIter) {
            iter++;
            // Comme dans l'implémentation de référence : on traite TOUJOURS
            // l'enregistrement à la position courante avant de s'arrêter — si on
            // s'arrêtait avant traitement (comme le faisait l'ancienne version),
            // un tampon à un seul enregistrement (oldest == newest) ne serait
            // jamais lu du tout.
            boolean isLastRecord = pos == newestRecord;
            if (pos + DAILY_HEADER_SIZE > cyclic.length) {
                if (isLastRecord) break;
                pos = 0;
                continue;
            }

            // previousRecordLength (2 octets) lu mais non utilisé pour la navigation,
            // comme dans l'implémentation de référence — seul recLen sert à avancer.
            int recLen = readUInt16BE(cyclic, pos + 2);
            long recDate = readUInt32BE(cyclic, pos + 4);

            if (recLen < DAILY_HEADER_SIZE || recLen > 2000) {
                if (isLastRecord) break;
                pos = (pos + 2) % cyclic.length;
                continue;
            }

            LocalDate date;
            try {
                date = Instant.ofEpochSecond(recDate).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception e) {
                if (isLastRecord) break;
                pos = (pos + recLen) % cyclic.length;
                continue;
            }

            int actStart = pos + DAILY_HEADER_SIZE;
            int actEnd = pos + recLen;

            double[] mins = daily.computeIfAbsent(date, k -> new double[3]);

            // Un ActivityChangeInfo décrit l'activité qui COMMENCE à son heure — la
            // durée d'un segment va donc de l'heure de l'entrée courante à celle de
            // la SUIVANTE, imputée à l'activité de l'entrée COURANTE (attribution
            // "en avant"). L'ancienne version imputait la durée écoulée DEPUIS
            // l'entrée précédente à l'entrée courante ("en arrière") : sur une
            // journée repos→conduite→travail, elle créditait 0 minute de repos et
            // reportait tout sur les deux activités suivantes — un vrai décalage
            // d'une entrée, pas un simple arrondi.
            int prevTime = 0;
            int prevCategory = -1;
            int actIdx = actStart;
            while (actIdx + 1 < cyclic.length && actIdx + 1 <= actEnd) {
                int b0 = cyclic[actIdx] & 0xFF;
                int b1 = cyclic[actIdx + 1] & 0xFF;
                // ActivityChangeInfo (scpaattttttttttt, 16 bits, poids fort en 1er) :
                // activité = bits 12-11 (2 bits après les 3 bits de créneau/équipage/carte,
                // donc bits 4-3 de l'octet de poids fort) ; heure = bits 10-0 (11 bits :
                // les 3 bits de poids faible de l'octet de poids fort + l'octet de poids
                // faible en entier).
                int activityType = (b0 >> 3) & 0x03;
                int timeMinutes = ((b0 & 0x07) << 8) | b1;

                int duration = timeMinutes - prevTime;
                if (duration < 0) duration += 1440;

                if (prevCategory >= 0) {
                    mins[prevCategory] += duration;
                } else if (duration > 0) {
                    // Aucune entrée avant celle-ci : le segment [00h00, heure de cette
                    // entrée) n'a pas d'activité connue plus tôt dans la journée — on
                    // suppose qu'elle prolonge l'activité de cette première entrée
                    // (même hypothèse de continuité que pour le dernier segment, voir
                    // plus bas).
                    int firstCategory = mapActivityType(activityType);
                    if (firstCategory >= 0) mins[firstCategory] += duration;
                }

                prevCategory = mapActivityType(activityType);
                prevTime = timeMinutes;
                actIdx += 2;
            }

            // lastDuration vaut naturellement 1440 quand une seule activité couvre
            // toute la journée (prevTime resté à 0) — un plafond "< 1440" exclurait
            // à tort ce cas parfaitement valide (ex: repos ininterrompu 24h).
            int lastDuration = 1440 - prevTime;
            if (lastDuration > 0 && prevCategory >= 0) {
                mins[prevCategory] += lastDuration;
            }

            if (isLastRecord) break;
            pos = (pos + recLen) % cyclic.length;
        }
    }

    /**
     * Mappe l'activité brute (2 bits, {@code ActivityChangeInfo}) vers l'indice
     * du tableau de cumul journalier : 00=repos, 01=disponibilité (exclue de
     * tout cumul), 10=travail, 11=conduite.
     */
    private int mapActivityType(int dddType) {
        return switch (dddType) {
            case 0 -> CAT_REST;
            case 2 -> CAT_WORK;
            case 3 -> CAT_DRIVING;
            default -> -1; // 1 = disponibilité, exclue
        };
    }

    // ===== Utilities =====

    /** Le format DDD est intégralement gros-boutiste (poids fort en premier). */
    private int readUInt16BE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private long readUInt32BE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
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
