package com.fleethub.dto.medical;

import com.fleethub.model.CustodyEvent;

import java.time.LocalDateTime;

public record CustodyEventDto(
        Long id,
        long sequenceNo,
        String type,
        Long tourId,
        Long tourStopId,
        String tourName,
        String siteName,
        String siteKind,
        LocalDateTime occurredAt,
        LocalDateTime recordedAt,
        String actor,
        String products,
        Integer totalQuantity,
        Double temperatureCelsius,
        String signedBy,
        Double latitude,
        Double longitude,
        String details,
        String hash
) {
    public static CustodyEventDto of(CustodyEvent e) {
        return new CustodyEventDto(e.getId(), e.getSequenceNo(), e.getType().name(), e.getTourId(), e.getTourStopId(),
                e.getTourName(), e.getSiteName(), e.getSiteKind(), e.getOccurredAt(), e.getRecordedAt(), e.getActor(),
                e.getProducts(), e.getTotalQuantity(), e.getTemperatureCelsius(), e.getSignedBy(),
                e.getLatitude(), e.getLongitude(), e.getDetails(), e.getHash());
    }
}
