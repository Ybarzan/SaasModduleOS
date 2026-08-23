package com.fleethub.dto;

import java.time.LocalDate;

/**
 * Synthèse de conformité tachygraphe d'un chauffeur sur une période,
 * avec les cumuls glissants de conduite (7 et 14 jours).
 */
public record TachographSummaryDto(
        Long driverId,
        String driverName,
        int days,
        int compliantDays,
        int nonCompliantDays,
        double complianceRate,
        double totalDrivingLast7d,
        double totalDrivingLast14d,
        LocalDate lastNonCompliantDate
) {}
