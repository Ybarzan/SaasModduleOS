package com.fleethub.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contrat M2M dédié (ADR-0002, docs/adr/0002-creation-expedition-externe.md)
 * pour la création d'une expédition brouillon par un hub externe
 * (compliance-engine : synchronisation e-commerce, accueil email). Volontairement
 * plus restreint que {@link ShipmentOrderDTO} (utilisé par l'UI fleet-hub) :
 * aucun champ de clé étrangère interne (carrierId/truckId/shippingRateId/clientId)
 * -- un appelant externe n'a aucun moyen de connaître ces identifiants de façon
 * fiable, les exposer inviterait à deviner plutôt qu'à laisser un humain les
 * compléter depuis l'UI fleet-hub normale une fois le brouillon créé.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalShipmentCreationRequest {

    @NotBlank(message = "Le nom de l'expéditeur est obligatoire")
    private String shipperName;
    private String shipperAddress;
    private String shipperCity;
    private String shipperCountry;
    private String shipperPostalCode;

    @NotBlank(message = "Le nom du destinataire est obligatoire")
    private String consigneeName;
    private String consigneeAddress;
    private String consigneeCity;
    private String consigneeCountry;
    private String consigneePostalCode;

    private String goodsDescription;
    @DecimalMin(value = "0.0", message = "La valeur des marchandises ne peut pas être négative")
    private Double goodsValue;
    private String currency;
    @DecimalMin(value = "0.0", message = "Le poids ne peut pas être négatif")
    private Double weightKg;
    @DecimalMin(value = "0.0", message = "Le volume ne peut pas être négatif")
    private Double volumeM3;
    private Integer packagesCount;
}
