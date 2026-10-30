package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vue liste d'une expédition fleet-hub (restaure
 * {@code GET /v1/mobile/recent-shipments}, voir {@code MobileDashboardService.getRecentShipments}).
 * Mappé depuis un élément de la réponse JSON de {@code GET /api/internal/shipments}
 * (liste, jusqu'à 50, voir {@code InternalShipmentController.listRecentShipments} côté
 * fleet-hub) -- contrairement à {@link ShipmentDetailDto}, ne porte pas les coûts
 * détaillés/douane/suivi, pas nécessaires pour une liste.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecentShipmentDto {
    private UUID id;
    private String orderNumber;
    private String status;
    private String shipperCity;
    private String shipperCountry;
    private String consigneeCity;
    private String consigneeCountry;
    private String goodsDescription;
    private Double weightKg;
    private String incotermCode;
    private Double quotedCost;
    private Double finalCost;
    private String currency;
    private String carrierName;
    private String transportMode;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedDeliveryDate;
}
