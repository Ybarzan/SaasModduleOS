package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shared.ShipmentStatusChangedPayload;
import com.incokalk.model.EventOutbox;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.EventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EventPublisher — Tests unitaires")
class EventPublisherTest {

    @Mock NotificationService notificationService;
    @Mock PushNotificationService pushNotificationService;
    @Mock EventOutboxRepository eventOutboxRepo;

    // ObjectMapper reel : on veut verifier une vraie serialisation JSON, pas un mock.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks EventPublisher eventPublisher;

    private UUID shipmentId, companyId, userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventPublisher = new EventPublisher(notificationService, pushNotificationService, eventOutboxRepo, objectMapper);
        shipmentId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("shipmentStatusChanged : ecrit un evenement outbox PENDING avec le bon payload")
    void shipmentStatusChanged_writesOutboxEvent() throws Exception {
        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                TrackingEvent.DataSource.LIVE);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());

        EventOutbox saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("SHIPMENT_STATUS_CHANGE");
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getStatus()).isEqualTo(EventOutbox.Status.PENDING);

        ShipmentStatusChangedPayload payload = objectMapper.readValue(saved.getPayload(), ShipmentStatusChangedPayload.class);
        assertThat(payload.getShipmentId()).isEqualTo(shipmentId);
        assertThat(payload.getOrderNumber()).isEqualTo("CMD-001");
        assertThat(payload.getOldStatus()).isEqualTo("BOOKED");
        assertThat(payload.getNewStatus()).isEqualTo("IN_TRANSIT");
        assertThat(payload.getAssignedUserId()).isEqualTo(userId);
        assertThat(payload.getDataSource()).isEqualTo(TrackingEvent.DataSource.LIVE);

        verify(notificationService, never()).onShipmentStatusChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("shipmentStatusChanged : utilisateur assigné → pousse aussi le mobile")
    void shipmentStatusChanged_withAssignedUser_pushes() {
        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                TrackingEvent.DataSource.LIVE);

        verify(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");
    }

    @Test
    @DisplayName("shipmentStatusChanged : aucun utilisateur assigné → ne pousse rien (branche assignedUserId == null)")
    void shipmentStatusChanged_noAssignedUser_noPush() {
        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, null,
                TrackingEvent.DataSource.LIVE);

        verify(pushNotificationService, never()).sendShipmentUpdate(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("shipmentStatusChanged : échec du push mobile → n'empêche pas l'écriture outbox")
    void shipmentStatusChanged_pushThrows_stillWritesOutbox() {
        doThrow(new RuntimeException("FCM down"))
                .when(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");

        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                TrackingEvent.DataSource.LIVE);

        verify(eventOutboxRepo).save(any());
    }

    @Test
    @DisplayName("shipmentStatusChanged : échec de l'écriture outbox → n'empêche pas le push mobile")
    void shipmentStatusChanged_outboxWriteThrows_stillPushes() {
        doThrow(new RuntimeException("DB down")).when(eventOutboxRepo).save(any());

        eventPublisher.shipmentStatusChanged(shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, userId,
                TrackingEvent.DataSource.LIVE);

        verify(pushNotificationService).sendShipmentUpdate(userId, companyId, shipmentId, "IN_TRANSIT");
    }
}
