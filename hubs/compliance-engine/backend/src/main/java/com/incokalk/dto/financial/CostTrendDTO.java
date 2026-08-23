package com.incokalk.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostTrendDTO {
    private String period;
    private double totalCost;
    private double avgCost;
    private int shipmentCount;
}
