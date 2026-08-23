package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shared.ShipmentStatusChangedPayload;
import com.incokalk.model.EventOutbox;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.EventOutboxRepository;
import com.incokalk.scheduling.DistributedJobLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EventOutboxProcessor — Tests unitaires")
class EventOutboxProcessorTest {

    @Mock EventOutboxRepository eventOutboxRepo;
    @Mock NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private EventOutboxProcessor processor;

    private UUID companyId, shipmentId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Pas de Redis en test -> DistributedJobLock.runExclusively execute directement le job.
        processor = new EventOutboxProcessor(eventOutboxRepo, notificationService, objectMapper,
                new DistributedJobLock(Optional.empty()));
        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
    }

    private EventOutbox shipmentStatusChangeEvent() throws Exception {
        ShipmentStatusChangedPayload payload = ShipmentStatusChangedPayload.builder()
                .shipmentId(shipmentId)
                .orderNumber("CMD-001")
                .oldStatus("BOOKED")
                .newStatus("IN_TRANSIT")
                .companyId(companyId)
                .dataSource(TrackingEvent.DataSource.LIVE)
                .build();
        return EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType("SHIPMENT_STATUS_CHANGE")
                .companyId(companyId)
                .payload(objectMapper.writeValueAsString(payload))
                .status(EventOutbox.Status.PENDING)
                .attempts(0)
                .build();
    }

    @Test
    @DisplayName("processOne : succès -> déserialise le payload, notifie, marque PROCESSED")
    void processOne_success_marksProcessed() throws Exception {
        EventOutbox event = shipmentStatusChangeEvent();

        processor.processOne(event);

        verify(notificationService).onShipmentStatusChange(
                shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, TrackingEvent.DataSource.LIVE);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PROCESSED);
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
        assertThat(captor.getValue().getLastError()).isNull();
    }

    @Test
    @DisplayName("processOne : échec sous le seuil -> reste PENDING, incrémente attempts, garde l'erreur")
    void processOne_failureBelowThreshold_staysPendingAndIncrements() throws Exception {
        EventOutbox event = shipmentStatusChangeEvent();
        doThrow(new RuntimeException("SMTP down")).when(notificationService)
                .onShipmentStatusChange(any(), any(), any(), any(), any(), any());

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("SMTP down");
    }

    @Test
    @DisplayName("processOne : échec au 5e essai -> abandonne, marque FAILED")
    void processOne_failureAtMaxAttempts_marksFailed() throws Exception {
        EventOutbox event = shipmentStatusChangeEvent();
        event.setAttempts(4);
        doThrow(new RuntimeException("SMTP down")).when(notificationService)
                .onShipmentStatusChange(any(), any(), any(), any(), any(), any());

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("processOne : type d'événement inconnu -> échoue proprement (comptabilisé comme une tentative)")
    void processOne_unknownEventType_failsGracefully() {
        EventOutbox event = EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType("SOMETHING_UNKNOWN")
                .companyId(companyId)
                .payload("{}")
                .status(EventOutbox.Status.PENDING)
                .attempts(0)
                .build();

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("processPendingEvents : aucun événement en attente -> ne sauvegarde rien")
    void processPendingEvents_empty_noOp() {
        when(eventOutboxRepo.findByStatusOrderByCreatedAtAsc(eq(EventOutbox.Status.PENDING), any())).thenReturn(List.of());

        processor.processPendingEvents();

        verify(eventOutboxRepo, never()).save(any());
    }

    @Test
    @DisplayName("processPendingEvents : traite chaque événement en attente")
    void processPendingEvents_processesEach() throws Exception {
        EventOutbox event = shipmentStatusChangeEvent();
        when(eventOutboxRepo.findByStatusOrderByCreatedAtAsc(eq(EventOutbox.Status.PENDING), any()))
                .thenReturn(List.of(event));

        processor.processPendingEvents();

        verify(notificationService).onShipmentStatusChange(
                shipmentId, "CMD-001", "BOOKED", "IN_TRANSIT", companyId, TrackingEvent.DataSource.LIVE);
        verify(eventOutboxRepo).save(any());
    }
}
