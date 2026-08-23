package com.incokalk.dto.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostByModeDTO {

    private String mode;
    private double totalCost;
    private long count;
    private double averageCost;
}
