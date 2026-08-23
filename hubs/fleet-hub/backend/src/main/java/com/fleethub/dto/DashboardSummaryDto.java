package com.fleethub.dto;

import com.fleethub.service.KpiService.KpiWidgetDto;

import java.util.List;

public record DashboardSummaryDto(
        int fleetSize,
        int activeCouples,
        int alertsCount,
        long totalKm,
        double costPerKm,
        double utilizationRate,
        double maintenanceComplianceRate,
        double unplannedDowntimeRate,
        double globalPerformanceScore,
        int vehiclesInService,
        int vehiclesStopped,
        int vehiclesAlerted,
        int nonCompliantDrivingDays,
        List<CoupleKpiDto> topCouples,
        List<KpiWidgetDto> northStars
) {}
