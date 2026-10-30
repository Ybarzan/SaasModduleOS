package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Article d'une expédition fleet-hub pour l'app mobile (restaure
 * {@code GET /v1/shipments/{id}/items}) -- plus riche que {@link ShipmentSummary.Item}
 * (id/name/description/originCountry en plus, nécessaires à
 * {@code mobile/src/types/index.ts#ShipmentItemDTO}).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentItemDetailDto {
    private UUID id;
    private String sku;
    private String name;
    private String description;
    private String hsCode;
    private String originCountry;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
}
