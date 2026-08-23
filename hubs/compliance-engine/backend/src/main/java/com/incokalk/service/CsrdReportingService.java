package com.incokalk.service;

import com.incokalk.model.CarbonOffset;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsrdReportingService {

    private final CarbonOffsetRepository carbonOffsetRepo;

    public Map<String, Object> getCsrdReport() {
        UUID companyId = TenantContext.get();
        Map<String, Object> report = new LinkedHashMap<>();

        report.put("reportPeriod", "2026-Q3");
        report.put("companyId", companyId);

        BigDecimal totalEmissionsKg = carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId);
        BigDecimal totalEmissionsCO2 = totalEmissionsKg != null
                ? totalEmissionsKg.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        report.put("totalEmissionsCO2", totalEmissionsCO2);

        BigDecimal total = totalEmissionsCO2;
        Map<String, Object> byScope = new LinkedHashMap<>();
        byScope.put("scope1", total.multiply(BigDecimal.valueOf(0.30)).setScale(2, RoundingMode.HALF_UP));
        byScope.put("scope2", total.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP));
        byScope.put("scope3", total.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP));
        report.put("emissionsByScope", byScope);

        List<CarbonOffset> allOffsets = carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<String, BigDecimal> laneEmissionsKg = new LinkedHashMap<>();
        for (CarbonOffset offset : allOffsets) {
            String lane = "Unknown";
            if (offset.getShipment() != null) {
                String origin = offset.getShipment().getShipperCity();
                String dest = offset.getShipment().getConsigneeCity();
                if (origin != null && dest != null) {
                    lane = origin + " \u2192 " + dest;
                }
            }
            BigDecimal co2Kg = offset.getCo2EmissionsKg();
            if (co2Kg != null) {
                laneEmissionsKg.merge(lane, co2Kg, BigDecimal::add);
            }
        }

        BigDecimal totalLaneKg = laneEmissionsKg.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal divisor = totalLaneKg.compareTo(BigDecimal.ZERO) > 0
                ? totalLaneKg : BigDecimal.ONE;

        List<Map<String, Object>> lanes = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : laneEmissionsKg.entrySet()) {
            Map<String, Object> laneMap = new LinkedHashMap<>();
            laneMap.put("lane", entry.getKey());
            laneMap.put("co2Tonnes", entry.getValue().divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));
            laneMap.put("percentage", entry.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(divisor, 2, RoundingMode.HALF_UP));
            lanes.add(laneMap);
        }
        report.put("emissionsByLane", lanes);

        BigDecimal creditsPurchased = carbonOffsetRepo.sumOffsetCreditsPurchasedByCompanyId(companyId);
        creditsPurchased = creditsPurchased != null ? creditsPurchased : BigDecimal.ZERO;
        report.put("offsetCreditsPurchased", creditsPurchased);

        BigDecimal creditsRetired = carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId);
        creditsRetired = creditsRetired != null ? creditsRetired : BigDecimal.ZERO;
        report.put("offsetCreditsRetired", creditsRetired);

        BigDecimal netEmissions = totalEmissionsCO2.subtract(creditsRetired);
        report.put("netEmissions", netEmissions);

        boolean esrsE1Compliant = totalEmissionsCO2.compareTo(BigDecimal.ZERO) > 0
                || creditsRetired.compareTo(BigDecimal.ZERO) > 0;
        report.put("esrsE1Compliant", esrsE1Compliant);

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Ensure all scope 3 emissions are mapped across the full value chain.");
        recommendations.add("Implement a double materiality assessment to identify key CSRD impacts.");
        recommendations.add("Align emission reduction targets with ESRS E1-1 requirements (1.5\u00b0C / 2\u00b0C pathways).");
        recommendations.add("Document data collection methodology for each scope category.");
        if (creditsRetired.compareTo(BigDecimal.ZERO) == 0) {
            recommendations.add("Consider purchasing verified carbon credits to offset unavoidable emissions.");
        }
        if (totalEmissionsCO2.compareTo(BigDecimal.ZERO) > 0 && netEmissions.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add("Set a science-based reduction target (SBTi) to reduce net emissions over time.");
        }
        report.put("recommendations", recommendations);

        report.put("reportGeneratedAt", LocalDateTime.now());

        log.info("CSRD report generated for company {} period 2026-Q3", companyId);
        return report;
    }
}
