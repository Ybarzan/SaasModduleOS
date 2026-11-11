package com.fleethub.dto.medical;

import com.fleethub.service.medical.Reconciliation;

import java.util.List;

/** Dossier de traçabilité d'une tournée médicale : rapprochement, journal, non-conformités. */
public record TourTraceabilityDto(
        Long tourId,
        String tourName,
        Reconciliation reconciliation,
        List<CustodyEventDto> events,
        List<NonConformityDto> nonConformities
) {}
