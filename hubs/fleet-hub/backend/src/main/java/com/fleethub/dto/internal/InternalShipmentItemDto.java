package com.fleethub.dto.internal;

import com.fleethub.model.ShipmentItem;

import java.math.BigDecimal;
import java.util.UUID;

/** Ligne d'expédition exposée aux autres hubs (voir {@link InternalShipmentDto}). */
public record InternalShipmentItemDto(
        UUID id,
        String sku,
        String name,
        String description,
        String hsCode,
        String originCountry,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice
) {
    public static InternalShipmentItemDto from(ShipmentItem i) {
        return new InternalShipmentItemDto(i.getId(), i.getSku(), i.getName(), i.getDescription(), i.getHsCode(),
                i.getOriginCountry(), i.getQuantity(), i.getUnit(), i.getUnitPrice());
    }
}
