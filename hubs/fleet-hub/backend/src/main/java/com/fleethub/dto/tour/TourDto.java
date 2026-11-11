package com.fleethub.dto.tour;

import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record TourDto(
        Long id,
        String name,
        LocalDate date,
        Long driverId,
        String driverName,
        Long truckId,
        String truckRegistration,
        Long depotId,
        String depotName,
        Double depotLatitude,
        Double depotLongitude,
        LocalTime plannedStart,
        String status,
        Double plannedDistanceKm,
        Integer plannedDurationMinutes,
        LocalDateTime optimizedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String notes,
        int stopsTotal,
        int stopsDone,
        int stopsFailed,
        /** Arrêts dont l'arrivée estimée dépasse la fin de créneau. */
        int stopsLate,
        List<TourStopDto> stops
) {
    public static TourDto of(Tour t, boolean withStops) {
        List<TourStop> stops = t.getStops();
        int done = (int) stops.stream().filter(s -> s.getStatus() == TourStop.StopStatus.FAIT).count();
        int failed = (int) stops.stream().filter(s -> s.getStatus() == TourStop.StopStatus.ECHEC).count();
        int late = (int) stops.stream()
                .filter(s -> s.getPlannedLatenessMinutes() != null && s.getPlannedLatenessMinutes() > 0).count();
        return new TourDto(t.getId(), t.getName(), t.getDate(),
                t.getDriver() != null ? t.getDriver().getId() : null,
                t.getDriver() != null ? t.getDriver().getFirstName() + " " + t.getDriver().getLastName() : null,
                t.getTruck() != null ? t.getTruck().getId() : null,
                t.getTruck() != null ? t.getTruck().getRegistration() : null,
                t.getDepot() != null ? t.getDepot().getId() : null,
                t.getDepot() != null ? t.getDepot().getName() : null,
                t.getDepot() != null ? t.getDepot().getLatitude() : null,
                t.getDepot() != null ? t.getDepot().getLongitude() : null,
                t.getPlannedStart(), t.getStatus().name(), t.getPlannedDistanceKm(),
                t.getPlannedDurationMinutes(), t.getOptimizedAt(), t.getStartedAt(), t.getCompletedAt(),
                t.getNotes(), stops.size(), done, failed, late,
                withStops ? stops.stream().map(TourStopDto::of).toList() : List.of());
    }
}
