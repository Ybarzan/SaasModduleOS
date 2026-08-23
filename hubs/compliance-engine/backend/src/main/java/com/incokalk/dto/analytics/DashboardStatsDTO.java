package com.incokalk.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    // Shipments
    private long totalShipments;
    private long activeShipments;
    private long deliveredShipments;
    private long draftShipments;
    private long cancelledShipments;

    // Costs
    private double totalShippingCost;
    private double averageShippingCost;
    private double maxShippingCost;
    private double minShippingCost;

    // Weight & Volume
    private double totalWeightKg;
    private double totalVolumeM3;
    private double averageWeightKg;
    private double averageVolumeM3;

    // Goods value
    private double totalGoodsValue;

    // Simulations
    private long totalSimulations;
    private long simulationsThisMonth;

    // Carriers
    private long totalCarriers;
    private long activeCarriers;

    // CO2
    private double totalCo2Kg;
    private double averageCo2PerShipment;

    // Time period
    private String period;
}
