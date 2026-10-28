package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.model.Company;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Couvre uniquement assignVessel() -- le reste de ShipmentService n'avait
 *  aucun test avant cette passe, hors scope de cet ajout. */
@DisplayName("ShipmentService — assignVessel")
class ShipmentServiceVesselTest {

    private ShipmentOrderRepository shipmentRepo;
    private ShipmentService service;

    private Long companyId;
    private UUID shipmentId;
    private ShipmentOrder shipment;

    @BeforeEach
    void setUp() {
        shipmentRepo = mock(ShipmentOrderRepository.class);
        service = new ShipmentService(
                shipmentRepo, mock(TrackingEventRepository.class), mock(CarrierRepository.class),
                mock(ShippingRateRepository.class), mock(CompanyRepository.class), mock(AppUserRepository.class),
                mock(TruckRepository.class), mock(ShipmentEventPublisher.class), mock(ShipmentItemRepository.class),
                mock(ClientUserRepository.class));

        companyId = 7L;
        shipmentId = UUID.randomUUID();
        shipment = ShipmentOrder.builder().id(shipmentId).company(Company.builder().id(companyId).build()).build();

        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("MMSI valide (9 chiffres) → lié à l'expédition")
    void assignVessel_validMmsi_assigns() {
        ShipmentOrder result = service.assignVessel(shipmentId, "235012345", companyId);

        assertThat(result.getTrackedVesselMmsi()).isEqualTo("235012345");
    }

    @Test
    @DisplayName("espaces autour du MMSI → supprimés avant stockage")
    void assignVessel_trimsWhitespace() {
        ShipmentOrder result = service.assignVessel(shipmentId, "  235012345  ", companyId);

        assertThat(result.getTrackedVesselMmsi()).isEqualTo("235012345");
    }

    @Test
    @DisplayName("mmsi null → délie l'expédition")
    void assignVessel_nullMmsi_unassigns() {
        shipment.setTrackedVesselMmsi("235012345");

        ShipmentOrder result = service.assignVessel(shipmentId, null, companyId);

        assertThat(result.getTrackedVesselMmsi()).isNull();
    }

    @Test
    @DisplayName("mmsi vide/blanc → délie l'expédition")
    void assignVessel_blankMmsi_unassigns() {
        shipment.setTrackedVesselMmsi("235012345");

        ShipmentOrder result = service.assignVessel(shipmentId, "   ", companyId);

        assertThat(result.getTrackedVesselMmsi()).isNull();
    }

    @Test
    @DisplayName("MMSI mal formé (pas 9 chiffres) → rejeté explicitement")
    void assignVessel_malformedMmsi_rejected() {
        assertThatThrownBy(() -> service.assignVessel(shipmentId, "12345", companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MMSI invalide");
    }

    @Test
    @DisplayName("MMSI non numérique → rejeté explicitement")
    void assignVessel_nonNumericMmsi_rejected() {
        assertThatThrownBy(() -> service.assignVessel(shipmentId, "ABCDEFGHI", companyId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expédition d'une autre société → introuvable")
    void assignVessel_wrongCompany_notFound() {
        assertThatThrownBy(() -> service.assignVessel(shipmentId, "235012345", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
