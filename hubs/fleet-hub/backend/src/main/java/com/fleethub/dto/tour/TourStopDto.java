package com.fleethub.dto.tour;

import com.fleethub.model.Site;
import com.fleethub.model.TourStop;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record TourStopDto(
        Long id,
        int sequence,
        Long siteId,
        String siteName,
        String siteKind,
        String address,
        String city,
        Double latitude,
        Double longitude,
        String contactPhone,
        String type,
        LocalTime windowStart,
        LocalTime windowEnd,
        int serviceMinutes,
        LocalTime plannedArrival,
        Integer plannedLatenessMinutes,
        String status,
        LocalDateTime arrivedAt,
        LocalDateTime completedAt,
        String signedBy,
        Integer parcelCount,
        Integer sampleCount,
        Double temperatureCelsius,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        boolean temperatureExcursion,
        String scannedCodes,
        String failureReason,
        String notes,
        Boolean onTime
) {
    public static TourStopDto of(TourStop s) {
        Site site = s.getSite();
        return new TourStopDto(s.getId(), s.getSequence(), site.getId(), site.getName(), site.getKind().name(),
                site.getAddress(), site.getCity(), site.getLatitude(), site.getLongitude(), site.getContactPhone(),
                s.getType().name(), s.getWindowStart(), s.getWindowEnd(), s.getServiceMinutes(),
                s.getPlannedArrival(), s.getPlannedLatenessMinutes(), s.getStatus().name(),
                s.getArrivedAt(), s.getCompletedAt(), s.getSignedBy(), s.getParcelCount(), s.getSampleCount(),
                s.getTemperatureCelsius(), s.getTemperatureMinCelsius(), s.getTemperatureMaxCelsius(),
                s.isTemperatureExcursion(), s.getScannedCodes(), s.getFailureReason(), s.getNotes(),
                s.deliveredOnTime());
    }
}
