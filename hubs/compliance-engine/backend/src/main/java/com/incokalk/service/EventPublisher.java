package com.incokalk.service;

import com.incokalk.model.TrackingEvent;
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

    @Async
    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, UUID companyId, UUID assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        try {
            notificationService.onShipmentStatusChange(shipmentId, orderNumber, oldStatus, newStatus, companyId, dataSource);
        } catch (Exception e) {
            log.warn("Failed to send notification for shipment status change: {}", e.getMessage());
        }
        if (assignedUserId != null) {
            try {
                pushNotificationService.sendShipmentUpdate(assignedUserId, companyId, shipmentId, newStatus);
            } catch (Exception e) {
                log.warn("Failed to send mobile push for shipment status change: {}", e.getMessage());
            }
        }
    }

    @Async
    public void shipmentCreated(UUID shipmentId, String orderNumber, UUID companyId) {
        try {
            notificationService.onShipmentCreated(shipmentId, orderNumber, companyId);
        } catch (Exception e) {
            log.warn("Failed to send notification for shipment created: {}", e.getMessage());
        }
    }

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

    @Async
    public void quoteReceived(int quoteCount, UUID companyId) {
        try {
            notificationService.onQuoteReceived(quoteCount, companyId);
        } catch (Exception e) {
            log.warn("Failed to send notification for quote received: {}", e.getMessage());
        }
    }
}
