package com.fleethub.dto;

import java.util.List;

public record CoupleDetailDto(
        CoupleKpiDto kpis,
        List<DailyPointDto> dailyTrend,
        List<EventBreakdownDto> eventBreakdown,
        List<CostBreakdownDto> costBreakdown
) {
    public record DailyPointDto(String date, double km, double cost, int events, double liters) {}

    public record EventBreakdownDto(String type, long count) {}

    public record CostBreakdownDto(String category, double amount) {}
}
