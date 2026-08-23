package com.incokalk.service;

import com.incokalk.dto.shipment.ShippingRateDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ShippingRateService — Tests unitaires")
class ShippingRateServiceTest {

    @Mock ShippingRateRepository shippingRateRepo;
    @Mock CarrierRepository carrierRepo;
    @Mock CompanyRepository companyRepo;

    @InjectMocks ShippingRateService service;

    private UUID companyId, carrierId, rateId;
    private Company company;
    private Carrier carrier;
    private ShippingRate rate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        carrierId = UUID.randomUUID();
        rateId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").build();
        carrier = Carrier.builder().id(carrierId).company(company).name("Maersk").code("MAE").transportModes("SEA").isActive(true).build();
        rate = ShippingRate.builder()
                .id(rateId)
                .company(company)
                .carrier(carrier)
                .name("Tarif FR→DE")
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode("SEA")
                .baseRate(150.0)
                .ratePerKg(2.5)
                .ratePerCbm(40.0)
                .isActive(true)
                .build();
    }

    private ShippingRateDTO buildDto() {
        return ShippingRateDTO.builder()
                .carrierId(carrierId)
                .name("Tarif FR→DE")
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode("SEA")
                .baseRate(150.0)
                .ratePerKg(2.5)
                .ratePerCbm(40.0)
                .isActive(true)
                .build();
    }

    // ── listRates ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste des tarifs par entreprise")
    void listRates_byCompany() {
        when(shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(rate));

        List<ShippingRate> result = service.listRates(companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tarif FR→DE");
    }

    @Test
    @DisplayName("Liste paginée des tarifs par entreprise")
    void listRates_byCompanyPaged() {
        Page<ShippingRate> page = new PageImpl<>(List.of(rate));
        when(shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(eq(companyId), any(PageRequest.class))).thenReturn(page);

        Page<ShippingRate> result = service.listRates(companyId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Liste des tarifs filtrée par transporteur")
    void listRatesByCarrier() {
        when(shippingRateRepo.findByCarrier_IdAndCompany_IdOrderByCreatedAtDesc(carrierId, companyId)).thenReturn(List.of(rate));

        List<ShippingRate> result = service.listRatesByCarrier(carrierId, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrier().getId()).isEqualTo(carrierId);
    }

    // ── createRate ────────────────────────────────────────────────────

    @Test
    @DisplayName("Création de tarif réussie")
    void createRate_success() {
        ShippingRateDTO dto = buildDto();
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(shippingRateRepo.save(any(ShippingRate.class))).thenAnswer(i -> {
            ShippingRate r = i.getArgument(0);
            r.setId(rateId);
            return r;
        });

        ShippingRate result = service.createRate(dto, companyId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Tarif FR→DE");
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.getCarrier()).isEqualTo(carrier);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("Création de tarif avec transporteur introuvable → exception")
    void createRate_carrierNotFound_throws() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRate(buildDto(), companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transporteur non trouvé");
    }

    @Test
    @DisplayName("Création de tarif avec entreprise introuvable → exception")
    void createRate_companyNotFound_throws() {
        when(carrierRepo.findById(carrierId)).thenReturn(Optional.of(carrier));
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRate(buildDto(), companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entreprise non trouvée");
    }

    // ── updateRate ────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour partielle de tarif — champs null ignorés")
    void updateRate_partialUpdate() {
        ShippingRateDTO dto = ShippingRateDTO.builder()
                .carrierId(carrierId)
                .name("Nouveau nom")
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode("SEA")
                .baseRate(0)
                .build();
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.of(rate));
        when(shippingRateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ShippingRate result = service.updateRate(rateId, dto, companyId);

        assertThat(result.getName()).isEqualTo("Nouveau nom");
        assertThat(result.getBaseRate()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Mise à jour de tarif introuvable → exception")
    void updateRate_notFound_throws() {
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRate(rateId, buildDto(), companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tarif non trouvé");
    }

    // ── deleteRate ────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression de tarif réussie")
    void deleteRate_success() {
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.of(rate));

        service.deleteRate(rateId, companyId);

        verify(shippingRateRepo).delete(rate);
    }

    @Test
    @DisplayName("Suppression de tarif introuvable → exception")
    void deleteRate_notFound_throws() {
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRate(rateId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(shippingRateRepo, never()).delete(any());
    }

    // ── toggleActive ──────────────────────────────────────────────────

    @Test
    @DisplayName("Activation/désactivation de tarif")
    void toggleActive_flipsFlag() {
        rate.setActive(true);
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.of(rate));
        when(shippingRateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ShippingRate result = service.toggleActive(rateId, companyId);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("Toggle active sur tarif introuvable → exception")
    void toggleActive_notFound_throws() {
        when(shippingRateRepo.findById(rateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(rateId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findMatchingRates ─────────────────────────────────────────────

    @Test
    @DisplayName("Recherche de tarifs correspondants")
    void findMatchingRates() {
        when(shippingRateRepo.findMatchingRates(companyId, "FR", "DE", "SEA", 500.0, null))
                .thenReturn(List.of(rate));

        List<ShippingRate> result = service.findMatchingRates(companyId, "FR", "DE", "SEA", 500.0, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginCountry()).isEqualTo("FR");
    }
}
