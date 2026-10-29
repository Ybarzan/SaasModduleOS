package com.finance.service.integration.fleethub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Résumé d'une expédition fleet-hub, limité aux champs dont
 * {@code CsrdReportingService.getCsrdReport()} a besoin pour regrouper les
 * émissions par lane (origine -> destination). Mappé directement depuis la
 * réponse JSON de {@code GET /api/internal/shipments/{id}} de fleet-hub (en-tête,
 * qui sérialise l'entité ShipmentOrder complète) -- les noms de champs
 * correspondent exactement à ceux de ShipmentOrder, {@code @JsonIgnoreProperties}
 * ignore le reste de ce qui est effectivement renvoyé (lignes, coûts, transporteur,
 * dont Finance n'a pas besoin ici).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipmentLaneSummary {

    private String shipperCity;
    private String shipperCountry;
    private String consigneeCity;
    private String consigneeCountry;
}
