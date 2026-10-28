package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.integration.fleethub.FleetHubShipmentEventRequest;
import com.incokalk.dto.notification.SendNotificationDTO;
import com.incokalk.model.EventOutbox;
import com.incokalk.repository.EventOutboxRepository;
import com.incokalk.scheduling.DistributedJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Livre les evenements ecrits dans event_outbox (V63) par EventPublisher.
 * Relit periodiquement les lignes PENDING plutot que de compter sur un
 * appel synchrone qui peut se perdre en silence -- c'est la garantie de
 * livraison que le pattern outbox est cense apporter (docs/03-plan-migration.md,
 * Phase 3 J1-J2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventOutboxProcessor {

    /** Au-dela de ce nombre d'echecs, on arrete de reessayer -- une erreur de
     * desserialisation ou un bug ne doit pas boucler indefiniment. */
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 50;

    private final EventOutboxRepository eventOutboxRepo;
    private final DistributedJobLock jobLock;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 30000)
    public void processPendingEvents() {
        jobLock.runExclusively("process-event-outbox", Duration.ofMinutes(2), () -> {
            List<EventOutbox> pending = eventOutboxRepo.findByStatusOrderByCreatedAtAsc(
                    EventOutbox.Status.PENDING, PageRequest.of(0, BATCH_SIZE));
            if (pending.isEmpty()) return;

            log.debug("[EventOutbox] {} evenement(s) en attente", pending.size());
            for (EventOutbox event : pending) {
                processOne(event);
            }
        });
    }

    @Transactional
    public void processOne(EventOutbox event) {
        try {
            dispatch(event);
            event.setStatus(EventOutbox.Status.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception e) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(truncate(e.getMessage(), 1000));
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.setStatus(EventOutbox.Status.FAILED);
                log.warn("[EventOutbox] Evenement {} ({}) abandonne apres {} tentatives: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), e.getMessage());
            } else {
                log.debug("[EventOutbox] Echec tentative {}/{} pour {} ({}): {}",
                        event.getAttempts(), MAX_ATTEMPTS, event.getId(), event.getEventType(), e.getMessage());
            }
        }
        eventOutboxRepo.save(event);
    }

    /**
     * SHIPMENT_STATUS_CHANGE/SHIPMENT_CREATED etaient ecrits par EventPublisher,
     * appele depuis ShipmentService -- retire avec le domaine Transport (V75).
     * FLEETHUB_* restaure un producteur reel (hors Transport) : fleet-hub, qui
     * possede desormais ShipmentOrder/TrackingEvent, pousse ces evenements via
     * webhook (voir FleetHubEventWebhookController/FleetHubWebhookService). Le
     * eventType passe a processEvent() reste "SHIPMENT_STATUS_CHANGE"/
     * "SHIPMENT_CREATED" (sans prefixe) : toute NotificationRule configuree avec
     * ces valeurs avant le retrait du domaine Transport redevient donc
     * fonctionnelle sans que l'utilisateur ait quoi que ce soit a reconfigurer.
     */
    private void dispatch(EventOutbox event) throws Exception {
        switch (event.getEventType()) {
            case "FLEETHUB_SHIPMENT_STATUS_CHANGE" -> handleShipmentStatusChange(event);
            case "FLEETHUB_SHIPMENT_CREATED" -> handleShipmentCreated(event);
            default -> throw new IllegalArgumentException("Type d'evenement outbox inconnu: " + event.getEventType());
        }
    }

    private void handleShipmentStatusChange(EventOutbox event) throws Exception {
        FleetHubShipmentEventRequest payload = objectMapper.readValue(
                event.getPayload(), FleetHubShipmentEventRequest.class);

        Map<String, String> templateData = new HashMap<>();
        templateData.put("orderNumber", payload.orderNumber());
        templateData.put("oldStatus", payload.oldStatus());
        templateData.put("newStatus", payload.newStatus());
        templateData.put("dataSource", payload.dataSource());

        notificationService.processEvent(SendNotificationDTO.builder()
                .eventType("SHIPMENT_STATUS_CHANGE")
                .companyId(event.getCompanyId())
                .entityType("SHIPMENT")
                .entityId(payload.shipmentId())
                .title("Expédition " + payload.orderNumber() + " — changement de statut")
                .message(payload.oldStatus() + " → " + payload.newStatus())
                .templateData(templateData)
                .build());
    }

    private void handleShipmentCreated(EventOutbox event) throws Exception {
        FleetHubShipmentEventRequest payload = objectMapper.readValue(
                event.getPayload(), FleetHubShipmentEventRequest.class);

        notificationService.processEvent(SendNotificationDTO.builder()
                .eventType("SHIPMENT_CREATED")
                .companyId(event.getCompanyId())
                .entityType("SHIPMENT")
                .entityId(payload.shipmentId())
                .title("Nouvelle expédition " + payload.orderNumber())
                .message("Expédition " + payload.orderNumber() + " créée sur fleet-hub")
                .templateData(Map.of("orderNumber", payload.orderNumber()))
                .build());
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
