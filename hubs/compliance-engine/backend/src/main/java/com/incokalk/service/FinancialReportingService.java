package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentFinancials;
import com.incokalk.repository.ShipmentFinancialsRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportingService {

    private final ShipmentFinancialsRepository financialsRepo;
    private final CompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public List<ShipmentFinancials> getAllShipments() {
        UUID companyId = TenantContext.get();
        return financialsRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public ShipmentFinancials getShipmentById(UUID id) {
        UUID companyId = TenantContext.get();
        return financialsRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Données financières non trouvées"));
    }

    @Transactional
    public ShipmentFinancials create(ShipmentFinancials financials) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        financials.setCompany(company);
        calculateFinancials(financials);

        ShipmentFinancials saved = financialsRepo.save(financials);
        log.info("Shipment financials created for shipment: {}", financials.getShipment() != null ? financials.getShipment().getId() : "N/A");
        return saved;
    }

    @Transactional
    public ShipmentFinancials update(UUID id, ShipmentFinancials financials) {
        ShipmentFinancials existing = getShipmentById(id);

        existing.setClientName(financials.getClientName());
        existing.setOrigin(financials.getOrigin());
        existing.setDestination(financials.getDestination());
        existing.setMode(financials.getMode());
        existing.setCarrierName(financials.getCarrierName());
        existing.setRevenue(financials.getRevenue());
        existing.setRevenueCurrency(financials.getRevenueCurrency());
        existing.setCostFreight(financials.getCostFreight());
        existing.setCostFuel(financials.getCostFuel());
        existing.setCostHandling(financials.getCostHandling());
        existing.setCostCustoms(financials.getCostCustoms());
        existing.setCostInsurance(financials.getCostInsurance());
        existing.setCostWarehouse(financials.getCostWarehouse());
        existing.setCostLastMile(financials.getCostLastMile());
        existing.setCostOther(financials.getCostOther());

        calculateFinancials(existing);

        return financialsRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        ShipmentFinancials financials = financialsRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Données financières non trouvées"));
        financialsRepo.delete(financials);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        UUID companyId = TenantContext.get();

        BigDecimal totalRevenue = financialsRepo.sumRevenueByCompanyId(companyId);
        BigDecimal totalCost = financialsRepo.sumCostByCompanyId(companyId);
        BigDecimal totalMargin = financialsRepo.sumMarginByCompanyId(companyId);
        long shipmentCount = financialsRepo.countByCompanyId(companyId);

        BigDecimal marginPercent = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            marginPercent = totalMargin.multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgRevenuePerShipment = BigDecimal.ZERO;
        BigDecimal avgMarginPerShipment = BigDecimal.ZERO;
        if (shipmentCount > 0) {
            avgRevenuePerShipment = totalRevenue.divide(BigDecimal.valueOf(shipmentCount), 2, RoundingMode.HALF_UP);
            avgMarginPerShipment = totalMargin.divide(BigDecimal.valueOf(shipmentCount), 2, RoundingMode.HALF_UP);
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalRevenue", totalRevenue);
        dashboard.put("totalCost", totalCost);
        dashboard.put("totalMargin", totalMargin);
        dashboard.put("marginPercent", marginPercent);
        dashboard.put("shipmentCount", shipmentCount);
        dashboard.put("avgRevenuePerShipment", avgRevenuePerShipment);
        dashboard.put("avgMarginPerShipment", avgMarginPerShipment);

        return dashboard;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProfitByCarrier() {
        UUID companyId = TenantContext.get();
        List<Object[]> results = financialsRepo.profitByCarrier(companyId);

        return results.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("carrier", row[0]);
            map.put("revenue", row[1]);
            map.put("cost", row[2]);
            map.put("margin", row[3]);
            map.put("shipments", row[4]);
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProfitByLane() {
        UUID companyId = TenantContext.get();
        List<Object[]> results = financialsRepo.profitByLane(companyId);

        return results.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("lane", row[0]);
            map.put("revenue", row[1]);
            map.put("cost", row[2]);
            map.put("margin", row[3]);
            map.put("shipments", row[4]);
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFxExposureReport() {
        UUID companyId = TenantContext.get();
        List<ShipmentFinancials> all = financialsRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, Map<String, Object>> currencyExposure = new LinkedHashMap<>();

        for (ShipmentFinancials sf : all) {
            String currency = sf.getRevenueCurrency() != null ? sf.getRevenueCurrency() : "EUR";
            BigDecimal revenue = sf.getRevenue() != null ? sf.getRevenue() : BigDecimal.ZERO;
            BigDecimal cost = sf.getTotalCost() != null ? sf.getTotalCost() : BigDecimal.ZERO;

            currencyExposure.putIfAbsent(currency, new LinkedHashMap<>());
            Map<String, Object> exp = currencyExposure.get(currency);

            exp.put("currency", currency);
            exp.merge("totalRevenue", revenue, (a, b) -> ((BigDecimal) a).add(revenue));
            exp.merge("totalCost", cost, (a, b) -> ((BigDecimal) a).add(cost));
            exp.merge("netExposure", revenue.subtract(cost), (a, b) -> ((BigDecimal) a).add(revenue.subtract(cost)));
            exp.merge("shipmentCount", 1, (a, b) -> ((Integer) a) + 1);
        }

        BigDecimal totalRevenue = currencyExposure.values().stream()
            .map(m -> (BigDecimal) m.get("totalRevenue"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Map<String, Object> exp : currencyExposure.values()) {
            BigDecimal rev = (BigDecimal) exp.get("totalRevenue");
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                exp.put("exposurePct", rev.multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 2, RoundingMode.HALF_UP));
            } else {
                exp.put("exposurePct", BigDecimal.ZERO);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRevenue", totalRevenue);
        report.put("totalCost", currencyExposure.values().stream()
            .map(m -> (BigDecimal) m.get("totalCost"))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        report.put("baseCurrency", "EUR");
        report.put("currencyExposures", currencyExposure.values());
        report.put("reportDate", java.time.LocalDate.now().toString());

        return report;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopLanes(int limit) {
        return getProfitByLane().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCarriers(int limit) {
        return getProfitByCarrier().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void calculateFinancials(ShipmentFinancials financials) {
        BigDecimal freight = financials.getCostFreight() != null ? financials.getCostFreight() : BigDecimal.ZERO;
        BigDecimal fuel = financials.getCostFuel() != null ? financials.getCostFuel() : BigDecimal.ZERO;
        BigDecimal handling = financials.getCostHandling() != null ? financials.getCostHandling() : BigDecimal.ZERO;
        BigDecimal customs = financials.getCostCustoms() != null ? financials.getCostCustoms() : BigDecimal.ZERO;
        BigDecimal insurance = financials.getCostInsurance() != null ? financials.getCostInsurance() : BigDecimal.ZERO;
        BigDecimal warehouse = financials.getCostWarehouse() != null ? financials.getCostWarehouse() : BigDecimal.ZERO;
        BigDecimal lastMile = financials.getCostLastMile() != null ? financials.getCostLastMile() : BigDecimal.ZERO;
        BigDecimal other = financials.getCostOther() != null ? financials.getCostOther() : BigDecimal.ZERO;

        BigDecimal totalCost = freight.add(fuel).add(handling).add(customs)
                .add(insurance).add(warehouse).add(lastMile).add(other);
        financials.setTotalCost(totalCost);

        BigDecimal revenue = financials.getRevenue() != null ? financials.getRevenue() : BigDecimal.ZERO;
        BigDecimal grossMargin = revenue.subtract(totalCost);
        financials.setGrossMargin(grossMargin);

        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal grossMarginPercent = grossMargin.multiply(BigDecimal.valueOf(100))
                    .divide(revenue, 2, RoundingMode.HALF_UP);
            financials.setGrossMarginPercent(grossMarginPercent);
        } else {
            financials.setGrossMarginPercent(BigDecimal.ZERO);
        }
    }
}
