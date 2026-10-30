package com.logistix.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vue minimale d'une expédition fleet-hub pour l'autocomplétion de recherche
 * (voir Receiving.tsx, ReceivingService.deriveLinesFromShipment). Mappée
 * depuis un élément de la réponse JSON de {@code GET /api/internal/shipments?q=}
 * (voir com.fleethub.controller.InternalShipmentController côté fleet-hub) --
 * seuls les champs utiles à l'affichage d'un résultat de recherche, le reste
 * est ignoré.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentSearchResult {
    private String id;
    private String orderNumber;
    private String status;
    private String consigneeCity;
    private String consigneeCountry;
    private String goodsDescription;
}
