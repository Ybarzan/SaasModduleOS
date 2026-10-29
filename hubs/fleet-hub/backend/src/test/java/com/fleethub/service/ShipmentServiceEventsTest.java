package com.fleethub.service;

import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.dto.ShipmentStatusUpdateDTO;
import com.fleethub.model.Company;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ADR-0001, Phase 1 (double écriture) : vérifie uniquement les nouveaux
 * appels à ShipmentEventRecorder ajoutés dans createShipment/updateStatus/
 * processWebhookEvent -- le reste du comportement de ces méthodes n'avait
 * pas de couverture avant cette passe et reste hors scope (voir
 * ShipmentServiceVesselTest, même principe pour assignVessel).
 */
@DisplayName("ShipmentService — journalisation des événements (ADR-0001 Phase 1)")
class ShipmentServiceEventsTest {

    private ShipmentOrderRepository shipmentRepo;
    private CompanyRepository companyRepo;
    private ShipmentEventRecorder eventRecorder;
    private ShipmentService service;

    private Long companyId;
    private Company company;

    @BeforeEach
    void setUp() {
        shipmentRepo = mock(ShipmentOrderRepository.class);
        companyRepo = mock(CompanyRepository.class);
        eventRecorder = mock(ShipmentEventRecorder.class);

        service = new ShipmentService(
                shipmentRepo, mock(TrackingEventRepository.class), mock(CarrierRepository.class),
                mock(ShippingRateRepository.class), companyRepo, mock(AppUserRepository.class),
                mock(TruckRepository.class), mock(ShipmentEventPublisher.class), mock(ShipmentItemRepository.class),
                mock(ClientUserRepository.class), eventRecorder);

        companyId = 7L;
        company = new Company();
        company.setId(companyId);

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(shipmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("createShipment → SHIPMENT_CREATED journalisé avec le numéro de commande")
    void createShipment_recordsShipmentCreated() {
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder()
                .shipperName("Acme").consigneeName("Bodacc").build();

        ShipmentOrder created = service.createShipment(dto, 1L, companyId);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventRecorder).record(eq(created), eq(ShipmentEvent.EventType.SHIPMENT_CREATED), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsEntry("orderNumber", created.getOrderNumber());
    }

    @Test
    @DisplayName("updateStatus (manuel) → STATUS_CHANGED journalisé avec dataSource MANUAL")
    void updateStatus_recordsStatusChanged() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(company).status(ShipmentOrder.Status.DRAFT).build();
        when(shipmentRepo.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        ShipmentStatusUpdateDTO dto = new ShipmentStatusUpdateDTO();
        dto.setStatus("BOOKED");

        service.updateStatus(shipment.getId(), dto, companyId);

        verify(eventRecorder).record(eq(shipment), eq(ShipmentEvent.EventType.STATUS_CHANGED),
                eq(Map.of("oldStatus", "DRAFT", "newStatus", "BOOKED", "dataSource", "MANUAL")));
    }

    @Test
    @DisplayName("processWebhookEvent → STATUS_CHANGED journalisé avec dataSource LIVE")
    void processWebhookEvent_statusChanges_recordsStatusChanged() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(company).orderNumber("SHP-1")
                .status(ShipmentOrder.Status.BOOKED).build();
        when(shipmentRepo.findByOrderNumber("SHP-1")).thenReturn(Optional.of(shipment));

        service.processWebhookEvent("SHP-1", "IN_TRANSIT", "Le Havre", "En transit", "carrier-webhook");

        verify(eventRecorder).record(eq(shipment), eq(ShipmentEvent.EventType.STATUS_CHANGED),
                eq(Map.of("oldStatus", "BOOKED", "newStatus", "IN_TRANSIT", "dataSource", "LIVE")));
    }

    @Test
    @DisplayName("processWebhookEvent sans changement de statut résolu → aucun événement journalisé")
    void processWebhookEvent_unmappedStatus_doesNotRecord() {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID()).company(company).orderNumber("SHP-2")
                .status(ShipmentOrder.Status.BOOKED).build();
        when(shipmentRepo.findByOrderNumber("SHP-2")).thenReturn(Optional.of(shipment));

        service.processWebhookEvent("SHP-2", "UNKNOWN_STATUS", "Anvers", "?", "carrier-webhook");

        verifyNoInteractions(eventRecorder);
    }
}
