package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentsOverTimeDTO {

    private String date;
    private long count;
    private double totalCost;
}
