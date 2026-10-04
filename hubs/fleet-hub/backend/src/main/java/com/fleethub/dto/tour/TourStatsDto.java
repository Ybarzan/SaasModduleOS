package com.fleethub.dto.tour;

import java.time.LocalDate;
import java.util.Map;

/** Indicateurs d'exploitation des tournées sur une période. */
public record TourStatsDto(
        LocalDate from,
        LocalDate to,
        int tours,
        int toursCompleted,
        int stops,
        int stopsDone,
        int stopsFailed,
        /** % d'arrêts réussis parmi les arrêts clôturés. */
        double successRate,
        /** % d'arrêts réalisés dans leur créneau (parmi ceux qui en ont un). */
        double onTimeRate,
        double avgStopsPerTour,
        double plannedKm,
        /** Km planifiés par arrêt : densité des tournées. */
        double kmPerStop,
        int samplesCollected,
        int parcelsHandled,
        int temperatureExcursions,
        Map<String, Long> failureReasons
) {}
