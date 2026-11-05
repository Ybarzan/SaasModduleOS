package com.fleethub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Company;
import com.fleethub.model.OutboundEvent;
import com.fleethub.model.TrackingEvent;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.OutboundEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L'émetteur ne fait plus aucun appel HTTP : il met l'événement en attente dans
 * l'outbox (envoi après commit par OutboundEventDispatcher, testé à part).
 */
@DisplayName("ShipmentEventPublisher — Tests unitaires")
class ShipmentEventPublisherTest {

    NotificationService notificationService;
    CompanyRepository companyRepository;
    OutboundEventRepository outboundEventRepository;
    ObjectMapper objectMapper = new ObjectMapper();
    ShipmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        companyRepository = mock(CompanyRepository.class);
        outboundEventRepository = mock(OutboundEventRepository.class);
        publisher = new ShipmentEventPublisher(notificationService, companyRepository, outboundEventRepository,
                objectMapper);
    }

    private Company companyWithWebhookKey(String key) {
        Company company = new Company();
        company.setId(42L);
        company.setComplianceEngineWebhookKey(key);
        return company;
    }

    private OutboundEvent savedEvent() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(outboundEventRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("shipmentStatusChanged → pousse une notification de changement de statut")
    void shipmentStatusChanged_notifiesStatusChange() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));

        publisher.shipmentStatusChanged(UUID.randomUUID(), "ORD-001", "EN_TRANSIT", "LIVRE", 42L, null,
                TrackingEvent.DataSource.MANUAL);

        verify(notificationService).notifyShipmentStatusChanged(42L, "ORD-001", "EN_TRANSIT", "LIVRE");
    }

    @Test
    @DisplayName("shipmentCreated → aucune notification (création de routine)")
    void shipmentCreated_doesNotNotify() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-002", 42L);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Pas de clé webhook → aucun événement mis en attente")
    void noWebhookKey_enqueuesNothing() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-003", 42L);

        verify(outboundEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Clé webhook vide → aucun événement mis en attente")
    void blankWebhookKey_enqueuesNothing() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey("   ")));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-004", 42L);

        verify(outboundEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("shipmentCreated avec clé → événement PENDING, dû immédiatement, eventId dans le corps")
    void shipmentCreated_withWebhookKey_enqueuesPendingEvent() throws Exception {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey("chiffrée")));
        UUID shipmentId = UUID.randomUUID();

        publisher.shipmentCreated(shipmentId, "ORD-005", 42L);

        OutboundEvent event = savedEvent();
        assertEquals(OutboundEvent.Status.PENDING, event.getStatus());
        assertEquals(42L, event.getCompanyId());
        assertEquals("SHIPMENT_CREATED", event.getEventType());
        assertNotNull(event.getNextAttemptAt());
        JsonNode body = objectMapper.readTree(event.getPayload());
        assertEquals(event.getEventId().toString(), body.get("eventId").asText());
        assertEquals("SHIPMENT_CREATED", body.get("eventType").asText());
        assertEquals(shipmentId.toString(), body.get("shipmentId").asText());
        assertEquals("ORD-005", body.get("orderNumber").asText());
    }

    @Test
    @DisplayName("shipmentStatusChanged avec clé → corps complet (statuts, source)")
    void shipmentStatusChanged_withWebhookKey_enqueuesFullPayload() throws Exception {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey("chiffrée")));

        publisher.shipmentStatusChanged(UUID.randomUUID(), "ORD-006", "EN_TRANSIT", "LIVRE", 42L, null,
                TrackingEvent.DataSource.LIVE);

        JsonNode body = objectMapper.readTree(savedEvent().getPayload());
        assertEquals("SHIPMENT_STATUS_CHANGE", body.get("eventType").asText());
        assertEquals("EN_TRANSIT", body.get("oldStatus").asText());
        assertEquals("LIVRE", body.get("newStatus").asText());
        assertEquals("LIVE", body.get("dataSource").asText());
    }
}
