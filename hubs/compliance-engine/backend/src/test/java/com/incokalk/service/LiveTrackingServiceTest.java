package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.TrackingEventRepository;
import com.incokalk.service.tracking.LivePosition;
import com.incokalk.service.tracking.TrackingProvider;
import com.incokalk.service.tracking.TrackingProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("LiveTrackingService — Sélection du provider par expédition")
class LiveTrackingServiceTest {

    @Mock TrackingProviderRegistry registry;
    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock TrackingEventRepository trackingEventRepo;
    @Mock TrackingProvider fleetHubProvider;
    @Mock TrackingProvider roadProvider;
    @Mock TrackingProvider maritimeProvider;

    private LiveTrackingService service;
    private UUID companyId, shipmentId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LiveTrackingService(registry, shipmentRepo, trackingEventRepo);
        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
    }

    @Test
    @DisplayName("getLivePosition : expédition avec camion fleet-hub assigné -> route vers le provider FLEET_HUB avec l'immatriculation")
    void getLivePosition_withFleetHubTruck_routesToFleetHubProvider() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001")
                .fleetHubTruckRegistration("AB-123-CD")
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("FLEET_HUB")).thenReturn(fleetHubProvider);
        LivePosition expected = LivePosition.builder().latitude(1.0).longitude(2.0).build();
        when(fleetHubProvider.getCurrentPosition("AB-123-CD", companyId)).thenReturn(expected);

        LivePosition result = service.getLivePosition(shipmentId, companyId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("getLivePosition : camion fleet-hub assigné prime sur le transporteur même si carrier est défini")
    void getLivePosition_fleetHubTruckTakesPrecedenceOverCarrier() {
        Carrier seaCarrier = Carrier.builder().id(UUID.randomUUID()).transportModes("SEA").build();
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001")
                .carrier(seaCarrier).fleetHubTruckRegistration("AB-123-CD")
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("FLEET_HUB")).thenReturn(fleetHubProvider);
        when(fleetHubProvider.getCurrentPosition("AB-123-CD", companyId)).thenReturn(null);

        service.getLivePosition(shipmentId, companyId);

        org.mockito.Mockito.verify(registry).getProvider("FLEET_HUB");
        org.mockito.Mockito.verify(registry, org.mockito.Mockito.never()).getProvider("MARITIME");
    }

    @Test
    @DisplayName("getLivePosition : pas de camion fleet-hub, transporteur maritime -> route vers MARITIME avec le numéro de commande (comportement existant)")
    void getLivePosition_withoutFleetHubTruck_fallsBackToCarrierMode() {
        Carrier seaCarrier = Carrier.builder().id(UUID.randomUUID()).transportModes("SEA").build();
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001").carrier(seaCarrier)
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("MARITIME")).thenReturn(maritimeProvider);
        LivePosition expected = LivePosition.builder().latitude(3.0).longitude(4.0).build();
        when(maritimeProvider.getCurrentPosition("CMD-001", companyId)).thenReturn(expected);

        LivePosition result = service.getLivePosition(shipmentId, companyId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("getLivePosition : expédition sans camion ni transporteur -> route vers ROAD avec le numéro de commande (comportement existant)")
    void getLivePosition_noCarrierNoFleetHub_defaultsToRoad() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001")
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("ROAD")).thenReturn(roadProvider);
        when(roadProvider.getCurrentPosition("CMD-001", companyId)).thenReturn(null);

        service.getLivePosition(shipmentId, companyId);

        org.mockito.Mockito.verify(registry).getProvider("ROAD");
    }

    @Test
    @DisplayName("trackShipment : camion fleet-hub assigné -> route vers FLEET_HUB")
    void trackShipment_withFleetHubTruck_routesToFleetHubProvider() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001")
                .fleetHubTruckRegistration("AB-123-CD")
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("FLEET_HUB")).thenReturn(fleetHubProvider);
        when(fleetHubProvider.getTrackingInfo("AB-123-CD", companyId)).thenReturn(List.of());

        service.trackShipment(shipmentId, companyId);

        org.mockito.Mockito.verify(fleetHubProvider).getTrackingInfo("AB-123-CD", companyId);
    }

    @Test
    @DisplayName("getLivePosition : aucun provider enregistré pour le mode détecté -> null")
    void getLivePosition_noProviderForMode_null() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(shipmentId).company(company).orderNumber("CMD-001")
                .fleetHubTruckRegistration("AB-123-CD")
                .build();
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(registry.getProvider("FLEET_HUB")).thenReturn(null);

        assertThat(service.getLivePosition(shipmentId, companyId)).isNull();
    }
}
