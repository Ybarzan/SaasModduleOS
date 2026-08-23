package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CurrencyExchangeService — Tests unitaires")
class CurrencyExchangeServiceTest {

    RestTemplate restTemplate;
    CurrencyExchangeService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new CurrencyExchangeService(restTemplate);
    }

    @Test
    @DisplayName("convert → même devise → pas d'appel API")
    void convert_sameCurrency() {
        double result = service.convert(100, "EUR", "EUR");
        assertThat(result).isEqualTo(100);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("convert → devise différente → utilise taux")
    void convert_differentCurrency() {
        Map<String, Object> mockResponse = Map.of("rates", Map.of("USD", 1.09));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);
        double result = service.convert(100, "EUR", "USD");
        assertThat(result).isEqualTo(109.0);
    }

    @Test
    @DisplayName("convert → API fails → retourne montant original")
    void convert_apiFailure() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenThrow(new RuntimeException("API error"));
        double result = service.convert(100, "EUR", "USD");
        assertThat(result).isEqualTo(100);
    }

    @Test
    @DisplayName("getRate → même devise → 1.0")
    void getRate_same() {
        assertThat(service.getRate("EUR", "EUR")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getRate → taux trouvé")
    void getRate_found() {
        Map<String, Object> mockResponse = Map.of("rates", Map.of("USD", 1.09));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);
        assertThat(service.getRate("EUR", "USD")).isEqualTo(1.09);
    }

    @Test
    @DisplayName("getRate → taux non trouvé → 1.0")
    void getRate_notFound() {
        Map<String, Object> mockResponse = Map.of("rates", Map.of("USD", 1.09));
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);
        assertThat(service.getRate("EUR", "GBP")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getSupportedCurrencies → contient EUR et USD")
    void getSupportedCurrencies() {
        assertThat(service.getSupportedCurrencies()).contains("EUR", "USD", "GBP");
    }
}
