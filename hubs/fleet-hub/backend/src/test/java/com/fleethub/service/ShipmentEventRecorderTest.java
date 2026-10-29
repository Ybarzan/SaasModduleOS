package com.fleethub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Company;
import com.fleethub.model.ShipmentEvent;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.ShipmentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-0001, Phase 1 (double écriture) : record() ne doit jamais propager
 * d'exception -- un échec d'écriture dans shipment_events ne doit jamais
 * faire échouer ou annuler l'opération métier qui l'a déclenché.
 */
@DisplayName("ShipmentEventRecorder — record")
class ShipmentEventRecorderTest {

    private ShipmentEventRepository repo;
    private ShipmentEventRecorder recorder;

    private Company company;
    private ShipmentOrder shipment;

    @BeforeEach
    void setUp() {
        repo = mock(ShipmentEventRepository.class);
        recorder = new ShipmentEventRecorder(repo, new ObjectMapper());

        company = new Company();
        company.setId(7L);
        shipment = ShipmentOrder.builder().id(UUID.randomUUID()).company(company).build();

        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("événement valide → enregistré avec le payload sérialisé en JSON")
    void record_validEvent_savesWithSerializedPayload() {
        recorder.record(shipment, ShipmentEvent.EventType.VESSEL_ASSIGNED, Map.of("mmsi", "235012345"));

        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(repo).save(captor.capture());
        ShipmentEvent saved = captor.getValue();

        assertThat(saved.getShipment()).isEqualTo(shipment);
        assertThat(saved.getCompany()).isEqualTo(company);
        assertThat(saved.getEventType()).isEqualTo(ShipmentEvent.EventType.VESSEL_ASSIGNED);
        assertThat(saved.getPayload()).contains("\"mmsi\"", "\"235012345\"");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("payload null → sérialisé comme objet JSON vide, jamais d'exception")
    void record_nullPayload_savesEmptyJsonObject() {
        recorder.record(shipment, ShipmentEvent.EventType.SHIPMENT_CREATED, null);

        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("{}");
    }

    @Test
    @DisplayName("échec du repository (ex. DB en panne) → journalisé, jamais propagé")
    void record_repositorySaveFails_doesNotThrow() {
        when(repo.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> recorder.record(shipment, ShipmentEvent.EventType.STATUS_CHANGED, Map.of("x", "y")))
                .doesNotThrowAnyException();
    }
}
