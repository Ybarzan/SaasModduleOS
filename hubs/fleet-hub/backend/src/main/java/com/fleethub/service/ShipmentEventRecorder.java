package com.fleethub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.ShipmentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Point d'écriture unique du journal d'événements d'expédition -- ADR-0001,
 * Phase 1 (double écriture). Appelé en plus du comportement métier existant,
 * jamais à sa place : un échec ici est journalisé et ignoré, jamais propagé,
 * pour ne jamais faire échouer ou annuler l'opération métier qui l'a
 * déclenché (même principe que CarrierBookingService.recordEtaReportedSource
 * et AisEtaCaptureService.captureBroadcastEtas).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentEventRecorder {

    private final ShipmentEventRepository repo;
    private final ObjectMapper objectMapper;

    public void record(ShipmentOrder shipment, ShipmentEvent.EventType eventType, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
            ShipmentEvent event = ShipmentEvent.builder()
                    .company(shipment.getCompany())
                    .shipment(shipment)
                    .eventType(eventType)
                    .payload(json)
                    .occurredAt(LocalDateTime.now())
                    .build();
            repo.save(event);
        } catch (Exception e) {
            log.warn("[ShipmentEventRecorder] Échec d'enregistrement de l'événement {} pour l'expédition {}: {}",
                    eventType, shipment.getId(), e.getMessage());
        }
    }
}
