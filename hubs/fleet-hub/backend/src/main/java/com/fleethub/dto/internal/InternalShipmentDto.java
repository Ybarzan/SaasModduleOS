package com.fleethub.dto.internal;

import com.fleethub.model.ShipmentOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Expédition telle qu'exposée aux autres hubs par {@code /api/internal/shipments/**}.
 * <p>
 * Contrat explicite, découplé de l'entité JPA : renommer ou retirer un champ casse
 * compliance-engine / Logistix (voir InternalApiContractTest, qui liste les champs
 * qu'ils lisent). Ajouter un champ est sans risque, les consommateurs ignorent les
 * champs inconnus.
 */
public record InternalShipmentDto(
        UUID id,
        String orderNumber,
        String status,
        String shipperName,
        String shipperAddress,
        String shipperCity,
        String shipperCountry,
        String shipperPostalCode,
        String consigneeName,
        String consigneeAddress,
        String consigneeCity,
        String consigneeCountry,
        String consigneePostalCode,
        String goodsDescription,
        BigDecimal goodsValue,
        String currency,
        Double weightKg,
        Double volumeM3,
        Integer packagesCount,
        String hsCode,
        String incotermCode,
        boolean dangerous,
        String countryOfOrigin,
        String containerType,
        Integer refrigeratedMinCelsius,
        Integer refrigeratedMaxCelsius,
        boolean temperatureMonitored,
        UUID carrierId,
        String carrierName,
        String transportMode,
        Long truckId,
        String truckRegistration,
        String customsStatus,
        String customsDeclarationNumber,
        BigDecimal dutyAmount,
        BigDecimal vatAmount,
        BigDecimal landedCost,
        BigDecimal quotedCost,
        BigDecimal finalCost,
        String costCurrency,
        LocalDateTime requestedPickupDate,
        LocalDateTime estimatedDeliveryDate,
        LocalDateTime actualDeliveryDate,
        LocalDateTime bookedAt,
        LocalDateTime shippedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<InternalTrackingEventDto> trackingEvents
) {

    /** À appeler dans une transaction : lit les relations paresseuses (transporteur, camion, tarif). */
    public static InternalShipmentDto from(ShipmentOrder s, boolean withTracking) {
        List<InternalTrackingEventDto> events = withTracking && s.getTrackingEvents() != null
                ? s.getTrackingEvents().stream().map(InternalTrackingEventDto::from).toList()
                : List.of();
        return new InternalShipmentDto(
                s.getId(), s.getOrderNumber(), s.getStatus() != null ? s.getStatus().name() : null,
                s.getShipperName(), s.getShipperAddress(), s.getShipperCity(), s.getShipperCountry(),
                s.getShipperPostalCode(),
                s.getConsigneeName(), s.getConsigneeAddress(), s.getConsigneeCity(), s.getConsigneeCountry(),
                s.getConsigneePostalCode(),
                s.getGoodsDescription(), s.getGoodsValue(), s.getCurrency(), s.getWeightKg(), s.getVolumeM3(),
                s.getPackagesCount(), s.getHsCode(), s.getIncotermCode(), s.isDangerous(), s.getCountryOfOrigin(),
                s.getContainerType() != null ? s.getContainerType().name() : null,
                s.getRefrigeratedMinCelsius(), s.getRefrigeratedMaxCelsius(), s.isTemperatureMonitored(),
                s.getCarrierId(), s.getCarrierName(), s.getTransportMode(),
                s.getTruckId(), s.getTruckRegistration(),
                s.getCustomsStatus(), s.getCustomsDeclarationNumber(), s.getDutyAmount(), s.getVatAmount(),
                s.getLandedCost(), s.getQuotedCost(), s.getFinalCost(), s.getCostCurrency(),
                s.getRequestedPickupDate(), s.getEstimatedDeliveryDate(), s.getActualDeliveryDate(),
                s.getBookedAt(), s.getShippedAt(), s.getCreatedAt(), s.getUpdatedAt(),
                events);
    }
}
