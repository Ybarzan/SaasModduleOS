package com.fleethub.service;

import com.fleethub.model.TrackingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'extension pour les notifications liées aux expéditions (créée /
 * changement de statut). Compliance-engine portait un pipeline complet
 * (outbox transactionnel + notifications push mobiles) bâti sur son propre
 * NotificationService/PushNotificationService générique — infrastructure
 * cross-cutting non portée ici (hors périmètre de cette fusion). Ce
 * publisher se contente de journaliser pour l'instant ; TODO : brancher sur
 * {@code com.fleethub.service.NotificationService} si/quand les événements
 * Transport doivent déclencher une alerte visible dans le centre de
 * notifications FleetHub (actuellement limité aux alertes flotte : maintenance,
 * tachygraphe, temps de conduite, usage anormal — voir NotificationRule.AlertType).
 */
@Slf4j
@Component
public class ShipmentEventPublisher {

    public void shipmentCreated(UUID shipmentId, String orderNumber, Long companyId) {
        log.info("[ShipmentEvent] Expédition créée: {} ({}) pour company={}", orderNumber, shipmentId, companyId);
    }

    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, Long companyId, Long assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        log.info("[ShipmentEvent] {} : {} -> {} (company={}, source={})",
                orderNumber, oldStatus, newStatus, companyId, dataSource);
    }
}
