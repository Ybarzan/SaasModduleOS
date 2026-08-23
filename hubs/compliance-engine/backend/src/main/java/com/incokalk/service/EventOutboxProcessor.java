package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shared.ShipmentStatusChangedPayload;
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
import java.util.List;

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
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final DistributedJobLock jobLock;

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

    private void dispatch(EventOutbox event) throws Exception {
        switch (event.getEventType()) {
            case "SHIPMENT_STATUS_CHANGE" -> {
                ShipmentStatusChangedPayload payload = objectMapper.readValue(
                        event.getPayload(), ShipmentStatusChangedPayload.class);
                notificationService.onShipmentStatusChange(
                        payload.getShipmentId(), payload.getOrderNumber(),
                        payload.getOldStatus(), payload.getNewStatus(),
                        payload.getCompanyId(), payload.getDataSource());
            }
            default -> throw new IllegalArgumentException("Type d'evenement outbox inconnu: " + event.getEventType());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
