package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résumé d'une expédition fleet-hub, limité aux champs dont
 * {@code CustomsInvoiceGeneratorService.generateInvoice()} a besoin. Mappé
 * directement depuis la réponse JSON de {@code GET /api/shipments/{id}} de
 * fleet-hub (en-tête) et de {@code GET /api/shipments/{id}/items} (lignes) --
 * les noms de champs correspondent exactement à ceux de l'entité ShipmentOrder/
 * ShipmentItem de fleet-hub, {@code @JsonIgnoreProperties} ignore le reste.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentSummary {

    private String shipperName;
    private String shipperAddress;
    private String shipperCity;
    private String shipperCountry;
    private String shipperPostalCode;

    private String consigneeName;
    private String consigneeAddress;
    private String consigneeCity;
    private String consigneeCountry;
    private String consigneePostalCode;

    private String goodsDescription;
    private String currency;
    private Double goodsValue;
    private Double weightKg;
    private Integer packagesCount;
    private String incotermCode;

    @Builder.Default
    private List<Item> items = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String sku;
        private String hsCode;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitPrice;
    }
}
