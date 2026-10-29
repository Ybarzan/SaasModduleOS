package com.fleethub.service;

import com.fleethub.model.Company;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.ShipmentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AutoTrackingSyncService — restaure autoSyncInTransitShipments(), désactivé
 * par défaut (voir javadoc de la classe) : ces tests couvrent surtout que le
 * flag coupe bien tout appel, et qu'une fois activé, l'échec d'une
 * expédition n'empêche jamais les autres d'être traitées.
 */
@DisplayName("AutoTrackingSyncService — syncInTransitShipments")
class AutoTrackingSyncServiceTest {

    private ShipmentOrderRepository shipmentRepo;
    private LiveTrackingService liveTrackingService;
    private AutoTrackingSyncService service;

    private Company company;

    @BeforeEach
    void setUp() {
        shipmentRepo = mock(ShipmentOrderRepository.class);
        liveTrackingService = mock(LiveTrackingService.class);
        service = new AutoTrackingSyncService(shipmentRepo, liveTrackingService);

        company = new Company();
        company.setId(7L);
    }

    private void enable() {
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    private ShipmentOrder shipment(Company c) {
        return ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(c).status(ShipmentOrder.Status.IN_TRANSIT).build();
    }

    @Test
    @DisplayName("désactivé par défaut → aucune interaction")
    void syncInTransitShipments_disabledByDefault_doesNothing() {
        service.syncInTransitShipments();

        verifyNoInteractions(shipmentRepo, liveTrackingService);
    }

    @Test
    @DisplayName("activé → synchronise chaque expédition en cours")
    void syncInTransitShipments_enabled_syncsEachShipment() {
        enable();
        ShipmentOrder s1 = shipment(company);
        ShipmentOrder s2 = shipment(company);
        when(shipmentRepo.findByStatusIn(any())).thenReturn(List.of(s1, s2));

        service.syncInTransitShipments();

        verify(liveTrackingService).syncTrackingToEvents(s1.getId(), 7L);
        verify(liveTrackingService).syncTrackingToEvents(s2.getId(), 7L);
    }

    @Test
    @DisplayName("échec sur une expédition → les autres sont tout de même synchronisées")
    void syncInTransitShipments_oneShipmentFails_othersStillSynced() {
        enable();
        ShipmentOrder failing = shipment(company);
        ShipmentOrder ok = shipment(company);
        when(shipmentRepo.findByStatusIn(any())).thenReturn(List.of(failing, ok));
        when(liveTrackingService.syncTrackingToEvents(failing.getId(), 7L))
                .thenThrow(new RuntimeException("provider en panne"));

        service.syncInTransitShipments();

        verify(liveTrackingService).syncTrackingToEvents(ok.getId(), 7L);
    }

    @Test
    @DisplayName("expédition sans société → ignorée, jamais d'appel")
    void syncInTransitShipments_shipmentWithoutCompany_skipped() {
        enable();
        ShipmentOrder orphan = shipment(null);
        when(shipmentRepo.findByStatusIn(any())).thenReturn(List.of(orphan));

        service.syncInTransitShipments();

        verifyNoInteractions(liveTrackingService);
    }

    @Test
    @DisplayName("aucune expédition en cours → aucun appel, pas d'exception")
    void syncInTransitShipments_noTrackedShipments_doesNothing() {
        enable();
        when(shipmentRepo.findByStatusIn(any())).thenReturn(List.of());

        service.syncInTransitShipments();

        verifyNoInteractions(liveTrackingService);
    }
}
