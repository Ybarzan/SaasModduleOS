package com.fleethub.service;

import com.fleethub.model.TachographDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du moteur de règles 561/2006 (règlement CE n° 561/2006).
 */
class TachographServiceTest {

    private final TachographService service = new TachographService();

    private TachographDay day(LocalDate date, double drivingHours, double restMinutes) {
        TachographDay d = new TachographDay();
        d.setDate(date);
        d.setDrivingHours(drivingHours);
        d.setRestMinutes(restMinutes);
        return d;
    }

    private List<TachographDay> history(TachographDay... days) {
        return new ArrayList<>(List.of(days));
    }

    @Test
    void normalDay_isCompliant() {
        TachographDay d = day(LocalDate.of(2026, 8, 10), 8.0, 60);
        TachographService.Assessment a = service.assess(d, history(d));
        assertTrue(a.compliant(), "8 h de conduite et 60 min de repos doivent être conformes");
        assertTrue(a.reasons().isEmpty());
    }

    @Test
    void shortBreakAfterLongDriving_isNonCompliant() {
        TachographDay d = day(LocalDate.of(2026, 8, 10), 9.0, 20);
        TachographService.Assessment a = service.assess(d, history(d));
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("45 min")),
                "Le motif de pause insuffisante doit être explicite");
    }

    @Test
    void dailyDrivingOver10h_isNonCompliant() {
        TachographDay d = day(LocalDate.of(2026, 8, 10), 11.0, 120);
        TachographService.Assessment a = service.assess(d, history(d));
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("10 h")));
    }

    @Test
    void thirdExtendedDayInWeek_isNonCompliant() {
        LocalDate base = LocalDate.of(2026, 8, 10);
        TachographDay day1 = day(base.minusDays(2), 9.5, 60);
        TachographDay day2 = day(base.minusDays(1), 9.5, 60);
        TachographDay today = day(base, 9.5, 60);

        TachographService.Assessment a = service.assess(today, history(day1, day2, today));
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("dérogation")),
                "3 journées > 9 h sur 7 jours doivent dépasser la dérogation");
    }

    @Test
    void twoExtendedDaysInWeek_areAllowed() {
        LocalDate base = LocalDate.of(2026, 8, 10);
        TachographDay day1 = day(base.minusDays(2), 9.5, 60);
        TachographDay today = day(base, 9.5, 60);

        TachographService.Assessment a = service.assess(today, history(day1, today));
        assertTrue(a.compliant(), "2 journées > 9 h restent dans la dérogation (repos suffisant)");
    }

    @Test
    void weeklyDrivingOver56h_isNonCompliant() {
        LocalDate base = LocalDate.of(2026, 8, 10);
        // 7 journées dans la fenêtre glissante (base-6..base) à 8 h 30 = 59 h 30 > 56 h.
        List<TachographDay> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            week.add(day(base.minusDays(i), 8.5, 60));
        }
        TachographService.Assessment a = service.assess(week.get(0), week);
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("7 jours glissants")));
    }

    @Test
    void fortnightDrivingOver90h_isNonCompliant() {
        LocalDate base = LocalDate.of(2026, 8, 10);
        // 14 journées dans la fenêtre glissante (base-13..base) à 7 h = 98 h > 90 h.
        List<TachographDay> days = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            days.add(day(base.minusDays(i), 7.0, 60));
        }
        TachographService.Assessment a = service.assess(days.get(0), days);
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("quinzaine")));
    }

    @Test
    void multipleViolations_areAllReported() {
        TachographDay d = day(LocalDate.of(2026, 8, 10), 11.0, 15);
        TachographService.Assessment a = service.assess(d, history(d));
        assertFalse(a.compliant());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("10 h")));
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("45 min")));
        assertEquals(2, a.reasons().size());
    }

    @Test
    void weekWindow_excludesDaysOlderThan7Days() {
        LocalDate base = LocalDate.of(2026, 8, 10);
        // Un jour ancien (9 h de conduite) ne doit pas peser dans la fenêtre 7 jours.
        TachographDay oldDay = day(base.minusDays(8), 9.0, 60);
        TachographDay today = day(base, 9.5, 60);
        TachographService.Assessment a = service.assess(today, history(oldDay, today));
        assertTrue(a.compliant(), "Le jour ancien sort de la fenêtre de 7 jours");
    }
}
