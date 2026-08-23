package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarrierPerformanceDTO {
    private String carrierName;
    private String carrierCode;
    private int totalShipments;
    private int delivered;
    private int cancelled;
    private double onTimeRate;
    private double avgCost;
    private double totalCost;
}
