package com.fleethub.dto;

import java.util.List;

public record CoupleKpiDto(
        Long assignmentId,
        Long driverId,
        String driverName,
        String licenseNumber,
        String driverPhone,
        Long truckId,
        String registration,
        String brand,
        String model,
        String truckType,
        String fuelType,
        int daysInPeriod,

        // North Star
        double costPerKm,
        double utilizationRate,
        double maintenanceComplianceRate,
        double unplannedDowntimeRate,

        // Conduite (chauffeur)
        double riskEventsPer1000Km,
        int riskEventsTotal,
        double ecoScore,
        double driveTimeShare,
        double idleShare,
        double onTimeRate,
        double drivingTimeComplianceRate,

        // Camion
        double consumptionPer100Km,
        double consumptionDeltaPct,
        double truckUptimeRate,
        double unplannedDowntimeHours,

        // Couple
        double totalKm,
        double totalDrivingHours,
        double totalCost,
        double loadedRunRate,
        double performanceScore,
        List<String> alerts
) {}
