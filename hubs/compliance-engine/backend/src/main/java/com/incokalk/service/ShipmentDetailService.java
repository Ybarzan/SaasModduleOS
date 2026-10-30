package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentDetailDto;
import com.incokalk.service.integration.fleethub.ShipmentItemDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Restaure GET /v1/shipments/{id} et .../items pour l'app mobile (retirés avec
 * le domaine Transport -- voir ShipmentDetailController, mobile/src/pages/ShipmentDetail.tsx).
 * Délègue à FleetHubIntegrationClient (même endpoint M2M que CustomsInvoiceGeneratorService/
 * LandedCostService utilisent déjà, mappé vers un DTO plus riche pour cet écran).
 * Toute panne fleet-hub (expédition introuvable pour cette société, ou fleet-hub
 * indisponible) devient un 404 -- même traitement, la distinction n'a pas de sens
 * côté app mobile (elle affiche un message d'erreur générique dans les deux cas).
 */
@Service
@RequiredArgsConstructor
public class ShipmentDetailService {

    private final FleetHubIntegrationClient fleetHubIntegrationClient;

    public ShipmentDetailDto getDetail(UUID shipmentId, UUID companyId) {
        try {
            return fleetHubIntegrationClient.getShipmentDetail(shipmentId, companyId);
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            throw new ResourceNotFoundException("Expédition introuvable : " + e.getMessage());
        }
    }

    public List<ShipmentItemDetailDto> getItems(UUID shipmentId, UUID companyId) {
        try {
            return fleetHubIntegrationClient.getShipmentItemsDetailed(shipmentId, companyId);
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            throw new ResourceNotFoundException("Expédition introuvable : " + e.getMessage());
        }
    }
}
