package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteOptimizationResult {
    private double totalDistanceKm;
    private int totalStops;
    private int estimatedHours;
    private double estimatedFuelLiters;
    private double estimatedFuelCost;
    private double estimatedTollCost;
    private List<StopResult> orderedStops;
    private String recommendation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopResult {
        private int order;
        private String city;
        private String country;
        private double distanceFromPreviousKm;
        private int cumulativeDistanceKm;
    }
}
