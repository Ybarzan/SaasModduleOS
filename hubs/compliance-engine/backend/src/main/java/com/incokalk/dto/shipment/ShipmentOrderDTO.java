package com.incokalk.dto.shipment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderDTO {

    private UUID carrierId;
    /** Immatriculation du camion fleet-hub assigné, si livrée par la flotte propre
     * plutôt qu'un transporteur tiers (carrierId). Voir docs/07-integration-fleet-hub.md. */
    private String fleetHubTruckRegistration;
    private UUID shippingRateId;

    // Compte client du portail à qui rattacher cette expédition (optionnel).
    private UUID clientId;

    // Shipper
    private String shipperName;
    private String shipperAddress;
    private String shipperCity;
    private String shipperCountry;
    private String shipperPostalCode;

    // Consignee
    private String consigneeName;
    private String consigneeAddress;
    private String consigneeCity;
    private String consigneeCountry;
    private String consigneePostalCode;

    // Cargo
    private String goodsDescription;
    @DecimalMin(value = "0.0", message = "La valeur des marchandises ne peut pas être négative")
    private Double goodsValue;
    private String currency;
    @DecimalMin(value = "0.0", message = "Le poids ne peut pas être négatif")
    private Double weightKg;
    @DecimalMin(value = "0.0", message = "Le volume ne peut pas être négatif")
    private Double volumeM3;
    @PositiveOrZero(message = "Le nombre de colis ne peut pas être négatif")
    private Integer packagesCount;
    private String hsCode;
    private String incotermCode;
    private Boolean isDangerous;

    // Items (lignes articles de l'expédition)
    private List<ShipmentOrderItemDTO> items;

    // Cost
    private Double quotedCost;

    // Dates
    private LocalDateTime requestedPickupDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentOrderItemDTO {
        private UUID itemId;
        private String sku;
        private String name;
        private String description;
        private String hsCode;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitPrice;
    }
}
