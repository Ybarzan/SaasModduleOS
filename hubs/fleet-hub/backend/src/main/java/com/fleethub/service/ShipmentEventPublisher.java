package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.model.Company;
import com.fleethub.model.TrackingEvent;
import com.fleethub.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
 * Pousse en plus (best-effort) ces deux événements vers le moteur de règles
 * de compliance-engine, désormais orphelin depuis que le domaine Transport a
 * été migré ici (voir commit dc1e04d) : ses seuls producteurs d'événements
 * (SHIPMENT_STATUS_CHANGE, SHIPMENT_CREATED) ont disparu avec la migration.
 * Opt-in par société (Company.complianceEngineWebhookKey) -- la plupart des
 * sociétés n'auront jamais configuré cette clé, auquel cas aucun appel n'est
 * tenté. Volontairement fire-and-forget pour cette première itération : pas
 * de retry, pas d'outbox côté fleet-hub -- la fiabilité de la livraison
 * (rejouer un événement perdu) est le rôle d'event_outbox + EventOutboxProcessor
 * côté compliance-engine, une fois l'événement arrivé. Ici, une indisponibilité
 * ou une mauvaise configuration de compliance-engine ne doit jamais ralentir
 * ni faire échouer le flux d'expédition local de fleet-hub : toute l'opération
 * est encapsulée dans un try/catch(Exception) qui ne fait que logger.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentEventPublisher {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/fleethub/shipment-events";

    private final NotificationService notificationService;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ApiKeyCrypto apiKeyCrypto;

    @Value("${app.integration.compliance-engine.base-url}")
    private String complianceEngineBaseUrl;

    public void shipmentCreated(UUID shipmentId, String orderNumber, Long companyId) {
        log.info("[ShipmentEvent] Expédition créée: {} ({}) pour company={}", orderNumber, shipmentId, companyId);
        publishToComplianceEngine(companyId, new ShipmentCreatedPayload(
                "SHIPMENT_CREATED", shipmentId.toString(), orderNumber));
    }

    public void shipmentStatusChanged(UUID shipmentId, String orderNumber,
                                       String oldStatus, String newStatus, Long companyId, Long assignedUserId,
                                       TrackingEvent.DataSource dataSource) {
        log.info("[ShipmentEvent] {} : {} -> {} (company={}, source={})",
                orderNumber, oldStatus, newStatus, companyId, dataSource);
        notificationService.notifyShipmentStatusChanged(companyId, orderNumber, oldStatus, newStatus);
        publishToComplianceEngine(companyId, new ShipmentStatusChangePayload(
                "SHIPMENT_STATUS_CHANGE", shipmentId.toString(), orderNumber, oldStatus, newStatus, dataSource.name()));
    }

    private void publishToComplianceEngine(Long companyId, Object payload) {
        try {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) {
                log.debug("[ShipmentEvent] Société {} introuvable, aucun appel compliance-engine", companyId);
                return;
            }
            String encryptedKey = company.getComplianceEngineWebhookKey();
            if (encryptedKey == null || encryptedKey.isBlank()) {
                // Société n'ayant pas configuré le partage -- cas normal, la grande
                // majorité des sociétés n'activeront jamais cette clé.
                log.debug("[ShipmentEvent] Pas de clé webhook compliance-engine pour company={}, aucun appel", companyId);
                return;
            }
            String webhookKey = apiKeyCrypto.decrypt(encryptedKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-FleetHub-Webhook-Key", webhookKey);

            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            String url = complianceEngineBaseUrl + WEBHOOK_PATH;
            restTemplate.postForEntity(url, entity, Void.class);
        } catch (Exception e) {
            // Best-effort : une panne ou une mauvaise config de compliance-engine ne
            // doit jamais remonter dans le flux d'expédition local de fleet-hub.
            log.warn("[ShipmentEvent] Échec de l'appel webhook compliance-engine pour company={}: {}",
                    companyId, e.getMessage());
        }
    }

    private record ShipmentCreatedPayload(String eventType, String shipmentId, String orderNumber) {
    }

    private record ShipmentStatusChangePayload(String eventType, String shipmentId, String orderNumber,
                                                 String oldStatus, String newStatus, String dataSource) {
    }
}
