package com.incokalk.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réduit aux seuls champs survivant au retrait du domaine Transport :
 * totalShipments/activeShipments/deliveredShipments/draftShipments/cancelledShipments,
 * les champs de coûts/poids/volume/CO₂ et totalCarriers/activeCarriers lisaient
 * ShipmentOrderRepository/CarrierRepository directement et ont été retirés.
 * Une réintroduction future nécessiterait un appel REST vers fleet-hub plutôt
 * qu'une lecture locale.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    // Simulations (SimulationRepository, indépendant du domaine Transport)
    private long totalSimulations;
    private long simulationsThisMonth;

    // Time period (conservé pour compatibilité d'API ; n'influence plus le résultat)
    private String period;
}
