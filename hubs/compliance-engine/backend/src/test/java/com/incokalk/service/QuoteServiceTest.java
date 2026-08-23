package com.incokalk.service;

import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.dto.shipment.SimulationRequest.TransportModeInput;
import com.incokalk.exception.ProviderException;
import com.incokalk.service.provider.CarrierProvider;
import com.incokalk.service.provider.CarrierProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("QuoteService — Tests unitaires")
class QuoteServiceTest {

    CarrierProviderRegistry providerRegistry;
    ProviderHealthService healthService;
    CurrencyExchangeService exchangeService;
    FreightRateService freightRateService;
    QuoteService service;

    UUID companyId;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(CarrierProviderRegistry.class);
        healthService = mock(ProviderHealthService.class);
        exchangeService = mock(CurrencyExchangeService.class);
        freightRateService = mock(FreightRateService.class);
        service = new QuoteService(providerRegistry, healthService, exchangeService, freightRateService);
        companyId = UUID.randomUUID();
    }

    // ── Aucun fournisseur actif → tarif interne de secours ─────────────────

    @Test
    @DisplayName("Aucun fournisseur actif → tarif interne de secours, mode valide, goodsValue fourni")
    void getQuotes_noActiveProviders_returnsFallbackQuote() {
        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("SEA")
                .weightKg(500.0).volumeM3(2.0).goodsValue(8000.0)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of());
        when(freightRateService.estimate(eq("CN"), eq("FR"), eq(TransportModeInput.SEA), eq(500.0), eq(2.0), eq(8000.0)))
                .thenReturn(new FreightRateService.FreightEstimate(300.0, 35));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        QuoteResponseDTO q = result.get(0);
        assertThat(q.getCarrierName()).isEqualTo("IncoKalk Standard");
        assertThat(q.getRateName()).isEqualTo("Tarif standard SEA");
        assertThat(q.getTransportMode()).isEqualTo("SEA");
        assertThat(q.getTotalCost()).isEqualTo(300.0);
        assertThat(q.getBaseRate()).isEqualTo(300.0);
        assertThat(q.getCurrency()).isEqualTo("EUR");
        assertThat(q.getTransitDaysMin()).isEqualTo(35);
        assertThat(q.getTransitDaysMax()).isEqualTo(35);
        assertThat(q.getProviderType()).isEqualTo("INCALK");
        assertThat(q.getProviderName()).isEqualTo("IncoKalk");

        verify(healthService, never()).isCircuitBroken(any(), any());
        verifyNoInteractions(exchangeService);
    }

    @Test
    @DisplayName("Aucun fournisseur actif, goodsValue absent → valeur par défaut 10000")
    void getQuotes_noActiveProviders_nullGoodsValue_usesDefault() {
        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("AIR")
                .weightKg(50.0).volumeM3(0.5).goodsValue(null)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of());
        when(freightRateService.estimate(eq("CN"), eq("FR"), eq(TransportModeInput.AIR), eq(50.0), eq(0.5), eq(10000.0)))
                .thenReturn(new FreightRateService.FreightEstimate(100.0, 7));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        QuoteResponseDTO q = result.get(0);
        assertThat(q.getTransportMode()).isEqualTo("AIR");
        assertThat(q.getTotalCost()).isEqualTo(100.0);

        verify(freightRateService).estimate(eq("CN"), eq("FR"), eq(TransportModeInput.AIR), eq(50.0), eq(0.5), eq(10000.0));
    }

    @Test
    @DisplayName("Mode de transport null → parseMode retourne null → FreightRateService.guess() prend le relais")
    void getQuotes_noActiveProviders_blankTransportMode_fallsBackToGuessedMode() {
        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("MA").destinationCountry("FR")
                .transportMode(null)
                .weightKg(50.0).volumeM3(0.5).goodsValue(1000.0)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of());
        when(freightRateService.guess("MA", 1000.0)).thenReturn(TransportModeInput.ROAD);
        when(freightRateService.estimate(eq("MA"), eq("FR"), eq(TransportModeInput.ROAD), eq(50.0), eq(0.5), eq(1000.0)))
                .thenReturn(new FreightRateService.FreightEstimate(50.0, 10));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransportMode()).isEqualTo("ROAD");
        verify(freightRateService).guess("MA", 1000.0);
    }

    @Test
    @DisplayName("Mode de transport invalide → catch IllegalArgumentException → FreightRateService.guess() prend le relais")
    void getQuotes_noActiveProviders_invalidTransportMode_fallsBackToGuessedMode() {
        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("NOT_A_MODE")
                .weightKg(50.0).volumeM3(0.5).goodsValue(60000.0)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of());
        when(freightRateService.guess("CN", 60000.0)).thenReturn(TransportModeInput.AIR);
        when(freightRateService.estimate(eq("CN"), eq("FR"), eq(TransportModeInput.AIR), eq(50.0), eq(0.5), eq(60000.0)))
                .thenReturn(new FreightRateService.FreightEstimate(500.0, 5));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransportMode()).isEqualTo("AIR");
        verify(freightRateService).guess("CN", 60000.0);
    }

    // ── Fournisseurs actifs ─────────────────────────────────────────────────

    @Test
    @DisplayName("Fournisseur actif réussit, pas de conversion (devise null) → tarifs directs")
    void getQuotes_allProvidersSucceed_noCurrencyRequested() throws Exception {
        CarrierProvider provider1 = mock(CarrierProvider.class);
        when(provider1.getProviderType()).thenReturn("PROV1");

        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("SEA")
                .weightKg(500.0).volumeM3(2.0).goodsValue(8000.0)
                .currency(null)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of(provider1));
        when(healthService.isCircuitBroken("PROV1", companyId)).thenReturn(false);

        QuoteResponseDTO quote1 = QuoteResponseDTO.builder()
                .carrierName("Carrier1").totalCost(200.0).currency("EUR").providerType("PROV1")
                .build();
        when(provider1.getRates(request, companyId)).thenReturn(List.of(quote1));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrierName()).isEqualTo("Carrier1");
        assertThat(result.get(0).getTotalCostConverted()).isNull();

        verify(healthService).recordSuccess("PROV1", companyId);
        verify(healthService, never()).recordFailure(any(), any());
        verifyNoInteractions(exchangeService);
        verifyNoInteractions(freightRateService);
    }

    @Test
    @DisplayName("Devise vide (blank) → conversion ignorée")
    void getQuotes_blankCurrency_skipsConversion() throws Exception {
        CarrierProvider provider1 = mock(CarrierProvider.class);
        when(provider1.getProviderType()).thenReturn("PROV1");

        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("SEA")
                .weightKg(500.0).volumeM3(2.0).goodsValue(8000.0)
                .currency("   ")
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of(provider1));
        when(healthService.isCircuitBroken("PROV1", companyId)).thenReturn(false);

        QuoteResponseDTO quote1 = QuoteResponseDTO.builder()
                .carrierName("Carrier1").totalCost(200.0).currency("EUR").providerType("PROV1")
                .build();
        when(provider1.getRates(request, companyId)).thenReturn(List.of(quote1));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCostConverted()).isNull();
        verifyNoInteractions(exchangeService);
    }

    @Test
    @DisplayName("Fournisseur avec circuit ouvert ignoré + fournisseur en échec → repli interne")
    void getQuotes_circuitBrokenSkipped_andFailingProvider_fallbackAdded() throws Exception {
        CarrierProvider provider1 = mock(CarrierProvider.class);
        when(provider1.getProviderType()).thenReturn("PROV1");
        CarrierProvider provider2 = mock(CarrierProvider.class);
        when(provider2.getProviderType()).thenReturn("PROV2");

        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("MA").destinationCountry("FR")
                .transportMode("ROAD")
                .weightKg(500.0).volumeM3(2.0).goodsValue(8000.0)
                .currency(null)
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of(provider1, provider2));
        when(healthService.isCircuitBroken("PROV1", companyId)).thenReturn(true);
        when(healthService.isCircuitBroken("PROV2", companyId)).thenReturn(false);
        when(provider2.getRates(request, companyId)).thenThrow(new ProviderException("PROV2", "boom"));

        when(freightRateService.estimate(eq("MA"), eq("FR"), eq(TransportModeInput.ROAD), eq(500.0), eq(2.0), eq(8000.0)))
                .thenReturn(new FreightRateService.FreightEstimate(95.0, 7));

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarrierName()).isEqualTo("IncoKalk Standard");

        verify(provider1, never()).getRates(any(), any());
        verify(healthService, never()).recordSuccess(eq("PROV1"), any());
        verify(healthService).recordFailure("PROV2", companyId);
        verify(healthService, never()).recordSuccess(eq("PROV2"), any());
    }

    @Test
    @DisplayName("Conversion de devise: tarif déjà dans la devise cible ignoré, tarif différent converti")
    void getQuotes_currencyConversion_mixedCurrencies() throws Exception {
        CarrierProvider provider1 = mock(CarrierProvider.class);
        when(provider1.getProviderType()).thenReturn("PROV1");

        QuoteRequestDTO request = QuoteRequestDTO.builder()
                .originCountry("CN").destinationCountry("FR")
                .transportMode("SEA")
                .weightKg(500.0).volumeM3(2.0).goodsValue(8000.0)
                .currency("eur")
                .build();

        when(providerRegistry.getAllAvailableProviders(companyId)).thenReturn(List.of(provider1));
        when(healthService.isCircuitBroken("PROV1", companyId)).thenReturn(false);

        QuoteResponseDTO quoteEur = QuoteResponseDTO.builder()
                .carrierName("CarrierEUR").totalCost(100.0).currency("EUR").providerType("PROV1")
                .build();
        QuoteResponseDTO quoteUsd = QuoteResponseDTO.builder()
                .carrierName("CarrierUSD").totalCost(200.0).currency("USD").providerType("PROV1")
                .build();
        when(provider1.getRates(request, companyId)).thenReturn(List.of(quoteEur, quoteUsd));

        when(exchangeService.convert(200.0, "USD", "EUR")).thenReturn(180.0);
        when(exchangeService.getRate("USD", "EUR")).thenReturn(0.9);

        List<QuoteResponseDTO> result = service.getQuotes(request, companyId);

        assertThat(result).hasSize(2);

        QuoteResponseDTO resultEur = result.stream().filter(q -> "CarrierEUR".equals(q.getCarrierName())).findFirst().orElseThrow();
        QuoteResponseDTO resultUsd = result.stream().filter(q -> "CarrierUSD".equals(q.getCarrierName())).findFirst().orElseThrow();

        assertThat(resultEur.getTotalCostConverted()).isNull();
        assertThat(resultEur.getDisplayCurrency()).isNull();

        assertThat(resultUsd.getTotalCostConverted()).isEqualTo(180.0);
        assertThat(resultUsd.getDisplayCurrency()).isEqualTo("EUR");
        assertThat(resultUsd.getConversionRate()).isEqualTo(0.9);

        verify(exchangeService).convert(200.0, "USD", "EUR");
        verify(exchangeService).getRate("USD", "EUR");
        verify(exchangeService, never()).convert(100.0, "EUR", "EUR");
    }
}
