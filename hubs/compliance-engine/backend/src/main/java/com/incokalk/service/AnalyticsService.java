package com.incokalk.service;

import com.incokalk.dto.analytics.ChartDataDTO;
import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.dto.financial.CostByCarrierDTO;
import com.incokalk.dto.financial.CostByModeDTO;
import com.incokalk.dto.financial.CostTrendDTO;
import com.incokalk.dto.financial.IncotermUsageDTO;
import com.incokalk.dto.shipment.CarrierPerformanceDTO;
import com.incokalk.dto.shipment.ShipmentByStatusDTO;
import com.incokalk.dto.shipment.ShipmentsOverTimeDTO;
import com.incokalk.dto.shipment.TopRouteDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ShipmentOrderRepository shipmentRepo;
    private final CarrierRepository carrierRepo;
    private final SimulationRepository simulationRepo;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(UUID companyId, String period) {
        LocalDateTime fromDate = computeFromDate(period);

        List<ShipmentOrder> shipments;
        if (fromDate == null) {
            shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);
        } else {
            shipments = shipmentRepo.findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(companyId, fromDate);
        }

        long totalShipments = shipments.size();
        long activeShipments = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentOrder.Status.IN_TRANSIT).count();
        long deliveredShipments = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentOrder.Status.DELIVERED).count();
        long draftShipments = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentOrder.Status.DRAFT).count();
        long cancelledShipments = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentOrder.Status.CANCELLED).count();

        DoubleSummaryStatistics costStats = shipments.stream()
                .map(s -> s.getFinalCost() != null ? s.getFinalCost() : s.getQuotedCost())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        DoubleSummaryStatistics weightStats = shipments.stream()
                .map(ShipmentOrder::getWeightKg)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        DoubleSummaryStatistics volumeStats = shipments.stream()
                .map(ShipmentOrder::getVolumeM3)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        double totalGoodsValue = shipments.stream()
                .map(ShipmentOrder::getGoodsValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double totalCo2Kg = computeTotalCo2(companyId, shipments);

        long totalCarriers = carrierRepo.countByCompanyId(companyId);
        long activeCarriers = carrierRepo.countByCompanyIdAndIsActiveTrue(companyId);
        long totalSimulations = simulationRepo.countByCompanyId(companyId);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long simulationsThisMonth = simulationRepo.countByCompanyIdAndCreatedAtAfter(companyId, monthStart);

        long costCount = costStats.getCount();
        long weightCount = weightStats.getCount();
        long volumeCount = volumeStats.getCount();

        return DashboardStatsDTO.builder()
                .totalShipments(totalShipments)
                .activeShipments(activeShipments)
                .deliveredShipments(deliveredShipments)
                .draftShipments(draftShipments)
                .cancelledShipments(cancelledShipments)
                .totalShippingCost(costCount > 0 ? costStats.getSum() : 0)
                .averageShippingCost(costCount > 0 ? costStats.getAverage() : 0)
                .maxShippingCost(costCount > 0 ? costStats.getMax() : 0)
                .minShippingCost(costCount > 0 ? costStats.getMin() : 0)
                .totalWeightKg(weightCount > 0 ? weightStats.getSum() : 0)
                .totalVolumeM3(volumeCount > 0 ? volumeStats.getSum() : 0)
                .averageWeightKg(weightCount > 0 ? weightStats.getAverage() : 0)
                .averageVolumeM3(volumeCount > 0 ? volumeStats.getAverage() : 0)
                .totalGoodsValue(totalGoodsValue)
                .totalSimulations(totalSimulations)
                .simulationsThisMonth(simulationsThisMonth)
                .totalCarriers(totalCarriers)
                .activeCarriers(activeCarriers)
                .totalCo2Kg(totalCo2Kg)
                .averageCo2PerShipment(totalShipments > 0 ? totalCo2Kg / totalShipments : 0)
                .period(period)
                .build();
    }

    public List<ShipmentsOverTimeDTO> getShipmentsOverTime(UUID companyId, String period, String granularity) {
        LocalDateTime fromDate = computeFromDate(period);

        List<ShipmentOrder> shipments;
        if (fromDate == null) {
            shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);
        } else {
            shipments = shipmentRepo.findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(companyId, fromDate);
        }

        Map<String, List<ShipmentOrder>> grouped = shipments.stream()
                .filter(s -> s.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        s -> computeTimeKey(s.getCreatedAt(), granularity),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ShipmentOrder> group = entry.getValue();
                    double totalCost = resolveCostSum(group);
                    return ShipmentsOverTimeDTO.builder()
                            .date(entry.getKey())
                            .count(group.size())
                            .totalCost(totalCost)
                            .build();
                })
                .sorted(Comparator.comparing(ShipmentsOverTimeDTO::getDate))
                .toList();
    }

    public List<ShipmentByStatusDTO> getShipmentsByStatus(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);
        long total = shipments.size();

        Map<ShipmentOrder.Status, Long> statusCounts = shipments.stream()
                .collect(Collectors.groupingBy(ShipmentOrder::getStatus, Collectors.counting()));

        return Arrays.stream(ShipmentOrder.Status.values())
                .map(status -> {
                    long count = statusCounts.getOrDefault(status, 0L);
                    double percentage = total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0;
                    return ShipmentByStatusDTO.builder()
                            .status(status.name())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CostByCarrierDTO> getCostByCarrier(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        List<Carrier> carriers = carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<UUID, Carrier> carrierMap = carriers.stream()
                .collect(Collectors.toMap(Carrier::getId, c -> c, (a, b) -> b));

        Map<UUID, List<ShipmentOrder>> byCarrier = shipments.stream()
                .filter(s -> s.getCarrier() != null)
                .collect(Collectors.groupingBy(s -> s.getCarrier().getId()));

        return byCarrier.entrySet().stream()
                .map(entry -> {
                    Carrier carrier = carrierMap.get(entry.getKey());
                    List<ShipmentOrder> group = entry.getValue();
                    double totalCost = resolveCostSum(group);
                    long count = group.size();
                    return CostByCarrierDTO.builder()
                            .carrierId(entry.getKey())
                            .carrierName(carrier != null ? carrier.getName() : "Unknown")
                            .totalCost(totalCost)
                            .shipmentCount(count)
                            .averageCost(count > 0 ? totalCost / count : 0)
                            .build();
                })
                .sorted(Comparator.comparingDouble(CostByCarrierDTO::getTotalCost).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CostByModeDTO> getCostByMode(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, List<ShipmentOrder>> byMode = shipments.stream()
                .collect(Collectors.groupingBy(this::resolveTransportMode, LinkedHashMap::new, Collectors.toList()));

        return byMode.entrySet().stream()
                .map(entry -> {
                    List<ShipmentOrder> group = entry.getValue();
                    double totalCost = resolveCostSum(group);
                    long count = group.size();
                    return CostByModeDTO.builder()
                            .mode(entry.getKey())
                            .totalCost(totalCost)
                            .count(count)
                            .averageCost(count > 0 ? totalCost / count : 0)
                            .build();
                })
                .sorted(Comparator.comparingDouble(CostByModeDTO::getTotalCost).reversed())
                .toList();
    }

    public List<TopRouteDTO> getTopRoutes(UUID companyId, int limit) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, List<ShipmentOrder>> byRoute = shipments.stream()
                .filter(s -> s.getShipperCountry() != null && s.getConsigneeCountry() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getShipperCountry() + "-" + s.getConsigneeCountry(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return byRoute.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("-");
                    List<ShipmentOrder> group = entry.getValue();
                    double totalCost = resolveCostSum(group);
                    return TopRouteDTO.builder()
                            .origin(parts[0])
                            .destination(parts[1])
                            .count(group.size())
                            .totalCost(totalCost)
                            .build();
                })
                .sorted(Comparator.comparingLong(TopRouteDTO::getCount).reversed())
                .limit(limit)
                .toList();
    }

    public List<IncotermUsageDTO> getIncotermUsage(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);
        long total = shipments.size();

        Map<String, Long> codeCounts = shipments.stream()
                .filter(s -> s.getIncotermCode() != null)
                .collect(Collectors.groupingBy(ShipmentOrder::getIncotermCode, Collectors.counting()));

        return codeCounts.entrySet().stream()
                .map(entry -> {
                    double percentage = total > 0 ? Math.round(entry.getValue() * 10000.0 / total) / 100.0 : 0;
                    return IncotermUsageDTO.builder()
                            .code(entry.getKey())
                            .count(entry.getValue())
                            .percentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparingLong(IncotermUsageDTO::getCount).reversed())
                .toList();
    }

    public ChartDataDTO getWeightDistribution(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        double[] buckets = new double[5];
        String[] labels = {"0-10kg", "10-50kg", "50-100kg", "100-500kg", "500+kg"};

        for (ShipmentOrder s : shipments) {
            if (s.getWeightKg() == null) continue;
            double w = s.getWeightKg();
            if (w <= 10) buckets[0]++;
            else if (w <= 50) buckets[1]++;
            else if (w <= 100) buckets[2]++;
            else if (w <= 500) buckets[3]++;
            else buckets[4]++;
        }

        List<Double> values = Arrays.stream(buckets).boxed().toList();

        return ChartDataDTO.builder()
                .labels(Arrays.asList(labels))
                .values(values)
                .title("Weight Distribution")
                .unit("kg")
                .build();
    }

    public ChartDataDTO getVolumeDistribution(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        double[] buckets = new double[5];
        String[] labels = {"0-0.5m3", "0.5-1m3", "1-5m3", "5-10m3", "10+m3"};

        for (ShipmentOrder s : shipments) {
            if (s.getVolumeM3() == null) continue;
            double v = s.getVolumeM3();
            if (v <= 0.5) buckets[0]++;
            else if (v <= 1) buckets[1]++;
            else if (v <= 5) buckets[2]++;
            else if (v <= 10) buckets[3]++;
            else buckets[4]++;
        }

        List<Double> values = Arrays.stream(buckets).boxed().toList();

        return ChartDataDTO.builder()
                .labels(Arrays.asList(labels))
                .values(values)
                .title("Volume Distribution")
                .unit("m3")
                .build();
    }

    private LocalDateTime computeFromDate(String period) {
        return switch (period) {
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "90d" -> LocalDateTime.now().minusDays(90);
            case "1y" -> LocalDateTime.now().minusYears(1);
            default -> null;
        };
    }

    private String computeTimeKey(LocalDateTime dateTime, String granularity) {
        LocalDate date = dateTime.toLocalDate();
        return switch (granularity) {
            case "week" -> {
                int year = date.get(IsoFields.WEEK_BASED_YEAR);
                int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield String.format("%d-W%02d", year, week);
            }
            case "month" -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
            default -> date.toString();
        };
    }

    private double resolveCostSum(List<ShipmentOrder> shipments) {
        return shipments.stream()
                .map(s -> s.getFinalCost() != null ? s.getFinalCost() : s.getQuotedCost())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private String resolveTransportMode(ShipmentOrder shipment) {
        if (shipment.getShippingRate() != null && shipment.getShippingRate().getTransportMode() != null) {
            return shipment.getShippingRate().getTransportMode();
        }
        if (shipment.getCarrier() != null && shipment.getCarrier().getTransportModes() != null) {
            return shipment.getCarrier().getTransportModes();
        }
        return "UNKNOWN";
    }

    private double computeTotalCo2(UUID companyId, List<ShipmentOrder> shipments) {
        return shipments.stream()
                .filter(s -> s.getShippingRate() != null)
                .map(s -> s.getShippingRate().getCo2EstimateKg())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    // ── Cost Trends ───────────────────────────────────────────────────────

    public List<CostTrendDTO> getCostTrends(UUID companyId, String period, String granularity) {
        LocalDateTime fromDate = computeFromDate(period);
        List<ShipmentOrder> shipments = fromDate != null
            ? shipmentRepo.findAnalyticsByCompanyIdAndCreatedAtAfterOrderByCreatedAtDesc(companyId, fromDate)
            : shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, List<ShipmentOrder>> grouped = shipments.stream()
            .collect(Collectors.groupingBy(s -> computeTimeKey(s.getCreatedAt(), granularity)));

        return grouped.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                List<ShipmentOrder> group = entry.getValue();
                double totalCost = resolveCostSum(group);
                double avgCost = group.isEmpty() ? 0 : totalCost / group.size();
                return new CostTrendDTO(entry.getKey(), totalCost, avgCost, group.size());
            })
            .toList();
    }

    // ── Carrier Performance ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CarrierPerformanceDTO> getCarrierPerformance(UUID companyId) {
        List<ShipmentOrder> shipments = shipmentRepo.findAnalyticsByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, List<ShipmentOrder>> byCarrier = shipments.stream()
            .filter(s -> s.getCarrier() != null)
            .collect(Collectors.groupingBy(s -> s.getCarrier().getName()));

        return byCarrier.entrySet().stream()
            .map(entry -> {
                List<ShipmentOrder> group = entry.getValue();
                int total = group.size();
                int delivered = (int) group.stream().filter(s -> s.getStatus() == ShipmentOrder.Status.DELIVERED).count();
                int cancelled = (int) group.stream().filter(s -> s.getStatus() == ShipmentOrder.Status.CANCELLED).count();
                double totalCost = resolveCostSum(group);
                double avgCost = total > 0 ? totalCost / total : 0;
                double onTimeRate = total > 0 ? (double) delivered / total * 100 : 0;

                Carrier carrier = group.get(0).getCarrier();
                return new CarrierPerformanceDTO(
                    carrier.getName(),
                    carrier.getCode(),
                    total, delivered, cancelled,
                    Math.round(onTimeRate * 10.0) / 10.0,
                    Math.round(avgCost * 100.0) / 100.0,
                    Math.round(totalCost * 100.0) / 100.0
                );
            })
            .sorted((a, b) -> Integer.compare(b.getTotalShipments(), a.getTotalShipments()))
            .toList();
    }
}
