package com.incokalk.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostByCarrierDTO {

    private UUID carrierId;
    private String carrierName;
    private double totalCost;
    private long shipmentCount;
    private double averageCost;
}
