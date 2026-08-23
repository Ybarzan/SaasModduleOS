package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EventPublisher — Tests unitaires")
class EventPublisherTest {

    @Mock NotificationService notificationService;
    @Mock PushNotificationService pushNotificationService;

    @InjectMocks EventPublisher eventPublisher;

    private UUID shipmentId, companyId, userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        shipmentId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("shipmentStatusChanged : utilisateur assigné → notifie et pousse le mobile")
    void shipmentStatusChanged_withAssignedUser_notifiesAndPushes() {
        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);

        verify(notificationService).onShipmentStatusChange(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);
        verify(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");
    }

    @Test
    @DisplayName("shipmentStatusChanged : aucun utilisateur assigné → notifie mais ne pousse rien (branche assignedUserId == null)")
    void shipmentStatusChanged_noAssignedUser_notifiesWithoutPush() {
        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, null,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);

        verify(notificationService).onShipmentStatusChange(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);
        verify(pushNotificationService, never()).sendShipmentUpdate(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("shipmentStatusChanged : échec du push mobile → n'empêche pas la notification classique")
    void shipmentStatusChanged_pushThrows_stillNotifies() {
        doThrow(new RuntimeException("FCM down"))
                .when(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");

        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);

        verify(notificationService).onShipmentStatusChange(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);
    }

    @Test
    @DisplayName("shipmentStatusChanged : échec de la notification classique → n'empêche pas le push mobile")
    void shipmentStatusChanged_notificationThrows_stillPushes() {
        doThrow(new RuntimeException("DB down"))
                .when(notificationService).onShipmentStatusChange(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId,
                        com.incokalk.model.TrackingEvent.DataSource.LIVE);

        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                com.incokalk.model.TrackingEvent.DataSource.LIVE);

        verify(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");
    }
}
