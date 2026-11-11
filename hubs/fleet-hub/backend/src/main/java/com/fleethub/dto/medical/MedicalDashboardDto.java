package com.fleethub.dto.medical;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Indicateurs du transport médical sur une période. */
public record MedicalDashboardDto(
        LocalDate from,
        LocalDate to,
        int tours,
        int toursCompleted,
        /** Tournées terminées sans aucune non-conformité / tournées terminées (%). */
        double conformityRate,
        int collectionsDone,
        int collectionsFailed,
        int productsCollected,
        int productsDelivered,
        Map<String, Integer> collectedByCategory,
        int temperatureExcursions,
        Double averageTransitMinutes,
        Long maxTransitMinutes,
        int transitsExceeded,
        int transitLimitMinutes,
        /** Non-conformités non clôturées (toutes périodes). */
        long openNonConformities,
        Map<String, Long> nonConformitiesByType,
        List<Day> daily
) {
    public record Day(LocalDate date, int collected, int delivered, int nonConformities) {}
}
