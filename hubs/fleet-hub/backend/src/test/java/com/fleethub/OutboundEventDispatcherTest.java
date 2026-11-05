package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.model.OutboundEvent;
import com.fleethub.model.TrackingEvent;
import com.fleethub.repository.OutboundEventRepository;
import com.fleethub.service.OutboundEventDispatcher;
import com.fleethub.service.ShipmentEventPublisher;
import com.fleethub.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Livraison fiable des événements d'expédition vers compliance-engine :
 * écriture atomique dans l'outbox, envoi après commit, nouvelles tentatives,
 * ordre conservé par société, abandon puis relance.
 * La passe planifiée est neutralisée (intervalle d'une heure) : les tests
 * appellent {@link OutboundEventDispatcher#dispatchBatch()} eux-mêmes.
 */
@SpringBootTest(properties = "app.integration.compliance-engine.dispatch-interval-ms=3600000")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboundEventDispatcherTest {

    private static final String WEBHOOK_URL = "http://localhost:8081/api/v1/webhooks/fleethub/shipment-events";
    private static final String CE_KEY = "ce-webhook-key-123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private OutboundEventDispatcher dispatcher;
    @Autowired
    private OutboundEventRepository eventRepository;
    @Autowired
    private ShipmentService shipmentService;
    @Autowired
    private ShipmentEventPublisher publisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private MockRestServiceServer complianceEngine;
    private String token;
    private long companyId;

    @BeforeEach
    void setUp() throws Exception {
        // Chaque passe d'envoi traite toutes les sociétés : on repart d'une outbox vide.
        eventRepository.deleteAll();
        complianceEngine = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(false).build();
        String email = "outbox-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Outbox\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        token = body.get("token").asText();
        companyId = body.get("companyId").asLong();
        mvc.perform(put("/api/compliance-engine-webhook/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"webhookKey\":\"" + CE_KEY + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID createShipment(String consignee) {
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder().shipperName("Labo").consigneeName(consignee).build();
        return shipmentService.createShipment(dto, null, companyId).getId();
    }

    private List<OutboundEvent> events() {
        return eventRepository.findAll().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
    }

    @Test
    void creatingAShipment_queuesTheEvent_withoutAnyHttpCall_thenTheDispatcherDeliversIt() throws Exception {
        complianceEngine.expect(never(), requestTo(WEBHOOK_URL));
        createShipment("Plateau technique");
        complianceEngine.verify();

        OutboundEvent queued = events().get(0);
        assertEquals(OutboundEvent.Status.PENDING, queued.getStatus());

        complianceEngine.reset();
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-FleetHub-Webhook-Key", CE_KEY))
                .andExpect(header("X-FleetHub-Event-Id", queued.getEventId().toString()))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertEquals(1, dispatcher.dispatchBatch());
        complianceEngine.verify();
        OutboundEvent sent = events().get(0);
        assertEquals(OutboundEvent.Status.SENT, sent.getStatus());
        assertNotNull(sent.getSentAt());
        assertEquals(queued.getEventId().toString(),
                objectMapper.readTree(sent.getPayload()).get("eventId").asText());
    }

    @Test
    void rolledBackTransaction_leavesNoEvent() {
        transactionTemplate.executeWithoutResult(tx -> {
            createShipment("Annulée");
            tx.setRollbackOnly();
        });
        assertTrue(events().isEmpty(), "un rollback ne doit jamais laisser d'événement à envoyer");
    }

    @Test
    void publishingOutsideATransaction_isRefused() {
        assertThrows(IllegalTransactionStateException.class,
                () -> publisher.shipmentCreated(UUID.randomUUID(), "ORD-X", companyId));
        assertThrows(IllegalTransactionStateException.class,
                () -> publisher.shipmentStatusChanged(UUID.randomUUID(), "ORD-X", "DRAFT", "BOOKED", companyId,
                        null, TrackingEvent.DataSource.MANUAL));
    }

    @Test
    void unavailableComplianceEngine_isRetriedLater_andLaterEventsOfTheCompanyWait() {
        createShipment("Premier");
        createShipment("Second");
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(0, dispatcher.dispatchBatch());
        complianceEngine.verify(); // un seul appel : le second événement attend le premier

        List<OutboundEvent> events = events();
        OutboundEvent first = events.get(0);
        assertEquals(OutboundEvent.Status.PENDING, first.getStatus());
        assertEquals(1, first.getAttempts());
        assertTrue(first.getLastError().contains("503"));
        assertTrue(first.getNextAttemptAt().isAfter(LocalDateTime.now().plusSeconds(20)));
        assertEquals(0, events.get(1).getAttempts());

        // Rien n'est renvoyé avant l'échéance du premier.
        complianceEngine.reset();
        complianceEngine.expect(never(), requestTo(WEBHOOK_URL));
        assertEquals(0, dispatcher.dispatchBatch());
        complianceEngine.verify();

        // Échéance passée : les deux partent, dans l'ordre.
        first.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        eventRepository.save(first);
        complianceEngine.reset();
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL))
                .andExpect(header("X-FleetHub-Event-Id", first.getEventId().toString()))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL))
                .andExpect(header("X-FleetHub-Event-Id", events.get(1).getEventId().toString()))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        assertEquals(2, dispatcher.dispatchBatch());
        complianceEngine.verify();
    }

    @Test
    void rejectedPayload_isDroppedAtOnce_withoutBlockingTheNextEvents() {
        createShipment("Refusé");
        createShipment("Accepté");
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertEquals(1, dispatcher.dispatchBatch());
        complianceEngine.verify();
        assertEquals(OutboundEvent.Status.FAILED, events().get(0).getStatus());
        assertEquals(OutboundEvent.Status.SENT, events().get(1).getStatus());
    }

    @Test
    void afterTheLastAttempt_theEventIsFailed_thenAnAdminCanRetryIt() throws Exception {
        createShipment("Abandonné");
        OutboundEvent event = events().get(0);
        event.setAttempts(11);
        eventRepository.save(event);
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        dispatcher.dispatchBatch();
        assertEquals(OutboundEvent.Status.FAILED, events().get(0).getStatus());

        mvc.perform(post("/api/compliance-engine-webhook/retry-failed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedEvents").value(0))
                .andExpect(jsonPath("$.pendingEvents").value(1));
        assertEquals(0, events().get(0).getAttempts());
    }

    @Test
    void settings_exposeTheQueueState() throws Exception {
        createShipment("En attente");
        complianceEngine.expect(once(), requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        dispatcher.dispatchBatch();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.pendingEvents").value(1))
                .andExpect(jsonPath("$.failedEvents").value(0))
                .andExpect(jsonPath("$.lastError").value(org.hamcrest.Matchers.containsString("502")));
    }

    @Test
    void keyRemovedBeforeSending_dropsTheEvent() throws Exception {
        createShipment("Sans destinataire");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/compliance-engine-webhook/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        complianceEngine.expect(never(), requestTo(WEBHOOK_URL));

        dispatcher.dispatchBatch();
        complianceEngine.verify();
        assertEquals(OutboundEvent.Status.FAILED, events().get(0).getStatus());
    }

    @Test
    void backoff_doubles_andIsCappedAtOneHour() {
        assertEquals(Duration.ofSeconds(30), backoff(1));
        assertEquals(Duration.ofMinutes(1), backoff(2));
        assertEquals(Duration.ofMinutes(2), backoff(3));
        assertEquals(Duration.ofHours(1), backoff(9));
        assertEquals(Duration.ofHours(1), backoff(40));
    }

    private static Duration backoff(int attempts) {
        try {
            var m = OutboundEventDispatcher.class.getDeclaredMethod("backoff", int.class);
            m.setAccessible(true);
            return (Duration) m.invoke(null, attempts);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
