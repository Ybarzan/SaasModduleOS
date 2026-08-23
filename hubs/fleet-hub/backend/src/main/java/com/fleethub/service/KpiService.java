package com.fleethub.service;

import com.fleethub.dto.CoupleDetailDto;
import com.fleethub.dto.CoupleKpiDto;
import com.fleethub.dto.TripDto;
import com.fleethub.dto.TruckDetailDto;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpiService {

    private final AssignmentRepository assignmentRepository;
    private final TripRepository tripRepository;
    private final DrivingEventRepository eventRepository;
    private final TachographDayRepository tachoRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final FuelRecordRepository fuelRepository;
    private final CostRecordRepository costRepository;
    private final TruckRepository truckRepository;

    public static final String PERIOD_DAY = "DAY";
    public static final String PERIOD_WEEK = "WEEK";
    public static final String PERIOD_MONTH = "MONTH";

    public record PeriodRange(LocalDateTime from, LocalDateTime to, int days) {
        public LocalDate fromDate() { return from.toLocalDate(); }
        public LocalDate toDate() { return to.toLocalDate(); }
    }

    public PeriodRange resolvePeriod(String period) {
        LocalDate today = LocalDate.now();
        LocalDateTime from;
        switch (period == null ? PERIOD_MONTH : period) {
            case PERIOD_DAY -> from = today.atStartOfDay();
            case PERIOD_WEEK -> from = today.minusWeeks(1).atStartOfDay();
            default -> from = today.minusMonths(1).atStartOfDay();
        }
        LocalDateTime to = LocalDateTime.now();
        int days = (int) ChronoUnit.DAYS.between(from.toLocalDate(), today) + 1;
        return new PeriodRange(from, to, days);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "kpi.couples", keyGenerator = "tenantKeyGenerator")
    public List<CoupleKpiDto> computeAllCouples(String period) {
        PeriodRange range = resolvePeriod(period);
        return assignmentRepository.findByActiveTrue(TenantContext.companyId()).stream()
                .map(a -> compute(a, range))
                .sorted(Comparator.comparingDouble(CoupleKpiDto::performanceScore).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public CoupleKpiDto computeForAssignment(Long assignmentId, String period) {
        PeriodRange range = resolvePeriod(period);
        DriverTruckAssignment assignment = assignmentRepository
                .findByIdAndCompanyId(assignmentId, TenantContext.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Affectation inconnue: " + assignmentId));
        return compute(assignment, range);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "kpi.detail", keyGenerator = "tenantKeyGenerator")
    public CoupleDetailDto computeDetail(Long assignmentId, String period) {
        PeriodRange range = resolvePeriod(period);
        DriverTruckAssignment a = assignmentRepository
                .findByIdAndCompanyId(assignmentId, TenantContext.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Affectation inconnue: " + assignmentId));
        CoupleKpiDto kpis = compute(a, range);
        return new CoupleDetailDto(kpis, dailyTrend(a, range), eventBreakdown(a, range), costBreakdown(a, range));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "kpi.truck", keyGenerator = "tenantKeyGenerator")
    public TruckDetailDto computeTruckDetail(Long truckId, String period) {
        PeriodRange range = resolvePeriod(period);
        Truck t = truckRepository.findByIdAndCompanyId(truckId, TenantContext.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Camion inconnu: " + truckId));

        DriverTruckAssignment a = assignmentRepository.findByActiveTrue(TenantContext.companyId()).stream()
                .filter(x -> x.getTruck().getId().equals(t.getId())).findFirst().orElse(null);
        Driver d = a != null ? a.getDriver() : null;

        double km = tripRepository.sumDistanceByTruckBetween(t, range.from(), range.to());
        List<Trip> trips = tripRepository.findByTruckAndStartTimeBetween(t, range.from(), range.to());
        List<DrivingEvent> events = eventRepository.findByTruckAndTimestampBetween(t, range.from(), range.to());
        List<MaintenanceRecord> maintenance = maintenanceRepository.findByTruckAndScheduledDateBetween(t, range.fromDate(), range.toDate());
        List<FuelRecord> fuels = fuelRepository.findByTruckAndDateBetween(t, range.fromDate(), range.toDate());
        List<CostRecord> costs = costRepository.findByTruck(t).stream()
                .filter(c -> c.getBillingMonth().atDay(1).isAfter(range.fromDate().withDayOfMonth(1).minusDays(1)))
                .toList();

        double drivingHours = trips.stream().mapToDouble(Trip::durationHours).sum();
        double totalCost = costs.stream().mapToDouble(CostRecord::getAmount).sum();
        double costPerKm = km > 0 ? round(totalCost / km) : 0;

        double availableHours = range.days() * 14.0;
        double utilization = availableHours > 0 ? round(Math.min(100, drivingHours / availableHours * 100)) : 0;

        List<MaintenanceRecord> planned = maintenance.stream().filter(MaintenanceRecord::isPlanned).toList();
        double maintenanceCompliance = !planned.isEmpty()
                ? round(planned.stream().filter(MaintenanceRecord::isDoneOnTime).count() * 100.0 / planned.size())
                : fallbackMaintenanceCompliance(t);

        double unplannedDowntimeHours = maintenance.stream()
                .filter(m -> !m.isPlanned() && m.getDoneDate() != null)
                .mapToDouble(m -> Math.max(1, ChronoUnit.HOURS.between(
                        m.getScheduledDate().atStartOfDay(), m.getDoneDate().atStartOfDay())))
                .sum();
        double totalPeriodHours = range.days() * 24.0;
        double unplannedDowntimeRate = totalPeriodHours > 0
                ? round(Math.min(100, unplannedDowntimeHours / totalPeriodHours * 100))
                : 0;
        double truckUptimeRate = round(Math.max(0, 100 - unplannedDowntimeRate));

        double consumptionPer100 = computeConsumption(t, km, fuels);
        double expected = t.getExpectedConsumptionL100Km() != null ? t.getExpectedConsumptionL100Km() : 32;
        double consumptionDeltaPct = consumptionPer100 > 0 ? round((consumptionPer100 - expected) / expected * 100) : 0;

        double loadedKm = trips.stream().filter(Trip::isLoaded).mapToDouble(Trip::getDistanceKm).sum();
        double loadedRunRate = km > 0 ? round(loadedKm / km * 100) : 0;

        List<String> alerts = buildTruckAlerts(t, maintenanceCompliance, unplannedDowntimeRate, consumptionDeltaPct);

        TruckDetailDto.TruckKpis kpis = new TruckDetailDto.TruckKpis(
                t.getId(), t.getRegistration(), t.getBrand(), t.getModel(), t.getModelYear(),
                t.getTruckType().name(), t.getFuelType().name(),
                t.getCurrentStatus() != null ? t.getCurrentStatus().name() : "ARRET",
                a != null ? a.getId() : null,
                d != null ? d.getId() : null,
                d != null ? d.getFirstName() + " " + d.getLastName() : null,
                range.days(),
                round(km), round(drivingHours), trips.size(), events.size(),
                consumptionPer100, consumptionDeltaPct,
                maintenanceCompliance, unplannedDowntimeRate, round(unplannedDowntimeHours),
                truckUptimeRate, utilization, loadedRunRate,
                round(totalCost), costPerKm, alerts);

        return new TruckDetailDto(
                kpis,
                truckDailyTrend(t, d, range),
                maintenance.stream().map(m -> new TruckDetailDto.MaintenanceDto(
                        m.getId(), m.getScheduledDate(), m.getDoneDate(), m.getType().name(),
                        m.isPlanned(), m.getCost(), m.isDoneOnTime(), m.getStatus().name())).toList(),
                fuels.stream().map(f -> new TruckDetailDto.FuelDto(
                        f.getId(), f.getDate(), f.getLiters(), f.getAmount(), f.getOdometerKm())).toList(),
                trips.stream().map(this::toTripDto).toList(),
                events.stream().map(e -> new TruckDetailDto.EventDto(
                        e.getId(), e.getTimestamp(), e.getType().name(), e.getSeverity(),
                        e.getSpeedKph(), e.getDurationSec())).toList(),
                costBreakdownForTruck(t, range));
    }

    private List<TruckDetailDto.DailyPointDto> truckDailyTrend(Truck t, Driver d, PeriodRange range) {
        List<Trip> trips = tripRepository.findByTruckAndStartTimeBetween(t, range.from(), range.to());
        List<FuelRecord> fuels = fuelRepository.findByTruckAndDateBetween(t, range.fromDate(), range.toDate());
        List<CostRecord> costs = costRepository.findByTruck(t);

        List<TruckDetailDto.DailyPointDto> result = new ArrayList<>();
        LocalDate day = range.fromDate();
        while (!day.isAfter(range.toDate())) {
            LocalDate cur = day;
            double km = trips.stream().filter(tr -> tr.getStartTime().toLocalDate().equals(cur))
                    .mapToDouble(Trip::getDistanceKm).sum();
            double liters = fuels.stream().filter(f -> f.getDate().equals(cur))
                    .mapToDouble(FuelRecord::getLiters).sum();
            double cost = costs.stream().filter(c -> c.getBillingMonth().atDay(1).getMonth() == cur.getMonth()
                            && c.getBillingMonth().atDay(1).getYear() == cur.getYear())
                    .mapToDouble(CostRecord::getAmount).sum() / Math.max(1, (double) cur.lengthOfMonth());
            result.add(new TruckDetailDto.DailyPointDto(cur.toString(), round(km), round(liters), round(cost)));
            day = day.plusDays(1);
        }
        return result;
    }

    private List<CoupleDetailDto.CostBreakdownDto> costBreakdownForTruck(Truck t, PeriodRange range) {
        Map<String, Double> byCategory = costRepository.findByTruck(t).stream()
                .filter(c -> !c.getBillingMonth().atDay(1).isBefore(range.fromDate().withDayOfMonth(1)))
                .collect(Collectors.groupingBy(
                        c -> c.getCategory().name(), Collectors.summingDouble(CostRecord::getAmount)));
        return byCategory.entrySet().stream()
                .map(e -> new CoupleDetailDto.CostBreakdownDto(e.getKey(), round(e.getValue())))
                .sorted(Comparator.comparingDouble(CoupleDetailDto.CostBreakdownDto::amount).reversed())
                .toList();
    }

    private TripDto toTripDto(Trip t) {
        return new TripDto(
                t.getId(),
                t.getDriver().getId(),
                t.getDriver().getFirstName() + " " + t.getDriver().getLastName(),
                t.getTruck().getId(),
                t.getTruck().getRegistration(),
                t.getStartTime(), t.getEndTime(),
                t.getDistanceKm(), t.getCargoWeightTons(),
                t.isLoaded(), t.getStatus().name(), t.isOnTime());
    }

    private List<String> buildTruckAlerts(Truck t, double maintenanceCompliance,
                                          double downtimeRate, double consumptionDeltaPct) {
        List<String> alerts = new ArrayList<>();
        if (maintenanceCompliance < 80) {
            alerts.add("Maintenance en retard (conformité " + Math.round(maintenanceCompliance) + "%)");
        }
        if (downtimeRate > 10) {
            alerts.add("Immobilisation imprévue élevée (" + Math.round(downtimeRate) + "% du temps)");
        }
        if (consumptionDeltaPct > 10) {
            alerts.add("Surconsommation (" + Math.round(consumptionDeltaPct) + "% au-dessus de la référence)");
        }
        if (t.getCurrentStatus() == Truck.VehicleStatus.ALERTE) {
            alerts.add("Véhicule en état d'alerte");
        }
        if (t.getCurrentStatus() == Truck.VehicleStatus.IMMOBILISE) {
            alerts.add("Véhicule immobilisé");
        }
        return alerts;
    }

    public record KpiWidgetDto(String key, double value, double deltaPct, List<Double> sparkline) {}

    @Transactional(readOnly = true)
    @Cacheable(value = "kpi.northstar", keyGenerator = "tenantKeyGenerator")
    public List<KpiWidgetDto> computeNorthStarWidgets(String period) {
        PeriodRange current = resolvePeriod(period);
        List<DriverTruckAssignment> couples = assignmentRepository.findByActiveTrue(TenantContext.companyId());
        if (couples.isEmpty()) return List.of();

        double[] cur = averageNorthStars(couples, current);

        List<List<Double>> series = new ArrayList<>();
        for (int k = 0; k < 4; k++) series.add(new ArrayList<>());
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            double[] vals = dailyNorthStars(couples, today.minusDays(i));
            for (int k = 0; k < 4; k++) series.get(k).add(vals[k]);
        }

        String[] keys = {"costPerKm", "utilization", "maintenance", "downtime"};
        List<KpiWidgetDto> out = new ArrayList<>();
        for (int k = 0; k < keys.length; k++) {
            out.add(new KpiWidgetDto(keys[k], round(cur[k]), sparkDelta(series.get(k)), series.get(k)));
        }
        return out;
    }

    private double[] averageNorthStars(List<DriverTruckAssignment> couples, PeriodRange range) {
        double cost = 0, util = 0, maint = 0, down = 0;
        for (DriverTruckAssignment a : couples) {
            CoupleKpiDto k = compute(a, range);
            cost += k.costPerKm();
            util += k.utilizationRate();
            maint += k.maintenanceComplianceRate();
            down += k.unplannedDowntimeRate();
        }
        int n = couples.size();
        return new double[]{cost / n, util / n, maint / n, down / n};
    }

    private double[] dailyNorthStars(List<DriverTruckAssignment> couples, LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay().minusSeconds(1);
        double cost = 0, util = 0, maint = 0, down = 0;
        for (DriverTruckAssignment a : couples) {
            Driver d = a.getDriver();
            Truck t = a.getTruck();
            double km = Math.max(
                    tripRepository.sumDistanceByDriverBetween(d, from, to),
                    tripRepository.sumDistanceByTruckBetween(t, from, to));
            double dayCost = costRepository.findByTruckAndDriver(t, d).stream()
                    .filter(c -> c.getBillingMonth().atDay(1).getYear() == day.getYear()
                            && c.getBillingMonth().atDay(1).getMonth() == day.getMonth())
                    .mapToDouble(CostRecord::getAmount).sum() / Math.max(1, (double) day.lengthOfMonth());
            cost += km > 0 ? dayCost / km : 0;

            double drivingHours = tripRepository.findByDriverAndStartTimeBetween(d, from, to)
                    .stream().mapToDouble(Trip::durationHours).sum();
            util += Math.min(100, drivingHours / 14.0 * 100);

            maint += fallbackMaintenanceCompliance(t);

            double downHours = maintenanceRepository.findByTruckAndScheduledDateBetween(t, day, day).stream()
                    .filter(m -> !m.isPlanned() && m.getDoneDate() != null)
                    .mapToDouble(m -> Math.max(1, ChronoUnit.HOURS.between(
                            m.getScheduledDate().atStartOfDay(), m.getDoneDate().atStartOfDay())))
                    .sum();
            down += Math.min(100, downHours / 24.0 * 100);
        }
        int n = couples.size();
        return new double[]{cost / n, util / n, maint / n, down / n};
    }

    private double sparkDelta(List<Double> series) {
        if (series.size() < 6) return 0;
        double first = (series.get(0) + series.get(1) + series.get(2)) / 3.0;
        double last = (series.get(series.size() - 1) + series.get(series.size() - 2) + series.get(series.size() - 3)) / 3.0;
        if (first == 0) return 0;
        return round((last - first) / first * 100);
    }

    private CoupleKpiDto compute(DriverTruckAssignment a, PeriodRange range) {
        Driver d = a.getDriver();
        Truck t = a.getTruck();

        double km = tripRepository.sumDistanceByDriverBetween(d, range.from(), range.to());
        double kmTruck = tripRepository.sumDistanceByTruckBetween(t, range.from(), range.to());
        double kmMax = Math.max(km, kmTruck);

        List<Trip> trips = tripRepository.findByDriverAndStartTimeBetween(d, range.from(), range.to());
        List<DrivingEvent> events = eventRepository.findByDriverAndTimestampBetween(d, range.from(), range.to());
        List<DrivingEvent> riskEvents = events.stream()
                .filter(e -> e.getType() != DrivingEvent.EventType.RALENTI).toList();
        List<TachographDay> tachoDays = tachoRepository.findByDriverAndDateBetween(d, range.fromDate(), range.toDate());
        List<MaintenanceRecord> maintenance = maintenanceRepository.findByTruckAndScheduledDateBetween(t, range.fromDate(), range.toDate());
        List<FuelRecord> fuels = fuelRepository.findByTruckAndDateBetween(t, range.fromDate(), range.toDate());
        List<CostRecord> costs = costRepository.findByTruckAndDriver(t, d).stream()
                .filter(c -> c.getBillingMonth().atDay(1).isAfter(range.fromDate().withDayOfMonth(1).minusDays(1)))
                .toList();

        double drivingHours = trips.stream().mapToDouble(Trip::durationHours).sum();

        // ---- North Star ----
        double totalCost = costs.stream().mapToDouble(CostRecord::getAmount).sum();
        double costPerKm = kmMax > 0 ? round(totalCost / kmMax) : 0;

        double availableHours = range.days() * 14.0;
        double utilization = availableHours > 0 ? round(Math.min(100, drivingHours / availableHours * 100)) : 0;

        List<MaintenanceRecord> planned = maintenance.stream().filter(MaintenanceRecord::isPlanned).toList();
        double maintenanceCompliance = !planned.isEmpty()
                ? round(planned.stream().filter(MaintenanceRecord::isDoneOnTime).count() * 100.0 / planned.size())
                : fallbackMaintenanceCompliance(t);

        double unplannedDowntimeHours = maintenance.stream()
                .filter(m -> !m.isPlanned() && m.getDoneDate() != null)
                .mapToDouble(m -> Math.max(1, ChronoUnit.HOURS.between(m.getScheduledDate().atStartOfDay(), m.getDoneDate().atStartOfDay())))
                .sum();
        double totalPeriodHours = range.days() * 24.0;
        double unplannedDowntimeRate = totalPeriodHours > 0
                ? round(Math.min(100, unplannedDowntimeHours / totalPeriodHours * 100))
                : 0;

        // ---- Conduite ----
        double riskPer1000 = kmMax > 0 ? round(riskEvents.size() * 1000.0 / kmMax) : 0;
        double idleMinutes = events.stream()
                .filter(e -> e.getType() == DrivingEvent.EventType.RALENTI)
                .mapToDouble(e -> e.getDurationSec() == null ? 0 : e.getDurationSec() / 60.0)
                .sum();
        double idleShare = drivingHours > 0 ? round(idleMinutes / (drivingHours * 60) * 100) : 0;
        double driveTimeShare = availableHours > 0 ? round(drivingHours / availableHours * 100) : 0;

        long onTimeTrips = trips.stream().filter(Trip::isOnTime).count();
        double onTimeRate = !trips.isEmpty() ? round(onTimeTrips * 100.0 / trips.size()) : 100;

        double drivingCompliance = !tachoDays.isEmpty()
                ? round(tachoDays.stream().filter(TachographDay::isCompliant).count() * 100.0 / tachoDays.size())
                : 100;

        double ecoScore = computeEcoScore(t, kmTruck, fuels, riskEvents.size());

        // ---- Camion ----
        double consumptionPer100 = computeConsumption(t, kmTruck, fuels);
        double expected = t.getExpectedConsumptionL100Km() != null ? t.getExpectedConsumptionL100Km() : 32;
        double consumptionDeltaPct = consumptionPer100 > 0
                ? round((consumptionPer100 - expected) / expected * 100)
                : 0;

        double truckUptimeRate = round(Math.max(0, 100 - unplannedDowntimeRate));

        // ---- Couple ----
        double loadedKm = trips.stream().filter(Trip::isLoaded).mapToDouble(Trip::getDistanceKm).sum();
        double loadedRunRate = kmMax > 0 ? round(loadedKm / kmMax * 100) : 0;

        double performanceScore = round(Math.max(0, Math.min(100,
                ecoScore * 0.30 +
                drivingCompliance * 0.25 +
                onTimeRate * 0.15 +
                maintenanceCompliance * 0.20 +
                loadedRunRate * 0.10)));

        List<String> alerts = buildAlerts(d, t, drivingCompliance, unplannedDowntimeRate, maintenanceCompliance, tachoDays);

        return new CoupleKpiDto(
                a.getId(), d.getId(), d.getFirstName() + " " + d.getLastName(),
                d.getLicenseNumber(), d.getPhone(),
                t.getId(), t.getRegistration(), t.getBrand(), t.getModel(),
                t.getTruckType().name(), t.getFuelType().name(), range.days(),
                costPerKm, utilization, maintenanceCompliance, unplannedDowntimeRate,
                riskPer1000, riskEvents.size(), ecoScore, driveTimeShare, idleShare,
                onTimeRate, drivingCompliance,
                consumptionPer100, consumptionDeltaPct, truckUptimeRate, round(unplannedDowntimeHours),
                round(kmMax), round(drivingHours), round(totalCost), loadedRunRate,
                performanceScore, alerts);
    }

    private double computeEcoScore(Truck t, double km, List<FuelRecord> fuels, int riskCount) {
        double consumption = computeConsumption(t, km, fuels);
        double expected = t.getExpectedConsumptionL100Km() != null ? t.getExpectedConsumptionL100Km() : 32;
        double score = 100.0;
        if (expected > 0) {
            score -= Math.max(0, (consumption - expected) / expected) * 100.0;
        }
        score -= riskCount * 1.5;
        return round(Math.max(0, Math.min(100, score)));
    }

    private double computeConsumption(Truck t, double km, List<FuelRecord> fuels) {
        double liters = fuels.stream().mapToDouble(FuelRecord::getLiters).sum();
        double expected = t.getExpectedConsumptionL100Km() != null ? t.getExpectedConsumptionL100Km() : 32;
        if (km > 0) {
            return round(liters / km * 100);
        }
        return expected;
    }

    private double fallbackMaintenanceCompliance(Truck t) {
        List<MaintenanceRecord> all = maintenanceRepository.findByTruckAndScheduledDateAfter(t, LocalDate.now().minusYears(1));
        List<MaintenanceRecord> planned = all.stream().filter(MaintenanceRecord::isPlanned).toList();
        if (planned.isEmpty()) return 100;
        return round(planned.stream().filter(MaintenanceRecord::isDoneOnTime).count() * 100.0 / planned.size());
    }

    private List<String> buildAlerts(Driver d, Truck t, double drivingCompliance,
                                     double downtimeRate, double maintenanceCompliance,
                                     List<TachographDay> tachoDays) {
        List<String> alerts = new ArrayList<>();
        if (drivingCompliance < 90) {
            alerts.add("Temps de conduite : non-conformités détectées (" + Math.round(drivingCompliance) + "% de conformité)");
        }
        long overdue = tachoDays.stream().filter(day -> !day.isCompliant()).count();
        if (overdue > 0) {
            alerts.add(overdue + " jour(s) de conduite non conforme");
        }
        if (downtimeRate > 10) {
            alerts.add("Immobilisation imprévue élevée (" + Math.round(downtimeRate) + "% du temps)");
        }
        if (maintenanceCompliance < 80) {
            alerts.add("Maintenance en retard (conformité " + Math.round(maintenanceCompliance) + "%)");
        }
        if (t.getCurrentStatus() == Truck.VehicleStatus.ALERTE) {
            alerts.add("Véhicule en état d'alerte");
        }
        return alerts;
    }

    private List<CoupleDetailDto.DailyPointDto> dailyTrend(DriverTruckAssignment a, PeriodRange range) {
        List<Trip> trips = tripRepository.findByDriverAndStartTimeBetween(a.getDriver(), range.from(), range.to());
        List<DrivingEvent> events = eventRepository.findByDriverAndTimestampBetween(a.getDriver(), range.from(), range.to());
        List<CostRecord> costs = costRepository.findByTruckAndDriver(a.getTruck(), a.getDriver());
        List<FuelRecord> fuels = fuelRepository.findByTruckAndDateBetween(a.getTruck(), range.fromDate(), range.toDate());

        List<CoupleDetailDto.DailyPointDto> result = new ArrayList<>();
        LocalDate day = range.fromDate();
        while (!day.isAfter(range.toDate())) {
            LocalDate d = day;
            double km = trips.stream().filter(t -> t.getStartTime().toLocalDate().equals(d))
                    .mapToDouble(Trip::getDistanceKm).sum();
            int ev = (int) events.stream().filter(e -> e.getTimestamp().toLocalDate().equals(d))
                    .filter(e -> e.getType() != DrivingEvent.EventType.RALENTI).count();
            double cost = costs.stream().filter(c -> c.getBillingMonth().atDay(1).getMonth() == d.getMonth()
                            && c.getBillingMonth().atDay(1).getYear() == d.getYear())
                    .mapToDouble(CostRecord::getAmount).sum() / Math.max(1, (double) d.lengthOfMonth());
            double liters = fuels.stream().filter(f -> f.getDate().equals(d))
                    .mapToDouble(FuelRecord::getLiters).sum();
            result.add(new CoupleDetailDto.DailyPointDto(d.toString(), round(km), round(cost), ev, round(liters)));
            day = day.plusDays(1);
        }
        return result;
    }

    private List<CoupleDetailDto.EventBreakdownDto> eventBreakdown(DriverTruckAssignment a, PeriodRange range) {
        List<DrivingEvent> events = eventRepository.findByDriverAndTimestampBetween(a.getDriver(), range.from(), range.to());
        return Arrays.stream(DrivingEvent.EventType.values())
                .map(type -> new CoupleDetailDto.EventBreakdownDto(
                        type.name(), events.stream().filter(e -> e.getType() == type).count()))
                .toList();
    }

    private List<CoupleDetailDto.CostBreakdownDto> costBreakdown(DriverTruckAssignment a, PeriodRange range) {
        List<CostRecord> costs = costRepository.findByTruckAndDriver(a.getTruck(), a.getDriver()).stream()
                .filter(c -> !c.getBillingMonth().atDay(1).isBefore(range.fromDate().withDayOfMonth(1)))
                .toList();
        Map<String, Double> byCategory = costs.stream().collect(Collectors.groupingBy(
                c -> c.getCategory().name(), Collectors.summingDouble(CostRecord::getAmount)));
        return byCategory.entrySet().stream()
                .map(e -> new CoupleDetailDto.CostBreakdownDto(e.getKey(), round(e.getValue())))
                .sorted(Comparator.comparingDouble(CoupleDetailDto.CostBreakdownDto::amount).reversed())
                .toList();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
