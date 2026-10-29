package com.incokalk.service.integration.fleethub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contrat M2M dédié (ADR-0002, docs/adr/0002-creation-expedition-externe.md)
 * pour la création d'une expédition brouillon sur fleet-hub -- miroir exact
 * de {@code ExternalShipmentCreationRequest} côté fleet-hub
 * (dto/ExternalShipmentCreationRequest.java). shipperName/consigneeName
 * obligatoires côté fleet-hub ; {@code ECommerceAdapter.mapOrderToShipment}
 * ne doit jamais construire cette classe sans eux (voir Optional vide =
 * commande non mappable plutôt que champs devinés).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalShipmentCreationRequest {

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
}
