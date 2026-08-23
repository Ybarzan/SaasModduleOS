package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TruckingRateResult {

    private String originCountry;
    private String destinationCountry;
    private int estimatedPallets;
    private double totalWeightKg;
    private double totalVolumeM3;

    private List<TruckOption> options;
    private TruckOption recommended;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TruckOption {
        private String mode;
        private String label;
        private double costEur;
        private int transitDays;
        private double co2Kg;
        private String description;
        private double costPerPallet;
        private boolean recommended;
    }
}
