package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopRouteDTO {

    private String origin;
    private String destination;
    private long count;
    private double totalCost;
}
