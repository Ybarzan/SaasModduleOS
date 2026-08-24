package com.incokalk.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentCreatedPayload {
    private UUID shipmentId;
    private String orderNumber;
    private UUID companyId;
}
