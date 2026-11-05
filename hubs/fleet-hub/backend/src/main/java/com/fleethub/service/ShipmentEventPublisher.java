package com.fleethub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Company;
import com.fleethub.model.OutboundEvent;
import com.fleethub.model.TrackingEvent;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.OutboundEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Point d'extension pour les notifications liées aux expéditions (créée /
 * changement de statut). La création reste journalisée seulement : comme pour
 * les autres types d'alerte de NotificationService (maintenance, tachygraphe…),
 * une simple création de routine n'est pas en soi digne d'une alerte. Le
 * changement de statut, lui, est poussé vers le centre de notifications
 * FleetHub (voir NotificationService.notifyShipmentStatusChanged et
 * NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE).
 *
 * Alimente en plus le moteur de règles de compliance-engine (SHIPMENT_CREATED,
 * SHIPMENT_STATUS_CHANGE), opt-in par société (Company.complianceEngineWebhookKey).
 * L'événement n'est PAS envoyé ici : il est écrit dans l'outbox
 * ({@link OutboundEvent}) dans la transaction de l'appelant, puis livré après
 * commit par {@link OutboundEventDispatcher}. Ainsi :
 * <ul>
 *   <li>un rollback de l'expédition n'envoie jamais d'événement fantôme ;</li>
 *   <li>une panne de compliance-engine ne ralentit ni ne fait échouer le flux
 *       d'expédition (aucun appel HTTP dans la transaction) ;</li>
 *   <li>un événement n'est jamais perdu : il est renvoyé jusqu'à acceptation.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentEventPublisher {

    static final String SHIPMENT_CREATED = "SHIPMENT_CREATED";
    static final String SHIPMENT_STATUS_CHANGE = "SHIPMENT_STATUS_CHANGE";

    private final NotificationService notificationService;
    private final CompanyRepository companyRepository;
    private final OutboundEventRepository outboundEventRepository;
    private final ObjectMapper objectMapper;

    /** MANDATORY : appelé dans la transaction qui crée l'expédition (écriture atomique de l'événement). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shipmentCreated(UUID shipmentId, String orderNumber, Long companyId) {
        log.info("[ShipmentEvent] Expédition créée: {} ({}) pour company={}", orderNumber, shipmentId, companyId);
        UUID eventId = UUID.randomUUID();
        enqueueForComplianceEngine(companyId, eventId, SHIPMENT_CREATED,
                new ShipmentCreatedPayload(eventId, SHIPMENT_CREATED, shipmentId.toString(), orderNumber));
    }

    /** MANDATORY : appelé dans la transaction qui change le statut (écriture atomique de l'événement). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, Long companyId, Long assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        log.info("[ShipmentEvent] {} : {} -> {} (company={}, source={})",
                orderNumber, oldStatus, newStatus, companyId, dataSource);
        notificationService.notifyShipmentStatusChanged(companyId, orderNumber, oldStatus, newStatus);
        UUID eventId = UUID.randomUUID();
        enqueueForComplianceEngine(companyId, eventId, SHIPMENT_STATUS_CHANGE,
                new ShipmentStatusChangePayload(eventId, SHIPMENT_STATUS_CHANGE, shipmentId.toString(), orderNumber,
                        oldStatus, newStatus, dataSource.name()));
    }

    private void enqueueForComplianceEngine(Long companyId, UUID eventId, String eventType, Object payload) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            log.debug("[ShipmentEvent] Société {} introuvable, aucun événement compliance-engine", companyId);
            return;
        }
        String encryptedKey = company.getComplianceEngineWebhookKey();
        if (encryptedKey == null || encryptedKey.isBlank()) {
            // Société n'ayant pas configuré le partage -- cas normal, la grande
            // majorité des sociétés n'activeront jamais cette clé.
            log.debug("[ShipmentEvent] Pas de clé webhook compliance-engine pour company={}", companyId);
            return;
        }
        OutboundEvent event = new OutboundEvent();
        event.setEventId(eventId);
        event.setCompanyId(companyId);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setStatus(OutboundEvent.Status.PENDING);
        LocalDateTime now = LocalDateTime.now();
        event.setCreatedAt(now);
        event.setNextAttemptAt(now);
        outboundEventRepository.save(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de l'événement d'expédition impossible", e);
        }
    }

    /** {@code eventId} permet à compliance-engine d'ignorer un renvoi. */
    record ShipmentCreatedPayload(UUID eventId, String eventType, String shipmentId, String orderNumber) {
    }

    record ShipmentStatusChangePayload(UUID eventId, String eventType, String shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, String dataSource) {
    }
}
