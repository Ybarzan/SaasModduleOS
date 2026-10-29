package com.fleethub.service;

import com.fleethub.model.EtaReportedSource;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.EtaReportedSourceRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.service.tracking.AisStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Capture périodiquement l'ETA que les navires suivis diffusent eux-mêmes via
 * AIS (voir AisStreamService.getBroadcastEta, EtaReportedSource.Source.AIS_BROADCAST)
 * pour les expéditions liées à un navire (ShipmentOrder.trackedVesselMmsi) --
 * complément AIS de Phase 2, docs/03-plan-migration.md.
 * <p>
 * Au plus une EtaReportedSource AIS_BROADCAST par expédition, toujours mise à
 * jour avec la dernière ETA diffusée connue (upsert) -- pas une ligne par
 * cycle de capture : la fiabilité mesurée compare la DERNIÈRE ETA connue
 * avant livraison à l'arrivée réelle, pas une moyenne de toutes les mises à
 * jour intermédiaires (voir EtaSourceReliabilityService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AisEtaCaptureService {

    private static final List<ShipmentOrder.Status> TRACKABLE_STATUSES =
            List.of(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.IN_TRANSIT);

    private final ShipmentOrderRepository shipmentRepo;
    private final EtaReportedSourceRepository reportedSourceRepo;
    private final AisStreamService aisStreamService;
    private final ShipmentEventRecorder eventRecorder;

    @Scheduled(fixedDelay = 900000)
    public void captureBroadcastEtas() {
        if (!aisStreamService.isConfigured()) return;

        List<ShipmentOrder> tracked = shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(TRACKABLE_STATUSES);
        int captured = 0;
        for (ShipmentOrder shipment : tracked) {
            try {
                Optional<LocalDateTime> broadcastEta = aisStreamService.getBroadcastEta(shipment.getTrackedVesselMmsi());
                if (broadcastEta.isEmpty()) continue;
                upsert(shipment, broadcastEta.get());
                captured++;
            } catch (Exception e) {
                log.warn("[AisEtaCapture] Échec de capture pour l'expédition {} (MMSI {}): {}",
                        shipment.getId(), shipment.getTrackedVesselMmsi(), e.getMessage());
            }
        }
        if (captured > 0) {
            log.info("[AisEtaCapture] {} ETA diffusée(s) capturée(s)/mise(s) à jour sur {} expédition(s) suivie(s)",
                    captured, tracked.size());
        }
    }

    void upsert(ShipmentOrder shipment, LocalDateTime broadcastEta) {
        EtaReportedSource source = reportedSourceRepo
                .findByShipmentIdAndSource(shipment.getId(), EtaReportedSource.Source.AIS_BROADCAST)
                .orElseGet(() -> EtaReportedSource.builder()
                        .company(shipment.getCompany())
                        .shipment(shipment)
                        .carrier(shipment.getCarrier())
                        .source(EtaReportedSource.Source.AIS_BROADCAST)
                        .build());

        source.setReportedArrival(broadcastEta);
        source.setOriginCountry(shipment.getShipperCountry());
        source.setDestinationCountry(shipment.getConsigneeCountry());
        source.setTransportMode(shipment.getTransportMode());
        reportedSourceRepo.save(source);

        eventRecorder.record(shipment, ShipmentEvent.EventType.ETA_CAPTURED,
                Map.of("source", EtaReportedSource.Source.AIS_BROADCAST.name(),
                        "reportedArrival", String.valueOf(broadcastEta)));
    }
}
