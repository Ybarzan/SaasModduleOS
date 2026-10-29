package com.fleethub.service;

import com.fleethub.model.Carrier;
import com.fleethub.model.Company;
import com.fleethub.model.EtaReportedSource;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.EtaReportedSourceRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import com.fleethub.service.tracking.AisStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AisEtaCaptureService — captureBroadcastEtas")
class AisEtaCaptureServiceTest {

    private ShipmentOrderRepository shipmentRepo;
    private EtaReportedSourceRepository reportedSourceRepo;
    private AisStreamService aisStreamService;
    private ShipmentEventRecorder eventRecorder;
    private AisEtaCaptureService service;

    private Company company;
    private Carrier carrier;

    @BeforeEach
    void setUp() {
        shipmentRepo = mock(ShipmentOrderRepository.class);
        reportedSourceRepo = mock(EtaReportedSourceRepository.class);
        aisStreamService = mock(AisStreamService.class);
        eventRecorder = mock(ShipmentEventRecorder.class);
        service = new AisEtaCaptureService(shipmentRepo, reportedSourceRepo, aisStreamService, eventRecorder);

        company = new Company();
        company.setId(7L);
        carrier = Carrier.builder().id(UUID.randomUUID()).name("Maersk").build();

        when(aisStreamService.isConfigured()).thenReturn(true);
        when(reportedSourceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ShipmentOrder trackedShipment(String mmsi) {
        return ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(company).carrier(carrier)
                .shipperCountry("CHN").consigneeCountry("DEU")
                .trackedVesselMmsi(mmsi)
                .status(ShipmentOrder.Status.IN_TRANSIT)
                .build();
    }

    @Test
    @DisplayName("AIS non configuré → aucun appel au repository")
    void captureBroadcastEtas_notConfigured_doesNothing() {
        when(aisStreamService.isConfigured()).thenReturn(false);

        service.captureBroadcastEtas();

        verifyNoInteractions(shipmentRepo, reportedSourceRepo);
    }

    @Test
    @DisplayName("ETA diffusée connue pour le MMSI suivi → nouvelle EtaReportedSource créée")
    void captureBroadcastEtas_newBroadcast_createsReportedSource() {
        ShipmentOrder shipment = trackedShipment("235012345");
        LocalDateTime eta = LocalDateTime.now().plusDays(5);

        when(shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(any())).thenReturn(List.of(shipment));
        when(aisStreamService.getBroadcastEta("235012345")).thenReturn(Optional.of(eta));
        when(reportedSourceRepo.findByShipmentIdAndSource(shipment.getId(), EtaReportedSource.Source.AIS_BROADCAST))
                .thenReturn(Optional.empty());

        service.captureBroadcastEtas();

        ArgumentCaptor<EtaReportedSource> captor = ArgumentCaptor.forClass(EtaReportedSource.class);
        verify(reportedSourceRepo).save(captor.capture());
        EtaReportedSource saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(EtaReportedSource.Source.AIS_BROADCAST);
        assertThat(saved.getReportedArrival()).isEqualTo(eta);
        assertThat(saved.getOriginCountry()).isEqualTo("CHN");
        assertThat(saved.getDestinationCountry()).isEqualTo("DEU");
        assertThat(saved.getShipment()).isEqualTo(shipment);
        assertThat(saved.getCarrier()).isEqualTo(carrier);

        verify(eventRecorder).record(eq(shipment), eq(ShipmentEvent.EventType.ETA_CAPTURED), any());
    }

    @Test
    @DisplayName("une EtaReportedSource AIS_BROADCAST existe déjà pour cette expédition → mise à jour en place, pas de doublon")
    void captureBroadcastEtas_existingReportedSource_updatesInPlace() {
        ShipmentOrder shipment = trackedShipment("235012345");
        LocalDateTime newEta = LocalDateTime.now().plusDays(3);
        EtaReportedSource existing = EtaReportedSource.builder()
                .id(UUID.randomUUID()).company(company).shipment(shipment).carrier(carrier)
                .source(EtaReportedSource.Source.AIS_BROADCAST)
                .reportedArrival(LocalDateTime.now().plusDays(10))
                .build();

        when(shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(any())).thenReturn(List.of(shipment));
        when(aisStreamService.getBroadcastEta("235012345")).thenReturn(Optional.of(newEta));
        when(reportedSourceRepo.findByShipmentIdAndSource(shipment.getId(), EtaReportedSource.Source.AIS_BROADCAST))
                .thenReturn(Optional.of(existing));

        service.captureBroadcastEtas();

        ArgumentCaptor<EtaReportedSource> captor = ArgumentCaptor.forClass(EtaReportedSource.class);
        verify(reportedSourceRepo).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getReportedArrival()).isEqualTo(newEta);
    }

    @Test
    @DisplayName("aucune ETA diffusée connue pour le MMSI → aucune EtaReportedSource touchée")
    void captureBroadcastEtas_noBroadcastEta_doesNotSave() {
        ShipmentOrder shipment = trackedShipment("235012345");
        when(shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(any())).thenReturn(List.of(shipment));
        when(aisStreamService.getBroadcastEta("235012345")).thenReturn(Optional.empty());

        service.captureBroadcastEtas();

        verifyNoInteractions(reportedSourceRepo);
    }

    @Test
    @DisplayName("aucune expédition suivie → aucune interaction avec AisStreamService")
    void captureBroadcastEtas_noTrackedShipments_doesNothing() {
        when(shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(any())).thenReturn(List.of());

        service.captureBroadcastEtas();

        verify(aisStreamService, never()).getBroadcastEta(any());
        verifyNoInteractions(reportedSourceRepo);
    }

    @Test
    @DisplayName("échec sur une expédition (ex. repo en panne) → les autres sont tout de même traitées")
    void captureBroadcastEtas_oneShipmentFails_othersStillProcessed() {
        ShipmentOrder failing = trackedShipment("111111111");
        ShipmentOrder ok = trackedShipment("235012345");
        LocalDateTime eta = LocalDateTime.now().plusDays(2);

        when(shipmentRepo.findByTrackedVesselMmsiIsNotNullAndStatusIn(any())).thenReturn(List.of(failing, ok));
        when(aisStreamService.getBroadcastEta("111111111")).thenReturn(Optional.of(eta));
        when(aisStreamService.getBroadcastEta("235012345")).thenReturn(Optional.of(eta));
        when(reportedSourceRepo.findByShipmentIdAndSource(eq(failing.getId()), any()))
                .thenThrow(new RuntimeException("DB down"));
        when(reportedSourceRepo.findByShipmentIdAndSource(eq(ok.getId()), any())).thenReturn(Optional.empty());

        service.captureBroadcastEtas();

        verify(reportedSourceRepo, times(1)).save(any());
    }
}
