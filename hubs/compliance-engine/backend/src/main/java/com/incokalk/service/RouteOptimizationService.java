package com.incokalk.service;

import com.incokalk.dto.shipment.RouteOptimizationRequest;
import com.incokalk.dto.shipment.RouteOptimizationResult;
import com.incokalk.dto.shipment.RouteOptimizationResult.StopResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RouteOptimizationService {

    private static final Map<String, double[]> CITY_COORDS = Map.ofEntries(
        Map.entry("PARIS", new double[]{48.8566, 2.3522}),
        Map.entry("LYON", new double[]{45.7640, 4.8357}),
        Map.entry("MARSEILLE", new double[]{43.2965, 5.3698}),
        Map.entry("TOULOUSE", new double[]{43.6047, 1.4442}),
        Map.entry("BORDEAUX", new double[]{44.8378, -0.5792}),
        Map.entry("LILLE", new double[]{50.6292, 3.0573}),
        Map.entry("NANTES", new double[]{47.2184, -1.5536}),
        Map.entry("STRASBOURG", new double[]{48.5734, 7.7521}),
        Map.entry("BRUXELLES", new double[]{50.8503, 4.3517}),
        Map.entry("AMSTERDAM", new double[]{52.3676, 4.9041}),
        Map.entry("FRANCFORT", new double[]{50.1109, 8.6821}),
        Map.entry("MADRID", new double[]{40.4168, -3.7038}),
        Map.entry("ROME", new double[]{41.9028, 12.4964}),
        Map.entry("LONDRES", new double[]{51.5074, -0.1278}),
        Map.entry("HAMBURG", new double[]{53.5511, 9.9937}),
        Map.entry("BARCELONE", new double[]{41.3874, 2.1686}),
        Map.entry("CASABLANCA", new double[]{33.5731, -7.5898}),
        Map.entry("ALGER", new double[]{36.7538, 3.0588}),
        Map.entry("TUNIS", new double[]{36.8065, 10.1815}),
        Map.entry("WARSZAWA", new double[]{52.2297, 21.0122}),
        Map.entry("PRAGUE", new double[]{50.0755, 14.4378}),
        Map.entry("VIENNE", new double[]{48.2082, 16.3738}),
        Map.entry("ZURICH", new double[]{47.3769, 8.5417}),
        Map.entry("ROTTERDAM", new double[]{51.9244, 4.4777}),
        Map.entry("ANVERS", new double[]{51.2194, 4.4025}),
        Map.entry("GDANSK", new double[]{54.3520, 18.6466}),
        Map.entry("LE HAVRE", new double[]{49.4944, 0.1079}),
        Map.entry("BELFORT", new double[]{47.6380, 6.8630})
    );

    private static final double AVG_SPEED_KMH = 70.0;
    private static final double FUEL_PRICE_DEFAULT = 1.75;
    private static final double CONSUMPTION_DEFAULT = 32.0;
    private static final double TOLL_PER_KM = 0.08;

    public RouteOptimizationResult optimize(RouteOptimizationRequest request) {
        List<RouteOptimizationRequest.StopPoint> stops = new ArrayList<>(request.getStops());
        String origin = request.getOriginCountry();
        String dest = request.getDestinationCountry();

        double fuelPrice = request.getFuelPricePerLiter() != null ? request.getFuelPricePerLiter() : FUEL_PRICE_DEFAULT;
        double consumption = request.getConsumptionPer100km() != null ? request.getConsumptionPer100km() : CONSUMPTION_DEFAULT;

        stops.add(0, createStop(origin, "Départ"));
        stops.add(createStop(dest, "Arrivée"));

        List<StopResult> ordered = new ArrayList<>();
        int cumulative = 0;
        double totalDist = 0;

        for (int i = 0; i < stops.size(); i++) {
            RouteOptimizationRequest.StopPoint current = stops.get(i);
            double dist = 0;
            if (i > 0) {
                dist = haversine(getCoords(stops.get(i - 1)), getCoords(current));
                cumulative += (int) Math.round(dist);
                totalDist += dist;
            }
            ordered.add(StopResult.builder()
                .order(i + 1)
                .city(current.getCity())
                .country(current.getCountry() != null ? current.getCountry() : "")
                .distanceFromPreviousKm(Math.round(dist))
                .cumulativeDistanceKm(cumulative)
                .build());
        }

        int hours = (int) Math.round(totalDist / AVG_SPEED_KMH);
        double fuelLiters = Math.round(totalDist * consumption / 100.0);
        double fuelCost = Math.round(fuelLiters * fuelPrice * 100.0) / 100.0;
        double tollCost = Math.round(totalDist * TOLL_PER_KM * 100.0) / 100.0;

        String rec;
        if (totalDist < 300) {
            rec = "Trajet court — livraison possible enournée.";
        } else if (totalDist < 1000) {
            rec = "Trajet moyen — prévoir un overnight ou départ très matinal.";
        } else {
            rec = "Trajet long — prévoir des relaises et pauses obligatoires (toutes les 4h).";
        }

        return RouteOptimizationResult.builder()
            .totalDistanceKm(Math.round(totalDist))
            .totalStops(stops.size())
            .estimatedHours(hours)
            .estimatedFuelLiters(fuelLiters)
            .estimatedFuelCost(fuelCost)
            .estimatedTollCost(tollCost)
            .orderedStops(ordered)
            .recommendation(rec)
            .build();
    }

    private RouteOptimizationRequest.StopPoint createStop(String countryOrCity, String label) {
        RouteOptimizationRequest.StopPoint sp = new RouteOptimizationRequest.StopPoint();
        sp.setCity(label);
        sp.setCountry(countryOrCity);
        String key = countryOrCity.toUpperCase().trim();
        double[] coords = CITY_COORDS.getOrDefault(key, new double[]{0, 0});
        sp.setLatitude(coords[0]);
        sp.setLongitude(coords[1]);
        return sp;
    }

    private double[] getCoords(RouteOptimizationRequest.StopPoint stop) {
        if (stop.getLatitude() != null && stop.getLongitude() != null
            && (stop.getLatitude() != 0 || stop.getLongitude() != 0)) {
            return new double[]{stop.getLatitude(), stop.getLongitude()};
        }
        String key = stop.getCity().toUpperCase().trim();
        return CITY_COORDS.getOrDefault(key, new double[]{0, 0});
    }

    private double haversine(double[] a, double[] b) {
        double R = 6371;
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
    }
}
