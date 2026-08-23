package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponseDTO {

    private UUID rateId;
    private UUID carrierId;
    private String carrierName;
    private String carrierLogo;
    private String rateName;
    private String transportMode;
    private double baseRate;
    private double totalCost;
    private String currency;
    private Integer transitDaysMin;
    private Integer transitDaysMax;
    private Double co2EstimateKg;

    private String providerType;
    private String providerLogo;
    private String providerName;

    private Double totalCostConverted;
    private String displayCurrency;
    private Double conversionRate;
}
