package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CurrencyService — Tests unitaires")
class CurrencyServiceTest {

    private CurrencyService service;
    private CurrencyExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        exchangeService = mock(CurrencyExchangeService.class);
        when(exchangeService.getRates("EUR")).thenReturn(Map.of());
        service = new CurrencyService(exchangeService);
    }

    // ── toEur ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Conversion USD → EUR")
    void toEur_usd() {
        double result = service.toEur(100, "USD");
        assertThat(result).isEqualTo(92.2);
    }

    @Test
    @DisplayName("Conversion EUR → EUR (inchangé)")
    void toEur_eur() {
        assertThat(service.toEur(100, "EUR")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Conversion GBP → EUR")
    void toEur_gbp() {
        double result = service.toEur(100, "GBP");
        assertThat(result).isEqualTo(116.7);
    }

    @Test
    @DisplayName("Conversion CNY → EUR")
    void toEur_cny() {
        double result = service.toEur(100, "CNY");
        assertThat(result).isEqualTo(12.8);
    }

    @Test
    @DisplayName("Conversion devise inconnue → erreur claire (plus de 1:1 silencieux)")
    void toEur_unknown() {
        assertThatThrownBy(() -> service.toEur(100, "UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Devise non supportée");
    }

    @Test
    @DisplayName("Devise manquante → erreur")
    void toEur_nullCurrency() {
        assertThatThrownBy(() -> service.toEur(100, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Devise hors table statique résolue via taux vivants (1 EUR = 4.23 AED → 100 AED ≈ 23.64 EUR)")
    void toEur_liveRateFallback() {
        when(exchangeService.getRates("EUR")).thenReturn(Map.of("AED", 4.23));
        assertThat(service.toEur(100, "AED")).isCloseTo(23.64, within(0.01));
    }

    // ── fromEur ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Conversion EUR → USD")
    void fromEur_usd() {
        double result = service.fromEur(100, "USD");
        assertThat(result).isEqualTo(108.46);
    }

    @Test
    @DisplayName("Conversion EUR → EUR (inchangé)")
    void fromEur_eur() {
        assertThat(service.fromEur(100, "EUR")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Conversion EUR → devise inconnue → erreur")
    void fromEur_unknown() {
        assertThatThrownBy(() -> service.fromEur(100, "UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Devise non supportée");
    }

    // ── getAllRates ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Toutes les devises présentes dans le map")
    void getAllRates_containsAllCurrencies() {
        Map<String, Double> rates = service.getAllRates();

        assertThat(rates).containsKeys("EUR", "USD", "GBP", "CNY", "JPY", "MAD", "TRY");
        assertThat(rates.get("EUR")).isEqualTo(1.0);
        assertThat(rates.get("USD")).isEqualTo(0.922);
        assertThat(rates.get("GBP")).isEqualTo(1.167);
        assertThat(rates.get("CNY")).isEqualTo(0.128);
        assertThat(rates.get("JPY")).isEqualTo(0.0062);
        assertThat(rates.get("MAD")).isEqualTo(0.092);
        assertThat(rates.get("TRY")).isEqualTo(0.028);
    }
}
