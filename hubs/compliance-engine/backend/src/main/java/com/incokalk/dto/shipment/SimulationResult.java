package com.incokalk.dto.shipment;

import com.incokalk.dto.compliance.ComplianceAlert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    private String incoterm;
    private String incotermFullName;
    private int buyerRiskScore;
    private String riskLevel;
    private int estimatedDays;

    private CostBreakdown buyerCosts;
    private CostBreakdown sellerCosts;
    private double totalBuyerCost;
    private double totalSellerCost;

    private ResponsibilityMatrix responsibilities;

    private List<String> recommendations;
    private List<String> warnings;
    private List<String> buyerRisks;
    private List<ComplianceAlert> complianceAlerts;

    private List<IncotermComparison> comparison;

    private LogisticsInfo logistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogisticsInfo {
        private int totalBoxes;
        private double totalVolumeM3;
        private double totalWeightKg;
        private double utilizationPercent;
        private String recommendedMode;
        private String modeReason;
        private double totalPackageVolumeM3;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostBreakdown {
        private double goodsValue;
        private double exportCustoms;
        private double originHandling;
        private double originDocumentation;
        private double freight;
        private double insurance;
        private double destinationHandling;
        private double destinationDocumentation;
        private double importDuties;
        private double importVat;
        private double lastMileDelivery;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsibilityMatrix {
        private boolean sellerExportClearance;
        private boolean sellerOriginCharges;
        private boolean sellerMainFreight;
        private boolean sellerInsurance;
        private boolean sellerDestinationCharges;
        private boolean sellerImportDuties;
        private boolean sellerVat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncotermComparison {
        private String code;
        private String fullName;
        private double totalBuyerCost;
        private int buyerRiskScore;
        private String riskLevel;
        private boolean compatible;
        private int estimatedDays;
    }
}
