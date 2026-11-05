package com.incokalk.dto.integration.fleethub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps du webhook POST /v1/webhooks/fleethub/shipment-events. oldStatus,
 * newStatus et dataSource ne sont présents que pour eventType =
 * SHIPMENT_STATUS_CHANGE (absents/ignorés pour SHIPMENT_CREATED).
 */
public record FleetHubShipmentEventRequest(
        @NotBlank String eventType,
        @NotNull UUID shipmentId,
        @NotBlank String orderNumber,
        String oldStatus,
        String newStatus,
        String dataSource,
        /** Identifiant unique de l'événement côté fleet-hub (absent des anciennes versions) :
         *  un renvoi du même événement est ignoré, voir FleetHubWebhookService.recordEvent. */
        UUID eventId
) {
    public FleetHubShipmentEventRequest withEventId(UUID id) {
        return new FleetHubShipmentEventRequest(eventType, shipmentId, orderNumber, oldStatus, newStatus,
                dataSource, id);
    }
}
