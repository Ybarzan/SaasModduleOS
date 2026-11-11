package com.fleethub.service.medical;

import com.fleethub.model.MedicalProduct;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rapprochement d'une tournée médicale : produits pris en charge aux points de
 * collecte contre produits remis au(x) laboratoire(s), par catégorie, et délai
 * d'acheminement de chaque collecte jusqu'à sa remise.
 */
public record Reconciliation(
        Map<MedicalProduct, Integer> collected,
        Map<MedicalProduct, Integer> delivered,
        /** delivered − collected par catégorie (négatif = produits manquants). */
        Map<MedicalProduct, Integer> gap,
        int collectedTotal,
        int deliveredTotal,
        boolean balanced,
        List<Transit> transits,
        int transitLimitMinutes,
        Long maxTransitMinutes
) {

    /** Acheminement d'une collecte jusqu'à la remise qui la suit dans la tournée. */
    public record Transit(Long stopId, String siteName, LocalDateTime collectedAt, Long deliveryStopId,
                          String deliverySiteName, LocalDateTime deliveredAt, Long minutes, boolean exceeded) {}

    public static Reconciliation of(Tour tour) {
        int limit = tour.getCompany().getMedicalMaxTransitMinutes();
        List<TourStop> stops = tour.getStops().stream()
                .sorted(Comparator.comparingInt(TourStop::getSequence)).toList();
        Map<MedicalProduct, Integer> collected = new EnumMap<>(MedicalProduct.class);
        Map<MedicalProduct, Integer> delivered = new EnumMap<>(MedicalProduct.class);
        for (TourStop s : stops) {
            if (s.getStatus() != TourStop.StopStatus.FAIT) continue;
            MedicalProduct.addAll(s.getType().isPickup() ? collected : delivered, s.productCounts());
        }
        Map<MedicalProduct, Integer> gap = new EnumMap<>(MedicalProduct.class);
        boolean balanced = true;
        for (MedicalProduct p : MedicalProduct.values()) {
            int diff = delivered.getOrDefault(p, 0) - collected.getOrDefault(p, 0);
            if (diff != 0) {
                gap.put(p, diff);
                balanced = false;
            }
        }

        List<Transit> transits = new ArrayList<>();
        Long max = null;
        for (int i = 0; i < stops.size(); i++) {
            TourStop pickup = stops.get(i);
            if (!pickup.getType().isPickup() || pickup.getStatus() != TourStop.StopStatus.FAIT
                    || pickup.getCompletedAt() == null || MedicalProduct.total(pickup.productCounts()) == 0) {
                continue;
            }
            TourStop delivery = null;
            for (int j = i + 1; j < stops.size(); j++) {
                TourStop d = stops.get(j);
                if (!d.getType().isPickup() && d.getStatus() == TourStop.StopStatus.FAIT && d.getCompletedAt() != null) {
                    delivery = d;
                    break;
                }
            }
            Long minutes = delivery != null
                    ? Math.max(0, Duration.between(pickup.getCompletedAt(), delivery.getCompletedAt()).toMinutes())
                    : null;
            if (minutes != null && (max == null || minutes > max)) {
                max = minutes;
            }
            transits.add(new Transit(pickup.getId(), pickup.getSite().getName(), pickup.getCompletedAt(),
                    delivery != null ? delivery.getId() : null, delivery != null ? delivery.getSite().getName() : null,
                    delivery != null ? delivery.getCompletedAt() : null, minutes, minutes != null && minutes > limit));
        }
        return new Reconciliation(collected, delivered, gap, MedicalProduct.total(collected), MedicalProduct.total(delivered),
                balanced, transits, limit, max);
    }

    /** Résumé lisible des écarts : « Sang : 8 collecté(s), 7 remis(s) ». */
    public String describeGap() {
        List<String> parts = new ArrayList<>();
        gap.forEach((p, diff) -> parts.add(p.getLabel() + " : " + collected.getOrDefault(p, 0) + " collecté(s), "
                + delivered.getOrDefault(p, 0) + " remis"));
        return String.join(" ; ", parts);
    }
}
