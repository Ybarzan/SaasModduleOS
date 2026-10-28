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
 * {@code CustomsInvoiceGeneratorService.generateInvoice()} et
 * {@code OrchestrationExecutor} ont besoin. Mappé directement depuis la
 * réponse JSON de {@code GET /api/internal/shipments/{id}} de fleet-hub
 * (en-tête -- qui sérialise l'entité ShipmentOrder complète) et de
 * {@code .../items} (lignes) -- les noms de champs correspondent exactement
 * à ceux de ShipmentOrder/ShipmentItem de fleet-hub, {@code @JsonIgnoreProperties}
 * ignore le reste de ce qui est effectivement renvoyé.
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

    /** UUID du transporteur fleet-hub (carrierId dans sa réponse JSON) -- null
     *  si l'expédition est livrée en interne (flotte propre, pas de carrier).
     *  Nécessaire à OrchestrationExecutor pour le contrôle de gouvernance
     *  allowedCarrierIds (voir NotificationRule). */
    private java.util.UUID carrierId;
    private String carrierName;

    /** Coûts de l'expédition -- nécessaires au contrôle de gouvernance
     *  maxBudgetAmount (voir OrchestrationExecutor). */
    private Double quotedCost;
    private Double finalCost;

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
