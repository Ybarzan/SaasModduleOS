package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shared.ShipmentCreatedPayload;
import com.incokalk.dto.shared.ShipmentStatusChangedPayload;
import com.incokalk.model.EventOutbox;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;
    private final EventOutboxRepository eventOutboxRepo;
    private final ObjectMapper objectMapper;

    /**
     * Pas @Async : la ligne outbox doit s'ecrire dans la MEME transaction que le
     * changement de statut qui la declenche (ShipmentService.updateStatus /
     * processWebhookEvent, tous deux @Transactional), sinon le pattern outbox ne
     * garantit plus rien -- voir V63 et docs/03-plan-migration.md. Le worker
     * EventOutboxProcessor livre la notification ensuite, avec relecture/retry.
     */
    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, UUID companyId, UUID assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        try {
            ShipmentStatusChangedPayload payload = ShipmentStatusChangedPayload.builder()
                    .shipmentId(shipmentId)
                    .orderNumber(orderNumber)
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .companyId(companyId)
                    .assignedUserId(assignedUserId)
                    .dataSource(dataSource)
                    .build();
            eventOutboxRepo.save(EventOutbox.builder()
                    .eventType("SHIPMENT_STATUS_CHANGE")
                    .companyId(companyId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to enqueue outbox event for shipment status change: {}", e.getMessage());
        }
        if (assignedUserId != null) {
            try {
                pushNotificationService.sendShipmentUpdate(assignedUserId, companyId, shipmentId, newStatus);
            } catch (Exception e) {
                log.warn("Failed to send mobile push for shipment status change: {}", e.getMessage());
            }
        }
    }

    /**
     * Meme raisonnement que shipmentStatusChanged ci-dessus : pas @Async, la
     * ligne outbox s'ecrit dans la meme transaction que ShipmentService.createShipment
     * (@Transactional). Migre 2026-08-24 (docs/03-plan-migration.md, Phase 3 J1-J2).
     */
    public void shipmentCreated(UUID shipmentId, String orderNumber, UUID companyId) {
        try {
            ShipmentCreatedPayload payload = ShipmentCreatedPayload.builder()
                    .shipmentId(shipmentId)
                    .orderNumber(orderNumber)
                    .companyId(companyId)
                    .build();
            eventOutboxRepo.save(EventOutbox.builder()
                    .eventType("SHIPMENT_CREATED")
                    .companyId(companyId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to enqueue outbox event for shipment created: {}", e.getMessage());
        }
    }

    /**
     * NON MIGRE vers l'outbox : aucun appelant reel dans le code actuel
     * (grep confirmé sur src/main) -- garder sur l'ancien chemin @Async tel
     * quel pour ne pas construire de plomberie autour d'une methode morte.
     * A migrer si/quand un vrai declencheur (health-check fournisseur) est
     * branche.
     */
    @Async
    public void providerStatusChanged(String providerType, boolean isDown, UUID companyId) {
        try {
            if (isDown) {
                notificationService.onProviderDown(providerType, companyId);
            } else {
                notificationService.onProviderRecovered(providerType, companyId);
            }
        } catch (Exception e) {
            log.warn("Failed to send notification for provider status change: {}", e.getMessage());
        }
    }

    /** NON MIGRE vers l'outbox pour la meme raison que providerStatusChanged
     * ci-dessus : aucun appelant reel dans le code actuel. */
    @Async
    public void quoteReceived(int quoteCount, UUID companyId) {
        try {
            notificationService.onQuoteReceived(quoteCount, companyId);
        } catch (Exception e) {
            log.warn("Failed to send notification for quote received: {}", e.getMessage());
        }
    }
}
