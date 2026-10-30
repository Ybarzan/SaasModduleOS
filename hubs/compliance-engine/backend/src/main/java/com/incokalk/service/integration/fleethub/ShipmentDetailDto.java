package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Détail complet d'une expédition fleet-hub pour l'app mobile (restaure
 * {@code GET /v1/shipments/{id}}, retiré avec le domaine Transport -- voir
 * {@code ShipmentDetailController}/{@code mobile/src/types/index.ts#ShipmentDetail}).
 * Mappé directement depuis la réponse JSON de {@code GET /api/internal/shipments/{id}}
 * de fleet-hub (qui sérialise l'entité ShipmentOrder complète, carrier/truck/shippingRate
 * déjà aplatis côté fleet-hub en carrierName/transportMode -- voir ShipmentOrder) --
 * {@code @JsonIgnoreProperties} ignore le reste. Plus riche que {@link ShipmentSummary}
 * (utilisé par CustomsInvoiceGeneratorService/LandedCostService, besoins différents).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentDetailDto {

    private UUID id;
    private String orderNumber;
    private String status;

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
    private Double goodsValue;
    private String currency;
    private Double weightKg;
    private Double volumeM3;
    private Integer packagesCount;
    private String hsCode;
    private String incotermCode;
    private boolean dangerous;
    private String countryOfOrigin;
    private String containerType;

    private String carrierName;
    private String transportMode;

    private String customsStatus;
    private String customsDeclarationNumber;
    private Double dutyAmount;
    private Double vatAmount;
    private Double landedCost;
    private Double quotedCost;
    private Double finalCost;
    private String costCurrency;

    private LocalDateTime requestedPickupDate;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private LocalDateTime bookedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime createdAt;

    private List<TrackingEventDto> trackingEvents;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackingEventDto {
        private UUID id;
        private String status;
        private String location;
        private String description;
        private LocalDateTime eventTime;
        private String source;
    }
}
