package com.incokalk.service;

import com.incokalk.dto.shipment.TruckingRateRequest;
import com.incokalk.dto.shipment.TruckingRateResult;
import com.incokalk.dto.shipment.TruckingRateResult.TruckOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TruckingService {

    private static final Set<String> NEARSHORE = Set.of("MA","TN","DZ","TR","PL","RO","HU","BG","PT","ES");
    private static final Set<String> EUROPE = Set.of("FR","DE","NL","BE","IT","ES","PL","RO","HU","BG","CZ","AT","PT","IE","SE","DK","FI");

    private static final double PALLET_WEIGHT_KG = 800;
    private static final double STANDARD_PALLET_CM = 120;

    public TruckingRateResult calculateRates(TruckingRateRequest req) {
        double weight = req.getWeightKg() != null ? req.getWeightKg() : 0;
        double volume = req.getVolumeM3() != null ? req.getVolumeM3() : 0;
        int pallets = req.getPalletCount() != null ? req.getPalletCount()
            : Math.max(1, (int) Math.ceil(Math.max(weight / PALLET_WEIGHT_KG, volume / 0.9)));

        String origin = req.getOriginCountry().toUpperCase();
        String dest = req.getDestinationCountry().toUpperCase();

        List<TruckOption> options = new ArrayList<>();

        options.add(buildLTLOption(origin, dest, weight, volume, pallets));
        options.add(buildFTLOption(origin, dest, weight, volume, pallets));
        options.add(buildExpressOption(origin, dest, weight, volume, pallets));
        applyRegulatoryTransit(options, req.getDistanceKm());

        TruckOption recommended = options.stream()
            .min(Comparator.comparingDouble(TruckOption::getCostEur))
            .orElse(options.get(0));
        recommended.setRecommended(true);

        return TruckingRateResult.builder()
            .originCountry(origin)
            .destinationCountry(dest)
            .estimatedPallets(pallets)
            .totalWeightKg(weight)
            .totalVolumeM3(volume)
            .options(options)
            .recommended(recommended)
            .build();
    }

    private TruckOption buildLTLOption(String origin, String dest, double weight, double volume, int pallets) {
        double baseRate = isNearshore(origin) ? 65 : isEuropeRoute(origin, dest) ? 45 : 120;
        double cost = pallets * baseRate * (1 + distanceFactor(origin, dest) * 0.1);
        double co2 = pallets * 12 * distanceFactor(origin, dest);

        return TruckOption.builder()
            .mode("LTL")
            .label("LTL - Less Than Truckload")
            .costEur(round(cost))
            .co2Kg(round(co2))
            .description(pallets + " palette" + (pallets > 1 ? "s" : "") + " en groupage — idéal pour < 6 pallets")
            .costPerPallet(round(cost / pallets))
            .build();
    }

    private TruckOption buildFTLOption(String origin, String dest, double weight, double volume, int pallets) {
        double ftlCost;
        String truckType;
        if (pallets <= 17) {
            ftlCost = isNearshore(origin) ? 1200 : isEuropeRoute(origin, dest) ? 950 : 2800;
            truckType = "20t (17 palettes)";
        } else if (pallets <= 33) {
            ftlCost = isNearshore(origin) ? 1800 : isEuropeRoute(origin, dest) ? 1400 : 4200;
            truckType = "33t (33 palettes)";
        } else {
            ftlCost = isNearshore(origin) ? 2500 : isEuropeRoute(origin, dest) ? 2000 : 5500;
            truckType = "Double plancher (50+ palettes)";
        }
        double cost = ftlCost * (1 + distanceFactor(origin, dest) * 0.05);
        double co2 = pallets <= 17 ? 85 : pallets <= 33 ? 140 : 210;

        return TruckOption.builder()
            .mode("FTL")
            .label("FTL - " + truckType)
            .costEur(round(cost))
            .co2Kg(round(co2))
            .description("Camion complet " + truckType + " — plus économique au-delà de 6 palettes")
            .costPerPallet(round(cost / pallets))
            .build();
    }

    private TruckOption buildExpressOption(String origin, String dest, double weight, double volume, int pallets) {
        double cost = pallets * 150 * (1 + distanceFactor(origin, dest) * 0.15);
        double co2 = pallets * 25 * distanceFactor(origin, dest);

        return TruckOption.builder()
            .mode("EXPRESS")
            .label("Express - Livraison prioritaire")
            .costEur(round(cost))
            .co2Kg(round(co2))
            .description("Transport express dédié, double équipage")
            .costPerPallet(round(cost / pallets))
            .build();
    }

    // ── Délais bornés par le Règlement (CE) n° 561/2006 ──────────────────────────
    /** Art. 6 §1 : durée de conduite journalière maximale (10 h seulement 2 fois par semaine). */
    static final double MAX_DAILY_DRIVING_H = 9.0;
    /**
     * HYPOTHÈSE à valider (pas une donnée réglementaire) : vitesse commerciale moyenne d'un
     * poids lourd sur autoroute/nationale, limite légale 90 km/h (dir. 92/6/CEE, limiteur).
     */
    static final double AVG_TRUCK_SPEED_KMH = 70.0;
    /** HYPOTHÈSE : une journée de groupage/dégroupage en plateforme pour le LTL. */
    static final int LTL_CROSSDOCK_DAYS = 1;

    /**
     * Jours de conduite minimum pour couvrir la distance. Équipage simple : 9 h/jour.
     * Double équipage (art. 8 §5 : repos journalier de 9 h dans une période de 30 h) : les deux
     * conducteurs se relaient, 18 h de conduite par période de 24 h au plus.
     */
    static int drivingDays(double distanceKm, int drivers) {
        double kmPerDay = MAX_DAILY_DRIVING_H * drivers * AVG_TRUCK_SPEED_KMH;
        return Math.max(1, (int) Math.ceil(distanceKm / kmPerDay));
    }

    /** Aucun délai forfaitaire : sans distance, transitDays reste null et la raison est indiquée. */
    private void applyRegulatoryTransit(List<TruckOption> options, Double distanceKm) {
        for (TruckOption o : options) {
            if (distanceKm == null || distanceKm <= 0) {
                o.setTransitDays(null);
                o.setDescription(o.getDescription() + " — délai non calculé : distance (km) requise");
                continue;
            }
            int days = switch (o.getMode()) {
                case "EXPRESS" -> drivingDays(distanceKm, 2);
                case "LTL" -> drivingDays(distanceKm, 1) + LTL_CROSSDOCK_DAYS;
                default -> drivingDays(distanceKm, 1);
            };
            o.setTransitDays(days);
        }
    }

    private boolean isNearshore(String country) { return NEARSHORE.contains(country); }
    private boolean isEuropeRoute(String o, String d) { return EUROPE.contains(o) && EUROPE.contains(d); }

    private double distanceFactor(String origin, String dest) {
        if (origin.equals(dest)) return 0.5;
        if (isEuropeRoute(origin, dest)) return 1.0;
        if (isNearshore(origin)) return 1.5;
        return 3.0;
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
