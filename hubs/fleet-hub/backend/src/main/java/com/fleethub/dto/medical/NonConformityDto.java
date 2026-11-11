package com.fleethub.dto.medical;

import com.fleethub.model.NonConformity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record NonConformityDto(
        Long id,
        Long tourId,
        Long tourStopId,
        String tourName,
        LocalDate tourDate,
        String siteName,
        String type,
        String severity,
        String status,
        boolean automatic,
        String description,
        LocalDateTime detectedAt,
        String detectedBy,
        String correctiveAction,
        LocalDateTime closedAt,
        String closedBy
) {
    public static NonConformityDto of(NonConformity n) {
        return new NonConformityDto(n.getId(), n.getTourId(), n.getTourStopId(), n.getTourName(), n.getTourDate(),
                n.getSiteName(), n.getType().name(), n.getSeverity().name(), n.getStatus().name(), n.isAutomatic(),
                n.getDescription(), n.getDetectedAt(), n.getDetectedBy(), n.getCorrectiveAction(),
                n.getClosedAt(), n.getClosedBy());
    }
}
