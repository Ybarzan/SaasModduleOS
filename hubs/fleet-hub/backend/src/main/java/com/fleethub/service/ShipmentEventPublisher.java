package com.fleethub.service;

import com.fleethub.model.TrackingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'extension pour les notifications liées aux expéditions (créée /
 * changement de statut). La création reste journalisée seulement : comme pour
 * les autres types d'alerte de NotificationService (maintenance, tachygraphe…),
 * une simple création de routine n'est pas en soi digne d'une alerte. Le
 * changement de statut, lui, est poussé vers le centre de notifications
 * FleetHub (voir NotificationService.notifyShipmentStatusChanged et
 * NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentEventPublisher {

    private final NotificationService notificationService;

    public void shipmentCreated(UUID shipmentId, String orderNumber, Long companyId) {
        log.info("[ShipmentEvent] Expédition créée: {} ({}) pour company={}", orderNumber, shipmentId, companyId);
    }

    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, Long companyId, Long assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        log.info("[ShipmentEvent] {} : {} -> {} (company={}, source={})",
                orderNumber, oldStatus, newStatus, companyId, dataSource);
        notificationService.notifyShipmentStatusChanged(companyId, orderNumber, oldStatus, newStatus);
    }
}
