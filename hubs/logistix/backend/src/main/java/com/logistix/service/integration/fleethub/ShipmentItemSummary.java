package com.logistix.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ligne article d'une expédition fleet-hub, limitée aux champs dont
 * {@code ReceivingService.createOrder()} a besoin pour dériver des lignes de
 * réception. Mappée directement depuis la réponse JSON de
 * {@code GET /api/shipments/{id}/items} de fleet-hub -- les noms de champs
 * correspondent exactement à ceux de l'entité ShipmentItem de fleet-hub,
 * {@code @JsonIgnoreProperties} ignore le reste.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentItemSummary {

    private String sku;
    private String name;
    private String hsCode;
    private String originCountry;
    private BigDecimal quantity;
    private String unit;
}
