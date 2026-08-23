package com.incokalk.service.provider;

import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.ShippingRate;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShippingRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InternalCarrierProvider — Tests unitaires")
class InternalCarrierProviderTest {

    ShippingRateRepository shippingRateRepo;
    CarrierRepository carrierRepo;
    InternalCarrierProvider provider;

    UUID companyId;

    @BeforeEach
    void setUp() {
        shippingRateRepo = mock(ShippingRateRepository.class);
        carrierRepo = mock(CarrierRepository.class);
        provider = new InternalCarrierProvider(shippingRateRepo, carrierRepo);
        companyId = UUID.randomUUID();
    }

    private QuoteRequestDTO request(String transportMode, Double weightKg, Double volumeM3) {
        return QuoteRequestDTO.builder()
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode(transportMode)
                .weightKg(weightKg)
                .volumeM3(volumeM3)
                .goodsValue(1000.0)
                .build();
    }

    private Carrier carrier(UUID id, String name, boolean active) {
        return Carrier.builder()
                .id(id)
                .name(name)
                .code("CODE-" + name)
                .logoUrl("https://example.com/" + name + ".png")
                .transportModes("AIR,SEA")
                .isActive(active)
                .build();
    }

    private ShippingRate rate(Carrier carrier, Double minWeightKg, Double maxWeightKg,
                               double baseRate, double ratePerKg, double ratePerCbm) {
        return ShippingRate.builder()
                .id(UUID.randomUUID())
                .carrier(carrier)
                .name("Tarif " + carrier.getName())
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode("AIR")
                .minWeightKg(minWeightKg)
                .maxWeightKg(maxWeightKg)
                .baseRate(baseRate)
                .ratePerKg(ratePerKg)
                .ratePerCbm(ratePerCbm)
                .currency("EUR")
                .transitDaysMin(2)
                .transitDaysMax(5)
                .co2EstimateKg(12.5)
                .isActive(true)
                .build();
    }

    // ---------- getProviderType / getName ----------

    @Test
    @DisplayName("getProviderType → INTERNAL")
    void getProviderType_returnsInternal() {
        assertThat(provider.getProviderType()).isEqualTo("INTERNAL");
    }

    @Test
    @DisplayName("getName → Tarifs internes")
    void getName_returnsTarifsInternes() {
        assertThat(provider.getName()).isEqualTo("Tarifs internes");
    }

    // ---------- isAvailable ----------

    @Test
    @DisplayName("isAvailable → toujours true")
    void isAvailable_alwaysTrue() {
        assertThat(provider.isAvailable(companyId)).isTrue();
        assertThat(provider.isAvailable(null)).isTrue();
    }

    // ---------- getHealth ----------

    @Test
    @DisplayName("getHealth → HEALTHY, actif, sans échec")
    void getHealth_returnsHealthyStatus() {
        ProviderHealthDTO health = provider.getHealth(companyId);

        assertThat(health.getProviderType()).isEqualTo("INTERNAL");
        assertThat(health.getHealthStatus()).isEqualTo("HEALTHY");
        assertThat(health.getConsecutiveFailures()).isEqualTo(0);
        assertThat(health.isActive()).isTrue();
        assertThat(health.getLastHealthCheck()).isNotNull();
    }

    // ---------- getRates — choix de la requête (transportMode) ----------

    @Test
    @DisplayName("getRates — transportMode fourni → recherche par mode de transport")
    void getRates_transportModeProvided_usesTransportModeQuery() {
        Carrier c = carrier(UUID.randomUUID(), "Kuehne", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
        verify(shippingRateRepo).findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR");
        verify(shippingRateRepo, never())
                .findByCompany_IdAndOriginCountryAndDestinationCountry(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("getRates — transportMode null → recherche par companyId")
    void getRates_transportModeNull_usesCompanyQuery() {
        Carrier c = carrier(UUID.randomUUID(), "Kuehne", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request(null, 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountry(companyId, "FR", "DE"))
                .thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
        verify(shippingRateRepo).findByCompany_IdAndOriginCountryAndDestinationCountry(companyId, "FR", "DE");
        verify(shippingRateRepo, never())
                .findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getRates — transportMode vide (blank) → recherche par companyId")
    void getRates_transportModeBlank_usesCompanyQuery() {
        Carrier c = carrier(UUID.randomUUID(), "Kuehne", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("   ", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountry(companyId, "FR", "DE"))
                .thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
        verify(shippingRateRepo).findByCompany_IdAndOriginCountryAndDestinationCountry(companyId, "FR", "DE");
        verify(shippingRateRepo, never())
                .findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(any(), any(), any(), any());
    }

    // ---------- getRates — filtrage transporteur ----------

    @Test
    @DisplayName("getRates — transporteur introuvable dans carrierMap → tarif exclu")
    void getRates_carrierNotFoundInMap_excludesRate() {
        Carrier c = carrier(UUID.randomUUID(), "Ghost", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        // carrierRepo returns nothing for this id → carrierMap will not contain it
        when(carrierRepo.findAllById(any())).thenReturn(List.of());

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRates — transporteur inactif → tarif exclu")
    void getRates_carrierInactive_excludesRate() {
        Carrier c = carrier(UUID.randomUUID(), "Inactive Co", false);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRates — transporteur actif et trouvé → tarif inclus")
    void getRates_carrierActiveAndFound_includesRate() {
        Carrier c = carrier(UUID.randomUUID(), "Active Co", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrierName()).isEqualTo("Active Co");
        assertThat(result.get(0).getCarrierId()).isEqualTo(c.getId());
        assertThat(result.get(0).getCarrierLogo()).isEqualTo(c.getLogoUrl());
        assertThat(result.get(0).getProviderType()).isEqualTo("INTERNAL");
        assertThat(result.get(0).getProviderName()).isEqualTo("Tarifs internes");
        assertThat(result.get(0).getProviderLogo()).isNull();
    }

    // ---------- getRates — filtrage poids min/max ----------

    @Test
    @DisplayName("getRates — minWeightKg null → pas de filtrage par le bas")
    void getRates_minWeightNull_notFiltered() {
        Carrier c = carrier(UUID.randomUUID(), "NoMin", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 0.5, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRates — poids inférieur au minimum → tarif exclu")
    void getRates_weightBelowMin_excludesRate() {
        Carrier c = carrier(UUID.randomUUID(), "MinCo", true);
        ShippingRate r = rate(c, 5.0, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 1.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRates — poids égal ou supérieur au minimum → tarif inclus")
    void getRates_weightAtOrAboveMin_includesRate() {
        Carrier c = carrier(UUID.randomUUID(), "MinCo", true);
        ShippingRate r = rate(c, 5.0, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 5.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRates — maxWeightKg null → pas de filtrage par le haut")
    void getRates_maxWeightNull_notFiltered() {
        Carrier c = carrier(UUID.randomUUID(), "NoMax", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 999.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRates — poids supérieur au maximum → tarif exclu")
    void getRates_weightAboveMax_excludesRate() {
        Carrier c = carrier(UUID.randomUUID(), "MaxCo", true);
        ShippingRate r = rate(c, null, 50.0, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 51.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRates — poids égal ou inférieur au maximum → tarif inclus")
    void getRates_weightAtOrBelowMax_includesRate() {
        Carrier c = carrier(UUID.randomUUID(), "MaxCo", true);
        ShippingRate r = rate(c, null, 50.0, 100.0, 2.0, 50.0);

        QuoteRequestDTO req = request("AIR", 50.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
    }

    // ---------- getRates — calcul du coût total et tri ----------

    @Test
    @DisplayName("getRates — calcul du coût total: baseRate + poids*ratePerKg + volume*ratePerCbm")
    void getRates_computesTotalCost() {
        Carrier c = carrier(UUID.randomUUID(), "CalcCo", true);
        ShippingRate r = rate(c, null, null, 100.0, 2.0, 10.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);
        // expected = 100 + (10*2) + (1*10) = 130.0

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(r));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCost()).isEqualTo(130.0);
        assertThat(result.get(0).getBaseRate()).isEqualTo(100.0);
        assertThat(result.get(0).getCurrency()).isEqualTo("EUR");
        assertThat(result.get(0).getTransitDaysMin()).isEqualTo(2);
        assertThat(result.get(0).getTransitDaysMax()).isEqualTo(5);
        assertThat(result.get(0).getCo2EstimateKg()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("getRates — plusieurs tarifs → triés par coût total croissant")
    void getRates_multipleRates_sortedByTotalCost() {
        Carrier c1 = carrier(UUID.randomUUID(), "Expensive", true);
        Carrier c2 = carrier(UUID.randomUUID(), "Cheap", true);
        ShippingRate expensive = rate(c1, null, null, 500.0, 5.0, 20.0);
        ShippingRate cheap = rate(c2, null, null, 10.0, 0.5, 1.0);

        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of(expensive, cheap));
        when(carrierRepo.findAllById(any())).thenReturn(List.of(c1, c2));

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCarrierName()).isEqualTo("Cheap");
        assertThat(result.get(1).getCarrierName()).isEqualTo("Expensive");
        assertThat(result.get(0).getTotalCost()).isLessThan(result.get(1).getTotalCost());
    }

    @Test
    @DisplayName("getRates — aucun tarif trouvé → liste vide")
    void getRates_noRatesFound_returnsEmptyList() {
        QuoteRequestDTO req = request("AIR", 10.0, 1.0);

        when(shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                companyId, "FR", "DE", "AIR")).thenReturn(List.of());
        when(carrierRepo.findAllById(any())).thenReturn(List.of());

        List<QuoteResponseDTO> result = provider.getRates(req, companyId);

        assertThat(result).isEmpty();
        verify(carrierRepo, times(1)).findAllById(eq(List.of()));
    }
}
