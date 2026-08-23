package com.incokalk.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargoInsuranceResult {
    private double goodsValue;
    private double premiumRate;
    private double premiumAmount;
    private double coverageAmount;
    private String coverageType;
    private String transportMode;
    private String note;
}
