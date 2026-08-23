package com.fleethub.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TruckDetailDto(
        TruckKpis kpis,
        List<DailyPointDto> dailyTrend,
        List<MaintenanceDto> maintenance,
        List<FuelDto> fuels,
        List<TripDto> trips,
        List<EventDto> events,
        List<CoupleDetailDto.CostBreakdownDto> costBreakdown
) {
    public record TruckKpis(
            Long truckId,
            String registration,
            String brand,
            String model,
            Integer modelYear,
            String truckType,
            String fuelType,
            String currentStatus,
            Long assignmentId,
            Long driverId,
            String driverName,
            int daysInPeriod,
            double totalKm,
            double totalDrivingHours,
            int tripCount,
            int eventCount,
            double consumptionPer100Km,
            double consumptionDeltaPct,
            double maintenanceComplianceRate,
            double unplannedDowntimeRate,
            double unplannedDowntimeHours,
            double truckUptimeRate,
            double utilizationRate,
            double loadedRunRate,
            double totalCost,
            double costPerKm,
            List<String> alerts
    ) {}

    public record DailyPointDto(String date, double km, double liters, double cost) {}

    public record MaintenanceDto(
            Long id,
            LocalDate scheduledDate,
            LocalDate doneDate,
            String type,
            boolean planned,
            Double cost,
            boolean doneOnTime,
            String status
    ) {}

    public record FuelDto(Long id, LocalDate date, double liters, double amount, double odometerKm) {}

    public record EventDto(Long id, LocalDateTime timestamp, String type, int severity, Double speedKph, Double durationSec) {}
}
