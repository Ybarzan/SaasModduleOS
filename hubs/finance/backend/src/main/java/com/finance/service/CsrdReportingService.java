package com.finance.service;

import com.finance.model.CarbonOffset;
import com.finance.repository.CarbonOffsetRepository;
import com.finance.security.TenantContext;
import com.finance.service.integration.fleethub.FleetHubIntegrationClient;
import com.finance.service.integration.fleethub.ShipmentLaneSummary;
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
    private final FleetHubIntegrationClient fleetHubIntegrationClient;

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

        // Le regroupement par "lane" (origine -> destination) lisait la ville du
        // shipper/consignee via la relation JPA CarbonOffset.shipment -- retirée avec
        // le domaine Transport (CarbonOffset.shipmentId est maintenant un UUID brut,
        // et l'expédition elle-même vit côté fleet-hub). Résolu depuis 2026-10-29 via
        // FleetHubIntegrationClient.getShipmentSummary (GET /api/internal/shipments/{id}),
        // même principe que compliance-engine/Logistix. Dégradation gracieuse à deux
        // niveaux : (1) société sans clé fleet-hub configurée -> aucun appel, tout sous
        // "Unknown" (comportement préexistant inchangé, pas de coût réseau ajouté pour
        // qui n'a rien activé) ; (2) clé configurée mais une expédition précise
        // indisponible (introuvable, fleet-hub en panne) -> seule cette ligne tombe à
        // "Unknown", jamais tout le rapport. mémoïsée par shipmentId : plusieurs offsets
        // de la même expédition ne déclenchent qu'un seul appel.
        List<CarbonOffset> allOffsets = carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        boolean fleetHubConfigured = fleetHubIntegrationClient.isConfigured(companyId);
        Map<UUID, String> laneByShipmentId = new HashMap<>();
        Map<String, BigDecimal> laneEmissionsKg = new LinkedHashMap<>();
        for (CarbonOffset offset : allOffsets) {
            UUID shipmentId = offset.getShipmentId();
            String lane = (fleetHubConfigured && shipmentId != null)
                    ? laneByShipmentId.computeIfAbsent(shipmentId, id -> resolveLaneOrUnknown(id, companyId))
                    : "Unknown";
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
        recommendations.add("Align emission reduction targets with ESRS E1-1 requirements (1.5°C / 2°C pathways).");
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

    /** Dégradation gracieuse par expédition : une expédition introuvable ou un
     * fleet-hub indisponible ne fait tomber que cette ligne sous "Unknown",
     * jamais tout le rapport -- voir le commentaire dans getCsrdReport(). */
    private String resolveLaneOrUnknown(UUID shipmentId, UUID companyId) {
        try {
            ShipmentLaneSummary summary = fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId);
            String origin = firstNonBlank(summary.getShipperCity(), summary.getShipperCountry());
            String destination = firstNonBlank(summary.getConsigneeCity(), summary.getConsigneeCountry());
            return (origin != null && destination != null) ? origin + " → " + destination : "Unknown";
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            log.debug("[FleetHub] Résumé d'expédition {} indisponible (lane CSRD) : {}", shipmentId, e.getMessage());
            return "Unknown";
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return null;
    }
}
