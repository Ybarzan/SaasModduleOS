package com.fleethub.service;

import com.fleethub.model.TrackingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("ShipmentEventPublisher — Tests unitaires")
class ShipmentEventPublisherTest {

    NotificationService notificationService;
    ShipmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        publisher = new ShipmentEventPublisher(notificationService);
    }

    @Test
    @DisplayName("shipmentStatusChanged → pousse une notification de changement de statut")
    void shipmentStatusChanged_notifiesStatusChange() {
        UUID shipmentId = UUID.randomUUID();

        publisher.shipmentStatusChanged(shipmentId, "ORD-001", "EN_TRANSIT", "LIVRE",
                42L, 7L, TrackingEvent.DataSource.MANUAL);

        verify(notificationService).notifyShipmentStatusChanged(42L, "ORD-001", "EN_TRANSIT", "LIVRE");
    }

    @Test
    @DisplayName("shipmentCreated → ne déclenche aucune notification (création de routine)")
    void shipmentCreated_doesNotNotify() {
        publisher.shipmentCreated(UUID.randomUUID(), "ORD-002", 42L);

        verifyNoInteractions(notificationService);
    }
}
