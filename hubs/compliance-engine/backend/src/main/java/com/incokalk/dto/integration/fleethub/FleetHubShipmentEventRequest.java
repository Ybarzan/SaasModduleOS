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
        String dataSource
) {}
