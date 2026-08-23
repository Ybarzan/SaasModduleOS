package com.fleethub.service;

import com.fleethub.dto.CoupleKpiDto;
import com.fleethub.dto.DashboardSummaryDto;
import com.fleethub.model.Truck;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final KpiService kpiService;
    private final TruckRepository truckRepository;
    private final TachographDayRepository tachographRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "dashboard.summary", keyGenerator = "tenantKeyGenerator")
    public DashboardSummaryDto summary(String period) {
        List<CoupleKpiDto> couples = kpiService.computeAllCouples(period);
        List<Truck> trucks = truckRepository.findByCompanyId(TenantContext.companyId()).stream()
                .filter(Truck::isActive).toList();

        double avgCostPerKm = couples.isEmpty() ? 0 : couples.stream().mapToDouble(CoupleKpiDto::costPerKm).average().orElse(0);
        double avgUtilization = couples.isEmpty() ? 0 : couples.stream().mapToDouble(CoupleKpiDto::utilizationRate).average().orElse(0);
        double avgMaintenance = couples.isEmpty() ? 0 : couples.stream().mapToDouble(CoupleKpiDto::maintenanceComplianceRate).average().orElse(0);
        double avgDowntime = couples.isEmpty() ? 0 : couples.stream().mapToDouble(CoupleKpiDto::unplannedDowntimeRate).average().orElse(0);
        double avgScore = couples.isEmpty() ? 0 : couples.stream().mapToDouble(CoupleKpiDto::performanceScore).average().orElse(0);
        long totalKm = couples.stream().mapToLong(c -> Math.round(c.totalKm())).sum();
        long alerts = couples.stream().mapToLong(c -> c.alerts().size()).sum();

        long inService = trucks.stream().filter(t -> t.getCurrentStatus() == Truck.VehicleStatus.ROULAGE).count();
        long stopped = trucks.stream().filter(t -> t.getCurrentStatus() == Truck.VehicleStatus.ARRET
                || t.getCurrentStatus() == Truck.VehicleStatus.REPOS).count();
        long alerted = trucks.stream().filter(t -> t.getCurrentStatus() == Truck.VehicleStatus.ALERTE
                || t.getCurrentStatus() == Truck.VehicleStatus.IMMOBILISE).count();

        KpiService.PeriodRange range = kpiService.resolvePeriod(period);
        long nonCompliantDays = tachographRepository.countNonCompliantBetween(
                TenantContext.companyId(), range.fromDate(), range.toDate());

        List<CoupleKpiDto> top = couples.stream().limit(5).toList();

        return new DashboardSummaryDto(
                trucks.size(), couples.size(), (int) alerts, totalKm,
                round(avgCostPerKm), round(avgUtilization), round(avgMaintenance),
                round(avgDowntime), round(avgScore),
                (int) inService, (int) stopped, (int) alerted,
                (int) nonCompliantDays, top,
                kpiService.computeNorthStarWidgets(period));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
