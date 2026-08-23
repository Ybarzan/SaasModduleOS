package com.incokalk.service.insurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.financial.CargoInsuranceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CargoInsurerClient — Tests unitaires")
class CargoInsurerClientTest {

    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    CargoInsurerClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new CargoInsurerClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.insurer.example.com");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    private CargoInsuranceRequest request() {
        CargoInsuranceRequest req = new CargoInsuranceRequest();
        req.setGoodsValue(1000.0);
        req.setWeightKg(10.0);
        req.setTransportMode("AIR");
        req.setGoodsCategory("ELECTRONICS");
        req.setOriginCountry("FR");
        req.setDestinationCountry("DE");
        return req;
    }

    @SuppressWarnings("unchecked")
    private void mockHttpResponse(String body, HttpStatus status) {
        ResponseEntity<String> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }

    // ---------- isConfigured ----------

    @Test
    @DisplayName("isConfigured — apiKey null → false")
    void isConfigured_apiKeyNull_false() {
        ReflectionTestUtils.setField(client, "apiKey", null);
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured — apiKey vide → false")
    void isConfigured_apiKeyBlank_false() {
        ReflectionTestUtils.setField(client, "apiKey", "   ");
        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured — apiKey présente → true")
    void isConfigured_apiKeyPresent_true() {
        ReflectionTestUtils.setField(client, "apiKey", "some-key");
        assertThat(client.isConfigured()).isTrue();
    }

    // ---------- fetchMarketRateFactor — non configuré ----------

    @Test
    @DisplayName("fetchMarketRateFactor — non configuré (apiKey null) → Optional.empty, pas d'appel HTTP")
    void fetchMarketRateFactor_notConfigured_returnsEmpty_noHttpCall() {
        ReflectionTestUtils.setField(client, "apiKey", null);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("fetchMarketRateFactor — non configuré (apiKey vide) → Optional.empty, pas d'appel HTTP")
    void fetchMarketRateFactor_apiKeyBlank_returnsEmpty_noHttpCall() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    // ---------- fetchMarketRateFactor — succès ----------

    @Test
    @DisplayName("fetchMarketRateFactor — succès, marketFactor positif → Optional.of(factor)")
    void fetchMarketRateFactor_success_returnsFactor() {
        mockHttpResponse("{\"marketFactor\": 1.25}", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).contains(1.25);
    }

    @Test
    @DisplayName("fetchMarketRateFactor — marketFactor = 0 → Optional.empty")
    void fetchMarketRateFactor_factorZero_returnsEmpty() {
        mockHttpResponse("{\"marketFactor\": 0}", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketRateFactor — marketFactor négatif → Optional.empty")
    void fetchMarketRateFactor_factorNegative_returnsEmpty() {
        mockHttpResponse("{\"marketFactor\": -0.5}", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketRateFactor — champ marketFactor absent → Optional.empty")
    void fetchMarketRateFactor_missingMarketFactorField_returnsEmpty() {
        mockHttpResponse("{\"someOtherField\": 42}", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketRateFactor — marketFactor non numérique → Optional.empty")
    void fetchMarketRateFactor_marketFactorNotNumber_returnsEmpty() {
        mockHttpResponse("{\"marketFactor\": \"high\"}", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    // ---------- fetchMarketRateFactor — réponses inattendues ----------

    @Test
    @DisplayName("fetchMarketRateFactor — statut non-2xx → Optional.empty")
    void fetchMarketRateFactor_nonSuccessStatus_returnsEmpty() {
        mockHttpResponse("{\"marketFactor\": 1.1}", HttpStatus.BAD_REQUEST);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketRateFactor — corps de réponse null malgré statut OK → Optional.empty")
    void fetchMarketRateFactor_nullBody_returnsEmpty() {
        mockHttpResponse(null, HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    // ---------- fetchMarketRateFactor — exceptions ----------

    @Test
    @DisplayName("fetchMarketRateFactor — exception réseau → Optional.empty (catch générique)")
    void fetchMarketRateFactor_networkException_returnsEmpty() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketRateFactor — JSON malformé → parsing échoue, Optional.empty")
    void fetchMarketRateFactor_malformedJson_returnsEmpty() {
        mockHttpResponse("{not-valid-json!!", HttpStatus.OK);

        Optional<Double> result = client.fetchMarketRateFactor(request());

        assertThat(result).isEmpty();
    }

    // ---------- fetchMarketRateFactor — URL / baseUrl ----------

    @Test
    @DisplayName("fetchMarketRateFactor — baseUrl avec slash final → slash retiré dans l'URL appelée")
    void fetchMarketRateFactor_baseUrlWithTrailingSlash_trimsSlash() {
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.insurer.example.com/");
        mockHttpResponse("{\"marketFactor\": 1.0}", HttpStatus.OK);

        client.fetchMarketRateFactor(request());

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getValue()).isEqualTo("https://api.insurer.example.com/v1/quotes/market-factor");
    }

    @Test
    @DisplayName("fetchMarketRateFactor — baseUrl sans slash final → URL inchangée")
    void fetchMarketRateFactor_baseUrlWithoutTrailingSlash_unchanged() {
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.insurer.example.com");
        mockHttpResponse("{\"marketFactor\": 1.0}", HttpStatus.OK);

        client.fetchMarketRateFactor(request());

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getValue()).isEqualTo("https://api.insurer.example.com/v1/quotes/market-factor");
    }

    // ---------- fetchMarketRateFactor — corps de la requête ----------

    @Test
    @DisplayName("fetchMarketRateFactor — corps de requête contient les champs attendus")
    void fetchMarketRateFactor_requestBody_containsExpectedFields() {
        mockHttpResponse("{\"marketFactor\": 1.0}", HttpStatus.OK);

        client.fetchMarketRateFactor(request());

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body)
                .containsEntry("goodsValue", 1000.0)
                .containsEntry("weightKg", 10.0)
                .containsEntry("transportMode", "AIR")
                .containsEntry("goodsCategory", "ELECTRONICS")
                .containsEntry("originCountry", "FR")
                .containsEntry("destinationCountry", "DE");
    }
}
