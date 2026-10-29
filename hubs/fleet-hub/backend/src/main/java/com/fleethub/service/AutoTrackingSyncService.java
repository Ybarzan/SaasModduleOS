package com.fleethub.service;

import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.ShipmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Restaure la synchronisation automatique du suivi temps réel pour les
 * expéditions en cours -- {@code autoSyncInTransitShipments()}, retirée de
 * compliance-engine avec le domaine Transport (elle lisait
 * {@code ShipmentOrderRepository} et appelait {@code LiveTrackingService},
 * tous deux côté fleet-hub désormais, voir docs/03-plan-migration.md).
 * <p>
 * <b>Désactivé par défaut</b> ({@code fleethub.tracking.auto-sync.enabled}) --
 * contrairement aux autres restaurations de ce type dans ce projet, celle-ci
 * déclenche des appels automatiques vers des API tierces potentiellement
 * facturées (Ship24/AviationStack/AIS, voir {@code TrackingProvider}) pour
 * chaque société ayant déjà configuré une clé. Chaque appel individuel est
 * déjà gratuit si aucune clé n'est configurée ({@code TrackingProvider.isAvailable}),
 * mais l'activer change silencieusement la consommation de quota/coût des
 * sociétés qui en ont une -- jusqu'ici uniquement déclenchée par un clic
 * manuel ({@code POST /api/tracking/shipments/{id}/sync}). Un déploiement
 * doit l'activer consciemment après avoir vérifié ses quotas, pas l'inverse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTrackingSyncService {

    private static final List<ShipmentOrder.Status> TRACKABLE_STATUSES =
            List.of(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.IN_TRANSIT);

    private final ShipmentOrderRepository shipmentRepo;
    private final LiveTrackingService liveTrackingService;

    @Value("${fleethub.tracking.auto-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelay = 900000)
    public void syncInTransitShipments() {
        if (!enabled) return;

        List<ShipmentOrder> tracked = shipmentRepo.findByStatusIn(TRACKABLE_STATUSES);
        int synced = 0;
        for (ShipmentOrder shipment : tracked) {
            if (shipment.getCompany() == null) continue;
            try {
                liveTrackingService.syncTrackingToEvents(shipment.getId(), shipment.getCompany().getId());
                synced++;
            } catch (Exception e) {
                log.warn("[AutoTrackingSync] Échec de synchronisation pour l'expédition {}: {}",
                        shipment.getId(), e.getMessage());
            }
        }
        if (synced > 0) {
            log.info("[AutoTrackingSync] {} expédition(s) synchronisée(s) sur {} en cours",
                    synced, tracked.size());
        }
    }
}
