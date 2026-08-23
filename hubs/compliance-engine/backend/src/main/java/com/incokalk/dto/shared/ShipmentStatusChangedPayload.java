package com.incokalk.dto.shared;

import com.incokalk.model.TrackingEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Payload serialise en JSON dans EventOutbox.payload pour l'evenement
 * SHIPMENT_STATUS_CHANGE -- voir EventPublisher.shipmentStatusChanged et
 * EventOutboxProcessor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentStatusChangedPayload {
    private UUID shipmentId;
    private String orderNumber;
    private String oldStatus;
    private String newStatus;
    private UUID companyId;
    private UUID assignedUserId;
    private TrackingEvent.DataSource dataSource;
}
