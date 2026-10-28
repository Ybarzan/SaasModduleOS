package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.notification.SendNotificationDTO;
import com.incokalk.model.EventOutbox;
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

/**
 * FLEETHUB_SHIPMENT_STATUS_CHANGE/FLEETHUB_SHIPMENT_CREATED restaurent un
 * producteur réel pour le moteur de règles (voir EventOutboxProcessor) :
 * fleet-hub, qui possède désormais ShipmentOrder/TrackingEvent, pousse ces
 * événements via webhook (FleetHubEventWebhookController). Les anciens types
 * SHIPMENT_STATUS_CHANGE/SHIPMENT_CREATED (sans préfixe), écrits par
 * EventPublisher depuis ShipmentService, ont été retirés avec le domaine
 * Transport -- ils restent "inconnus" pour dispatch() (jamais plus écrits
 * dans event_outbox) ; "SHIPMENT_STATUS_CHANGE" est désormais uniquement
 * l'eventType transmis à NotificationService.processEvent(), pas un type
 * d'événement outbox.
 */
@DisplayName("EventOutboxProcessor — Tests unitaires")
class EventOutboxProcessorTest {

    @Mock EventOutboxRepository eventOutboxRepo;
    @Mock NotificationService notificationService;

    private EventOutboxProcessor processor;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Pas de Redis en test -> DistributedJobLock.runExclusively execute directement le job.
        processor = new EventOutboxProcessor(eventOutboxRepo, new DistributedJobLock(Optional.empty()),
                notificationService, new ObjectMapper());
        companyId = UUID.randomUUID();
    }

    private EventOutbox pendingEvent(String eventType, String payload, int attempts) {
        return EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .companyId(companyId)
                .payload(payload)
                .status(EventOutbox.Status.PENDING)
                .attempts(attempts)
                .build();
    }

    private String shipmentStatusChangePayload() {
        return """
                {"eventType":"SHIPMENT_STATUS_CHANGE","shipmentId":"%s","orderNumber":"FH-1001","oldStatus":"EN_TRANSIT","newStatus":"DELIVERED","dataSource":"LIVE"}
                """.formatted(UUID.randomUUID()).trim();
    }

    private String shipmentCreatedPayload() {
        return """
                {"eventType":"SHIPMENT_CREATED","shipmentId":"%s","orderNumber":"FH-1002"}
                """.formatted(UUID.randomUUID()).trim();
    }

    @Test
    @DisplayName("processOne : FLEETHUB_SHIPMENT_STATUS_CHANGE -> appelle processEvent avec eventType SHIPMENT_STATUS_CHANGE, marque PROCESSED")
    void processOne_fleethubShipmentStatusChange_dispatchesToNotificationService() {
        EventOutbox event = pendingEvent("FLEETHUB_SHIPMENT_STATUS_CHANGE", shipmentStatusChangePayload(), 0);

        processor.processOne(event);

        ArgumentCaptor<SendNotificationDTO> dtoCaptor = ArgumentCaptor.forClass(SendNotificationDTO.class);
        verify(notificationService).processEvent(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getEventType()).isEqualTo("SHIPMENT_STATUS_CHANGE");
        assertThat(dtoCaptor.getValue().getCompanyId()).isEqualTo(companyId);
        assertThat(dtoCaptor.getValue().getTemplateData()).containsEntry("oldStatus", "EN_TRANSIT")
                .containsEntry("newStatus", "DELIVERED");

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PROCESSED);
    }

    @Test
    @DisplayName("processOne : FLEETHUB_SHIPMENT_CREATED -> appelle processEvent avec eventType SHIPMENT_CREATED, marque PROCESSED")
    void processOne_fleethubShipmentCreated_dispatchesToNotificationService() {
        EventOutbox event = pendingEvent("FLEETHUB_SHIPMENT_CREATED", shipmentCreatedPayload(), 0);

        processor.processOne(event);

        ArgumentCaptor<SendNotificationDTO> dtoCaptor = ArgumentCaptor.forClass(SendNotificationDTO.class);
        verify(notificationService).processEvent(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getEventType()).isEqualTo("SHIPMENT_CREATED");
        assertThat(dtoCaptor.getValue().getCompanyId()).isEqualTo(companyId);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PROCESSED);
    }

    @Test
    @DisplayName("processOne : échec au 5e essai -> abandonne, marque FAILED")
    void processOne_failureAtMaxAttempts_marksFailed() {
        EventOutbox event = pendingEvent("SOMETHING_UNKNOWN", "{}", 4);

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(5);
        verify(notificationService, never()).processEvent(any());
    }

    @Test
    @DisplayName("processOne : type d'événement inconnu -> échoue proprement (comptabilisé comme une tentative)")
    void processOne_unknownEventType_failsGracefully() {
        EventOutbox event = pendingEvent("SOMETHING_UNKNOWN", "{}", 0);

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(captor.getValue().getLastError()).contains("inconnu");
    }

    @Test
    @DisplayName("processOne : ancien type SHIPMENT_STATUS_CHANGE sans préfixe (retiré avec le domaine Transport) -> inconnu")
    void processOne_legacyUnprefixedShipmentType_isUnknown() {
        EventOutbox event = pendingEvent("SHIPMENT_STATUS_CHANGE", "{}", 0);

        processor.processOne(event);

        verify(notificationService, never()).processEvent(any());
        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(captor.getValue().getLastError()).contains("inconnu");
    }

    @Test
    @DisplayName("processOne : payload JSON invalide -> échoue proprement (comptabilisé comme une tentative, jamais propagé)")
    void processOne_malformedPayload_failsGracefully() {
        EventOutbox event = pendingEvent("FLEETHUB_SHIPMENT_CREATED", "not-json", 0);

        processor.processOne(event);

        verify(notificationService, never()).processEvent(any());
        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
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
    void processPendingEvents_processesEach() {
        EventOutbox event = pendingEvent("FLEETHUB_SHIPMENT_CREATED", shipmentCreatedPayload(), 0);
        when(eventOutboxRepo.findByStatusOrderByCreatedAtAsc(eq(EventOutbox.Status.PENDING), any()))
                .thenReturn(List.of(event));

        processor.processPendingEvents();

        verify(eventOutboxRepo).save(any());
    }
}
