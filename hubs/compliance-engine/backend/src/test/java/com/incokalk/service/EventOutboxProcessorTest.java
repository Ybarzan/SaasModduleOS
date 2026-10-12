package com.incokalk.service;

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
 * SHIPMENT_STATUS_CHANGE/SHIPMENT_CREATED étaient les deux seuls types
 * d'événement outbox existants, écrits par EventPublisher (retiré avec le
 * domaine Transport). dispatch() rejette donc désormais systématiquement tout
 * événement reçu (voir EventOutboxProcessor) ; le constructeur ne prend plus
 * NotificationService ni ObjectMapper.
 */
@DisplayName("EventOutboxProcessor — Tests unitaires")
class EventOutboxProcessorTest {

    @Mock EventOutboxRepository eventOutboxRepo;

    private EventOutboxProcessor processor;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Pas de Redis en test -> DistributedJobLock.runExclusively execute directement le job.
        processor = new EventOutboxProcessor(eventOutboxRepo, new DistributedJobLock(Optional.empty()));
        companyId = UUID.randomUUID();
    }

    private EventOutbox pendingEvent(String eventType, int attempts) {
        return EventOutbox.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .companyId(companyId)
                .payload("{}")
                .status(EventOutbox.Status.PENDING)
                .attempts(attempts)
                .build();
    }

    @Test
    @DisplayName("processOne : tout type d'événement -> échoue (aucun type n'est implémenté), reste PENDING sous le seuil")
    void processOne_anyEventType_failsBelowThreshold() {
        EventOutbox event = pendingEvent("SHIPMENT_STATUS_CHANGE", 0);

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("inconnu");
    }

    @Test
    @DisplayName("processOne : échec au 5e essai -> abandonne, marque FAILED")
    void processOne_failureAtMaxAttempts_marksFailed() {
        EventOutbox event = pendingEvent("SHIPMENT_CREATED", 4);

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("processOne : type d'événement inconnu -> échoue proprement (comptabilisé comme une tentative)")
    void processOne_unknownEventType_failsGracefully() {
        EventOutbox event = pendingEvent("SOMETHING_UNKNOWN", 0);

        processor.processOne(event);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(EventOutbox.Status.PENDING);
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
        EventOutbox event = pendingEvent("SHIPMENT_STATUS_CHANGE", 0);
        when(eventOutboxRepo.findByStatusOrderByCreatedAtAsc(eq(EventOutbox.Status.PENDING), any()))
                .thenReturn(List.of(event));

        processor.processPendingEvents();

        verify(eventOutboxRepo).save(any());
    }
}
