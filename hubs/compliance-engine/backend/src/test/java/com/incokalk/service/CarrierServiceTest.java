package com.incokalk.service;

import com.incokalk.dto.shipment.CarrierDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CarrierService — Tests unitaires")
class CarrierServiceTest {

    @Mock CarrierRepository carrierRepo;
    @Mock CompanyRepository companyRepo;
    @Mock ShipmentOrderRepository shipmentOrderRepo;

    @InjectMocks CarrierService service;

    private UUID companyId;
    private UUID carrierId;
    private Company company;
    private Carrier carrier;
    private CarrierDTO dto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        carrierId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        carrier = Carrier.builder()
                .id(carrierId)
                .company(company)
                .name("Maersk")
                .code("MAE")
                .transportModes("SEA")
                .isActive(true)
                .build();
        dto = CarrierDTO.builder()
                .name("Maersk")
                .code("MAE")
                .transportModes("SEA")
                .isActive(true)
                .build();
    }

    // ── createCarrier ────────────────────────────────────────────────────

    @Test
    @DisplayName("Création transporteur réussie")
    void createCarrier_success() {
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, "MAE")).thenReturn(false);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(carrierRepo.save(any(Carrier.class))).thenAnswer(i -> {
            Carrier c = i.getArgument(0);
            c.setId(carrierId);
            return c;
        });

        Carrier result = service.createCarrier(dto, companyId);

        assertThat(result.getName()).isEqualTo("Maersk");
        assertThat(result.getCode()).isEqualTo("MAE");
        assertThat(result.getCompany()).isEqualTo(company);
        verify(carrierRepo).save(any(Carrier.class));
    }

    @Test
    @DisplayName("Création transporteur avec code dupliqué → exception")
    void createCarrier_duplicateCode_throws() {
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, "MAE")).thenReturn(true);

        assertThatThrownBy(() -> service.createCarrier(dto, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");

        verify(carrierRepo, never()).save(any());
    }

    @Test
    @DisplayName("Création transporteur avec entreprise introuvable → exception")
    void createCarrier_companyNotFound_throws() {
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, "MAE")).thenReturn(false);
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCarrier(dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entreprise non trouvée");
    }

    // ── updateCarrier ────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour transporteur réussie")
    void updateCarrier_success() {
        CarrierDTO updateDto = CarrierDTO.builder()
                .name("Maersk Updated")
                .code("MAE")
                .transportModes("SEA,AIR")
                .build();

        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(carrierRepo.save(any(Carrier.class))).thenAnswer(i -> i.getArgument(0));

        Carrier result = service.updateCarrier(carrierId, updateDto, companyId);

        assertThat(result.getName()).isEqualTo("Maersk Updated");
        assertThat(result.getTransportModes()).isEqualTo("SEA,AIR");
        verify(carrierRepo).save(any(Carrier.class));
    }

    @Test
    @DisplayName("Mise à jour transporteur introuvable → exception")
    void updateCarrier_notFound_throws() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCarrier(carrierId, dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transporteur non trouvé");
    }

    @Test
    @DisplayName("Mise à jour transporteur d'une autre entreprise → exception")
    void updateCarrier_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));

        assertThatThrownBy(() -> service.updateCarrier(carrierId, dto, otherCompanyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transporteur non trouvé");
    }

    @Test
    @DisplayName("Mise à jour avec code dupliqué → exception")
    void updateCarrier_duplicateCode_throws() {
        CarrierDTO updateDto = CarrierDTO.builder()
                .name("Maersk")
                .code("NEWCODE")
                .transportModes("SEA")
                .build();

        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, "NEWCODE")).thenReturn(true);

        assertThatThrownBy(() -> service.updateCarrier(carrierId, updateDto, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("Mise à jour avec même code → pas de vérification doublon")
    void updateCarrier_sameCode_noDuplicateCheck() {
        CarrierDTO updateDto = CarrierDTO.builder()
                .name("Maersk v2")
                .code("MAE")
                .transportModes("SEA,AIR")
                .build();

        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(carrierRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Carrier result = service.updateCarrier(carrierId, updateDto, companyId);

        assertThat(result.getName()).isEqualTo("Maersk v2");
        verify(carrierRepo, never()).existsByCompanyIdAndCodeIgnoreCase(any(), any());
    }

    // ── deleteCarrier ────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression transporteur réussie")
    void deleteCarrier_success() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(shipmentOrderRepo.countByCarrier_Id(carrierId)).thenReturn(0L);

        service.deleteCarrier(carrierId, companyId);

        verify(carrierRepo).delete(carrier);
    }

    @Test
    @DisplayName("Suppression transporteur introuvable → exception")
    void deleteCarrier_notFound_throws() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCarrier(carrierId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(carrierRepo, never()).delete(any());
    }

    // ── toggleActive ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Bascule actif → inactif puis inactif → actif")
    void toggleActive_toggles() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(carrierRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Carrier toggled1 = service.toggleActive(carrierId, companyId);
        assertThat(toggled1.isActive()).isFalse();

        Carrier toggled2 = service.toggleActive(carrierId, companyId);
        assertThat(toggled2.isActive()).isTrue();
    }

    @Test
    @DisplayName("Bascule transporteur introuvable → exception")
    void toggleActive_notFound_throws() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(carrierId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── listCarriers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste des transporteurs filtrée par entreprise")
    void listCarriers_filteredByCompany() {
        Carrier c2 = Carrier.builder().id(UUID.randomUUID()).company(company).name("DHL").code("DHL").build();
        when(carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(carrier, c2));

        List<Carrier> result = service.listCarriers(companyId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Carrier::getName).containsExactly("Maersk", "DHL");
    }
}
