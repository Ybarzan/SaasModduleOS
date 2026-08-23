package com.fleethub.integration.parser;

import com.fleethub.integration.dto.TachographDayDto;
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
 * Parse un fichier CSV de données tachygraphe (export depuis un logiciel
 * d'analyse ou depuis l'extranet AS24) en {@link TachographDayDto}.
 *
 * <p>Format attendu (en-tête obligatoire, séparateur virgule) :
 * <pre>
 * licence_number,date,driving_hours,work_hours,rest_minutes
 * 123456789012,2026-08-15,8.5,10.0,480
 * </pre>
 *
 * <p>Les colonnes acceptées (insensible à la casse) :
 * <ul>
 *   <li>{@code licence_number} ou {@code license_number} — numéro de permis</li>
 *   <li>{@code date} — date au format {@code yyyy-MM-dd}</li>
 *   <li>{@code driving_hours} ou {@code driving} — heures de conduite</li>
 *   <li>{@code work_hours} ou {@code work} — heures de travail</li>
 *   <li>{@code rest_minutes} ou {@code rest} — minutes de repos</li>
 * </ul>
 */
public class TachoFileParser {

    private static final Logger log = LoggerFactory.getLogger(TachoFileParser.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public record ParseResult(List<TachographDayDto> rows, List<String> errors) {
    }

    public ParseResult parse(InputStream inputStream) {
        List<TachographDayDto> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                errors.add("Fichier vide ou en-tête manquant");
                return new ParseResult(rows, errors);
            }

            String[] headers = splitCsv(headerLine);
            int colLicence = findColumn(headers, "licence_number", "license_number");
            int colDate = findColumn(headers, "date");
            int colDriving = findColumn(headers, "driving_hours", "driving");
            int colWork = findColumn(headers, "work_hours", "work");
            int colRest = findColumn(headers, "rest_minutes", "rest");

            if (colLicence < 0 || colDate < 0) {
                errors.add("Colonnes obligatoires manquantes : licence_number et/ou date");
                return new ParseResult(rows, errors);
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;

                try {
                    String[] cols = splitCsv(line);
                    String licence = cols[colLicence].trim();
                    if (licence.isEmpty()) {
                        errors.add("Ligne " + lineNum + ": numéro de permis vide");
                        continue;
                    }

                    LocalDate date;
                    try {
                        date = LocalDate.parse(cols[colDate].trim(), DATE_FMT);
                    } catch (DateTimeParseException e) {
                        errors.add("Ligne " + lineNum + ": date invalide '" + cols[colDate].trim() + "'");
                        continue;
                    }

                    double driving = colDriving >= 0 && colDriving < cols.length
                            ? parseDouble(cols[colDriving].trim()) : 0.0;
                    double work = colWork >= 0 && colWork < cols.length
                            ? parseDouble(cols[colWork].trim()) : 0.0;
                    double rest = colRest >= 0 && colRest < cols.length
                            ? parseDouble(cols[colRest].trim()) : 0.0;

                    rows.add(new TachographDayDto(licence, date, driving, work, rest, false));
                } catch (Exception e) {
                    errors.add("Ligne " + lineNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("Erreur de lecture : " + e.getMessage());
        }

        log.info("Tacho CSV parsé : {} lignes lues, {} erreurs", rows.size(), errors.size());
        return new ParseResult(rows, errors);
    }

    private String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    private int findColumn(String[] headers, String... candidates) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase().replace("-", "_");
            for (String c : candidates) {
                if (h.equals(c)) return i;
            }
        }
        return -1;
    }

    private double parseDouble(String s) {
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
