package com.fleethub.integration.parser;

import com.fleethub.integration.dto.FuelTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse un fichier CSV ou DSW/AUL de transactions carburant (export
 * AS24 Infoservice ou autre) en {@link FuelTransactionDto}.
 *
 * <p>Deux modes de détection automatique :
 * <ul>
 *   <li><b>En-têtes nommés</b> (CSV classique) — colonnes repérées par nom
 *       ({@code registration}, {@code date}, {@code liters}, etc.)</li>
 *   <li><b>Colonnes positionnelles</b> (DSW/AUL sans en-tête) — les colonnes
 *       sont extraites par position fixe : 0=immat, 1=date, 2=litres,
 *       3=montant, 4=km. Le séparateur est détecté automatiquement.</li>
 * </ul>
 *
 * <p>Format positionnel attendu (pas d'en-tête, séparateur point-virgule) :
 * <pre>
 * AA-123-BB;15/08/2026;120,50;185,30;125000
 * BB-456-CC;16/08/2026;80,00;122,40;130500
 * </pre>
 */
public class FuelFileParser {

    private static final Logger log = LoggerFactory.getLogger(FuelFileParser.class);
    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FR2 = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public record ParseResult(List<FuelTransactionDto> rows, List<String> errors) {
    }

    public ParseResult parse(InputStream inputStream) {
        List<FuelTransactionDto> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                errors.add("Fichier vide ou en-tête manquant");
                return new ParseResult(rows, errors);
            }

            char separator = detectSeparator(headerLine);
            String[] headers = splitLine(headerLine, separator);

            boolean hasHeaders = detectHeaders(headers);

            if (hasHeaders) {
                parseWithHeaders(reader, headers, separator, rows, errors);
            } else {
                parsePositional(reader, headerLine, separator, rows, errors);
            }
        } catch (Exception e) {
            errors.add("Erreur de lecture : " + e.getMessage());
        }

        log.info("Fuel parsé : {} lignes importées, {} erreurs", rows.size(), errors.size());
        return new ParseResult(rows, errors);
    }

    private boolean detectHeaders(String[] headers) {
        for (String h : headers) {
            String normalized = h.trim().toLowerCase()
                    .replace("-", "_")
                    .replace("é", "e").replace("è", "e").replace("ê", "e")
                    .replace("à", "a").replace("ô", "o");
            if (normalized.equals("registration") || normalized.equals("immatriculation")
                    || normalized.equals("date") || normalized.equals("liters")
                    || normalized.equals("volume") || normalized.equals("amount")
                    || normalized.equals("montant") || normalized.equals("odometer_km")
                    || normalized.equals("kilometrage") || normalized.equals("odometer")) {
                return true;
            }
        }
        return false;
    }

    private void parseWithHeaders(BufferedReader reader, String[] headers, char separator,
                                  List<FuelTransactionDto> rows, List<String> errors) throws java.io.IOException {
        int colReg = findColumn(headers, "registration", "immatriculation");
        int colDate = findColumn(headers, "date");
        int colLiters = findColumn(headers, "liters", "volume");
        int colAmount = findColumn(headers, "amount", "montant");
        int colOdo = findColumn(headers, "odometer_km", "kilometrage", "odometer");

        if (colReg < 0 || colDate < 0) {
            errors.add("Colonnes obligatoires manquantes : registration et/ou date");
            return;
        }

        String line;
        int lineNum = 1;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            if (line.isBlank()) continue;

            try {
                String[] cols = splitLine(line, separator);
                String registration = cols[colReg].trim().replace("\"", "");
                if (registration.isEmpty()) {
                    errors.add("Ligne " + lineNum + ": immatriculation vide");
                    continue;
                }

                LocalDate date = parseDate(cols[colDate].trim().replace("\"", ""));
                if (date == null) {
                    errors.add("Ligne " + lineNum + ": date invalide '" + cols[colDate].trim() + "'");
                    continue;
                }

                double liters = colLiters >= 0 && colLiters < cols.length
                        ? parseDouble(cols[colLiters].trim().replace("\"", "")) : 0.0;
                double amount = colAmount >= 0 && colAmount < cols.length
                        ? parseDouble(cols[colAmount].trim().replace("\"", "")) : 0.0;
                double odo = colOdo >= 0 && colOdo < cols.length
                        ? parseDouble(cols[colOdo].trim().replace("\"", "")) : 0.0;

                if (liters <= 0 && amount <= 0) {
                    errors.add("Ligne " + lineNum + ": volume et montant nuls");
                    continue;
                }

                rows.add(new FuelTransactionDto(registration, date, liters, amount, odo));
            } catch (Exception e) {
                errors.add("Ligne " + lineNum + ": " + e.getMessage());
            }
        }
    }

    /**
     * Parse un fichier sans en-tête avec des colonnes positionnelles.
     * Position fixes : 0=immatriculation, 1=date, 2=litres, 3=montant, 4=kilométrage.
     * Le premier en-tête (déjà lu) est traité comme une première ligne de données.
     */
    private void parsePositional(BufferedReader reader, String firstLine, char separator,
                                 List<FuelTransactionDto> rows, List<String> errors) throws java.io.IOException {
        int lineNum = 1;
        processPositionalLine(firstLine, separator, lineNum, rows, errors);

        String line;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            if (line.isBlank()) continue;
            processPositionalLine(line, separator, lineNum, rows, errors);
        }
    }

    private void processPositionalLine(String line, char separator, int lineNum,
                                       List<FuelTransactionDto> rows, List<String> errors) {
        try {
            String[] cols = splitLine(line, separator);
            if (cols.length < 3) {
                errors.add("Ligne " + lineNum + ": trop peu de colonnes (" + cols.length + "/3 minimum)");
                return;
            }

            String registration = cols[0].trim().replace("\"", "");
            if (registration.isEmpty()) {
                errors.add("Ligne " + lineNum + ": immatriculation vide");
                return;
            }

            LocalDate date = parseDate(cols[1].trim().replace("\"", ""));
            if (date == null) {
                errors.add("Ligne " + lineNum + ": date invalide '" + cols[1].trim() + "'");
                return;
            }

            double liters = cols.length > 2 ? parseDouble(cols[2].trim().replace("\"", "")) : 0.0;
            double amount = cols.length > 3 ? parseDouble(cols[3].trim().replace("\"", "")) : 0.0;
            double odo = cols.length > 4 ? parseDouble(cols[4].trim().replace("\"", "")) : 0.0;

            if (liters <= 0 && amount <= 0) {
                errors.add("Ligne " + lineNum + ": volume et montant nuls");
                return;
            }

            rows.add(new FuelTransactionDto(registration, date, liters, amount, odo));
        } catch (Exception e) {
            errors.add("Ligne " + lineNum + ": " + e.getMessage());
        }
    }

    private char detectSeparator(String headerLine) {
        int semiCount = countChar(headerLine, ';');
        int tabCount = countChar(headerLine, '\t');
        int commaCount = countChar(headerLine, ',');
        if (semiCount >= tabCount && semiCount >= commaCount && semiCount > 0) return ';';
        if (tabCount >= commaCount && tabCount > 0) return '\t';
        return ',';
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private String[] splitLine(String line, char separator) {
        if (separator == '\t') {
            return line.split("\t", -1);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == separator && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, DATE_ISO);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(s, DATE_FR);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(s, DATE_FR2);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private int findColumn(String[] headers, String... candidates) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase()
                    .replace("-", "_")
                    .replace("é", "e")
                    .replace("è", "e")
                    .replace("ê", "e")
                    .replace("à", "a")
                    .replace("ô", "o");
            for (String c : candidates) {
                if (h.equals(c)) return i;
            }
        }
        return -1;
    }

    private double parseDouble(String s) {
        if (s.isEmpty()) return 0.0;
        String normalized = s.replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
