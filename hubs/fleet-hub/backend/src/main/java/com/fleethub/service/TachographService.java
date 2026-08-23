package com.fleethub.service;

import com.fleethub.model.TachographDay;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Moteur de règles du règlement CE n° 561/2006 (temps de conduite et de repos).
 *
 * <p>La conformité est évaluée sur l'agrégat journalier disponible
 * ({@link TachographDay} : conduite, travail, repos). Les cumuls hebdomadaire et
 * bimensuel sont contrôlés sur des fenêtres glissantes de 7 et 14 jours, ce qui
 * évite toute dépendance à un découpage calendaire arbitraire.</p>
 *
 * <p>Le résultat (conforme + motifs éventuels) est recalculé côté serveur à chaque
 * écriture : le client ne fournit plus le verdict, il fournit les relevés.</p>
 */
@Service
public class TachographService {

    /** Conduite journalière normale : 9 h. */
    public static final double DAILY_LIMIT_H = 9.0;
    /** Conduite journalière dérogatoire : 10 h (2 jours max par semaine). */
    public static final double DAILY_EXTENDED_LIMIT_H = 10.0;
    /** Nombre maximal de journées prolongées (&gt; 9 h) par semaine. */
    public static final long MAX_EXTENDED_DAYS_PER_WEEK = 2;
    /** Conduite continue maximale avant pause obligatoire : 4 h 30. */
    public static final double CONTINUOUS_DRIVING_LIMIT_H = 4.5;
    /** Pause obligatoire après 4 h 30 de conduite : 45 min. */
    public static final double MIN_BREAK_MINUTES = 45.0;
    /** Conduite hebdomadaire maximale : 56 h. */
    public static final double WEEKLY_LIMIT_H = 56.0;
    /** Conduite maximale sur quinzaine glissante : 90 h. */
    public static final double FORTNIGHT_LIMIT_H = 90.0;

    /** Résultat d'évaluation : verdict de conformité + motifs de non-conformité. */
    public record Assessment(boolean compliant, List<String> reasons) {
        public static Assessment ok() {
            return new Assessment(true, List.of());
        }
    }

    /**
     * Évalue la conformité d'une journée de conduite.
     *
     * @param day        la journée à évaluer
     * @param driverDays toutes les journées du chauffeur, {@code day} inclus,
     *                   pour calculer les fenêtres glissantes de 7 et 14 jours
     */
    public Assessment assess(TachographDay day, List<TachographDay> driverDays) {
        List<String> reasons = new ArrayList<>();
        LocalDate date = day.getDate();
        double driving = day.getDrivingHours();
        double rest = day.getRestMinutes();

        if (driving > DAILY_EXTENDED_LIMIT_H) {
            reasons.add("Conduite journalière de " + format(driving)
                    + " h (max " + format(DAILY_EXTENDED_LIMIT_H) + " h)");
        }

        long extendedDays = countInWindow(driverDays, date, 7,
                d -> d.getDrivingHours() > DAILY_LIMIT_H);
        if (driving > DAILY_LIMIT_H && extendedDays > MAX_EXTENDED_DAYS_PER_WEEK) {
            reasons.add("Conduite prolongée (> 9 h) sur " + extendedDays
                    + " jours cette semaine (dérogation limitée à "
                    + MAX_EXTENDED_DAYS_PER_WEEK + " jours)");
        }

        if (driving >= CONTINUOUS_DRIVING_LIMIT_H && rest < MIN_BREAK_MINUTES) {
            reasons.add("Pause de " + format(rest)
                    + " min insuffisante après " + format(CONTINUOUS_DRIVING_LIMIT_H)
                    + " h de conduite (45 min requises)");
        }

        double week = sumInWindow(driverDays, date, 7);
        if (week > WEEKLY_LIMIT_H) {
            reasons.add("Cumul de conduite de " + format(week)
                    + " h sur 7 jours glissants (max " + format(WEEKLY_LIMIT_H) + " h)");
        }

        double fortnight = sumInWindow(driverDays, date, 14);
        if (fortnight > FORTNIGHT_LIMIT_H) {
            reasons.add("Cumul de conduite de " + format(fortnight)
                    + " h sur quinzaine glissante (max " + format(FORTNIGHT_LIMIT_H) + " h)");
        }

        return reasons.isEmpty() ? Assessment.ok() : new Assessment(false, reasons);
    }

    private long countInWindow(List<TachographDay> days, LocalDate date, int windowDays,
                               java.util.function.Predicate<TachographDay> predicate) {
        return days.stream()
                .filter(d -> !d.getDate().isAfter(date) && d.getDate().isAfter(date.minusDays(windowDays)))
                .filter(predicate)
                .count();
    }

    private double sumInWindow(List<TachographDay> days, LocalDate date, int windowDays) {
        return days.stream()
                .filter(d -> !d.getDate().isAfter(date) && d.getDate().isAfter(date.minusDays(windowDays)))
                .mapToDouble(TachographDay::getDrivingHours)
                .sum();
    }

    private String format(double value) {
        String s = String.format(Locale.FRENCH, "%.1f", value);
        return s.endsWith(",0") ? s.substring(0, s.length() - 2) : s;
    }
}
