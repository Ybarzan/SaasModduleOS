package com.fleethub.dto.tour;

import com.fleethub.model.MedicalProduct;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        List<TourStopDto> stops,
        /** Tournée de transport médical (comptage par catégorie, journal de garde). */
        boolean medical
) {
    public static TourDto of(Tour t, boolean withStops) {
        return of(t, withStops, java.util.Map.of());
    }

    /** Variante avec suggestions de signataire par site (application chauffeur). */
    public static TourDto of(Tour t, boolean withStops, java.util.Map<Long, String> signers) {
        List<TourStop> stops = t.getStops();
        boolean medical = t.getCompany() != null && t.getCompany().isMedical();
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
                withStops ? stopDtos(stops, signers, medical) : List.of(),
                medical);
    }

    /**
     * Arrêts de la tournée ; en transport médical, chaque remise porte les
     * produits « à remettre » (pris en charge avant elle et pas encore remis).
     */
    private static List<TourStopDto> stopDtos(List<TourStop> stops, Map<Long, String> signers, boolean medical) {
        if (!medical) {
            return stops.stream().map(s -> TourStopDto.of(s, signers.get(s.getSite().getId()))).toList();
        }
        List<TourStop> ordered = stops.stream().sorted(Comparator.comparingInt(TourStop::getSequence)).toList();
        Map<MedicalProduct, Integer> onBoard = new EnumMap<>(MedicalProduct.class);
        Map<Long, Map<MedicalProduct, Integer>> expected = new java.util.HashMap<>();
        for (TourStop s : ordered) {
            if (!s.getType().isPickup()) {
                expected.put(s.getId(), new EnumMap<>(onBoard));
            }
            if (s.getStatus() == TourStop.StopStatus.FAIT) {
                Map<MedicalProduct, Integer> counts = s.productCounts();
                if (s.getType().isPickup()) {
                    MedicalProduct.addAll(onBoard, counts);
                } else {
                    counts.forEach((k, v) -> onBoard.merge(k, -v, Integer::sum));
                    onBoard.replaceAll((k, v) -> Math.max(0, v));
                }
            }
        }
        List<TourStopDto> out = new ArrayList<>();
        for (TourStop s : stops) {
            out.add(TourStopDto.of(s, signers.get(s.getSite().getId()), expected.get(s.getId())));
        }
        return out;
    }
}
