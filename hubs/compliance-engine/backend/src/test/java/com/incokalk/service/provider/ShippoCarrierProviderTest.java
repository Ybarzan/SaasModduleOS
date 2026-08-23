package com.incokalk.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.exception.ProviderException;
import com.incokalk.model.ProviderConfig;
import com.incokalk.model.ProviderRateCache;
import com.incokalk.repository.ProviderConfigRepository;
import com.incokalk.repository.ProviderRateCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ShippoCarrierProvider — Tests unitaires")
class ShippoCarrierProviderTest {

    ProviderConfigRepository providerConfigRepo;
    ProviderRateCacheRepository rateCacheRepo;
    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    ShippoCarrierProvider provider;

    UUID companyId;

    @BeforeEach
    void setUp() {
        providerConfigRepo = mock(ProviderConfigRepository.class);
        rateCacheRepo = mock(ProviderRateCacheRepository.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        provider = new ShippoCarrierProvider(providerConfigRepo, rateCacheRepo, restTemplate, objectMapper);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.goshippo.com");
        ReflectionTestUtils.setField(provider, "cacheTtlHours", 1);
        companyId = UUID.randomUUID();
    }

    private ProviderConfig activeConfig() {
        return ProviderConfig.builder()
                .id(UUID.randomUUID())
                .providerType("SHIPPO")
                .apiKeyEncrypted("api-key")
                .isActive(true)
                .healthStatus("HEALTHY")
                .consecutiveFailures(0)
                .build();
    }

    private QuoteRequestDTO request() {
        return QuoteRequestDTO.builder()
                .originCountry("FR")
                .destinationCountry("DE")
                .transportMode("AIR")
                .weightKg(10.0)
                .volumeM3(0.5)
                .goodsValue(1000.0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void mockHttpResponse(String body, HttpStatus status) {
        ResponseEntity<String> response = new ResponseEntity<>(body, status);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }

    // ---------- getProviderType / getName / getLogoUrl ----------

    @Test
    @DisplayName("getProviderType → SHIPPO")
    void getProviderType_returnsShippo() {
        assertThat(provider.getProviderType()).isEqualTo("SHIPPO");
    }

    @Test
    @DisplayName("getName → Shippo")
    void getName_returnsShippo() {
        assertThat(provider.getName()).isEqualTo("Shippo");
    }

    @Test
    @DisplayName("getLogoUrl → URL du logo Shippo")
    void getLogoUrl_returnsLogoUrl() {
        assertThat(provider.getLogoUrl()).contains("goshippo.com");
    }

    // ---------- isAvailable ----------

    @Test
    @DisplayName("isAvailable — config active → true")
    void isAvailable_configActive_true() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(activeConfig()));
        assertThat(provider.isAvailable(companyId)).isTrue();
    }

    @Test
    @DisplayName("isAvailable — config inactive → false")
    void isAvailable_configInactive_false() {
        ProviderConfig config = activeConfig();
        config.setActive(false);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        assertThat(provider.isAvailable(companyId)).isFalse();
    }

    @Test
    @DisplayName("isAvailable — config absente → false")
    void isAvailable_configAbsent_false() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.empty());
        assertThat(provider.isAvailable(companyId)).isFalse();
    }

    // ---------- getHealth ----------

    @Test
    @DisplayName("getHealth — config présente → mappe les champs")
    void getHealth_configPresent_mapsFields() {
        ProviderConfig config = activeConfig();
        config.setHealthStatus("DEGRADED");
        config.setLastHealthCheck(LocalDateTime.now());
        config.setConsecutiveFailures(3);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));

        ProviderHealthDTO health = provider.getHealth(companyId);

        assertThat(health.getProviderType()).isEqualTo("SHIPPO");
        assertThat(health.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(health.getConsecutiveFailures()).isEqualTo(3);
        assertThat(health.isActive()).isTrue();
    }

    @Test
    @DisplayName("getHealth — config absente → UNKNOWN, inactif")
    void getHealth_configAbsent_returnsUnknown() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.empty());

        ProviderHealthDTO health = provider.getHealth(companyId);

        assertThat(health.getProviderType()).isEqualTo("SHIPPO");
        assertThat(health.getHealthStatus()).isEqualTo("UNKNOWN");
        assertThat(health.isActive()).isFalse();
    }

    // ---------- getRates — config errors ----------

    @Test
    @DisplayName("getRates — config absente → ProviderException")
    void getRates_configNotFound_throwsProviderException() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Configuration Shippo non trouvée");

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("getRates — config inactive → ProviderException")
    void getRates_configInactive_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setActive(false);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("désactivé");
    }

    // ---------- getRates — cache ----------

    @Test
    @DisplayName("getRates — cache hit → retourne les tarifs du cache sans appel HTTP")
    void getRates_cacheHit_returnsCachedRates_noHttpCall() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));

        String cachedJson = """
                {"results":[{"provider":{"display_name":"UPS","logo_url":"https://x/logo.png"},
                "servicelevel":{"name":"Express Saver"},"terms":"express","amount":"42.50","currency":"EUR",
                "estimated_days":2}]}
                """;
        ProviderRateCache cacheEntry = ProviderRateCache.builder()
                .providerType("SHIPPO")
                .cacheKey("whatever")
                .responseJson(cachedJson)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(cacheEntry));

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getCarrierName()).isEqualTo("UPS");
        assertThat(rates.get(0).getTotalCost()).isEqualTo(42.50);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        verify(rateCacheRepo, never()).save(any());
    }

    // ---------- getRates — API key ----------

    @Test
    @DisplayName("getRates — clé API manquante → ProviderException")
    void getRates_apiKeyNull_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setApiKeyEncrypted(null);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Clé API Shippo non configurée");
    }

    @Test
    @DisplayName("getRates — clé API vide → ProviderException")
    void getRates_apiKeyBlank_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setApiKeyEncrypted("   ");
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Clé API Shippo non configurée");
    }

    // ---------- getRates — success path ----------

    @Test
    @DisplayName("getRates — succès, terms express → mode AIR, tarifs retournés et mis en cache")
    void getRates_success_express_savesCacheAndReturnsRates() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"results":[{"provider":{"display_name":"FedEx","logo_url":"https://x/fedex.png"},
                "servicelevel":{"name":"Priority Express"},"terms":"express","amount":"120.5","currency":"EUR",
                "estimated_days":1}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        QuoteResponseDTO rate = rates.get(0);
        assertThat(rate.getTransportMode()).isEqualTo("AIR");
        assertThat(rate.getTotalCost()).isEqualTo(120.5);
        assertThat(rate.getCurrency()).isEqualTo("EUR");
        assertThat(rate.getTransitDaysMin()).isEqualTo(1);
        assertThat(rate.getTransitDaysMax()).isEqualTo(3);
        assertThat(rate.getProviderType()).isEqualTo("SHIPPO");
        assertThat(rate.getProviderName()).isEqualTo("Shippo");
        assertThat(rate.getCarrierName()).isEqualTo("FedEx");

        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    @Test
    @DisplayName("getRates — succès, terms ocean freight → mode SEA")
    void getRates_success_ocean_returnsSeaMode() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"results":[{"provider":{"display_name":"Maersk"},
                "servicelevel":{"name":"Ocean Freight"},"terms":"ocean freight","amount":"80.0","currency":"USD",
                "estimated_days":20}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        QuoteResponseDTO rate = rates.get(0);
        assertThat(rate.getTransportMode()).isEqualTo("SEA");
        assertThat(rate.getCurrency()).isEqualTo("USD");
        assertThat(rate.getTransitDaysMin()).isEqualTo(20);
        assertThat(rate.getTransitDaysMax()).isEqualTo(22);
    }

    @Test
    @DisplayName("getRates — succès, terms inconnus → mode ROAD par défaut")
    void getRates_success_unknownTerms_returnsRoadMode() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"results":[{"provider":{"display_name":"DPD"},
                "servicelevel":{"name":"Standard"},"terms":"standard_ground","amount":"25.0","currency":"EUR",
                "estimated_days":3}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getTransportMode()).isEqualTo("ROAD");
    }

    @Test
    @DisplayName("getRates — succès, plusieurs résultats triés par coût total croissant")
    void getRates_success_multipleResults_sortedByCost() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"results":[
                  {"provider":{"display_name":"UPS"},"servicelevel":{"name":"Express"},"terms":"express",
                   "amount":"200.0","currency":"EUR","estimated_days":1},
                  {"provider":{"display_name":"DHL"},"servicelevel":{"name":"Ground"},"terms":"ground",
                   "amount":"50.0","currency":"EUR","estimated_days":5}
                ]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(2);
        assertThat(rates.get(0).getTotalCost()).isEqualTo(50.0);
        assertThat(rates.get(1).getTotalCost()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("getRates — résultat sans amount/currency/provider → valeurs par défaut")
    void getRates_success_missingFields_usesDefaults() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"results":[{}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        QuoteResponseDTO rate = rates.get(0);
        assertThat(rate.getBaseRate()).isEqualTo(0);
        assertThat(rate.getTotalCost()).isEqualTo(0);
        assertThat(rate.getCurrency()).isEqualTo("USD");
        assertThat(rate.getCarrierName()).isEqualTo("Shippo");
        assertThat(rate.getCarrierLogo()).isEqualTo(provider.getLogoUrl());
        assertThat(rate.getTransportMode()).isEqualTo("ROAD");
        assertThat(rate.getTransitDaysMin()).isEqualTo(0);
        assertThat(rate.getTransitDaysMax()).isEqualTo(2);
    }

    @Test
    @DisplayName("getRates — pas de champ results → liste vide, mise en cache quand même")
    void getRates_success_noResultsField_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{}", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    @Test
    @DisplayName("getRates — results n'est pas un tableau → liste vide")
    void getRates_success_resultsNotArray_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{\"results\":\"not-an-array\"}", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
    }

    @Test
    @DisplayName("getRates — JSON malformé → parsing échoue silencieusement, liste vide")
    void getRates_success_malformedJson_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{not-valid-json!!", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    // ---------- getRates — réponse inattendue ----------

    @Test
    @DisplayName("getRates — statut non-OK → ProviderException")
    void getRates_responseNotOk_throwsProviderException() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{\"results\":[]}", HttpStatus.ACCEPTED);

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Réponse inattendue");
        verify(rateCacheRepo, never()).save(any());
    }

    @Test
    @DisplayName("getRates — corps de réponse null malgré statut OK → ProviderException")
    void getRates_responseBodyNull_throwsProviderException() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse(null, HttpStatus.OK);

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Réponse inattendue");
    }

    // ---------- getRates — erreurs HTTP ----------

    @Test
    @DisplayName("getRates — HttpClientErrorException 401 → clé invalide, statut DEGRADED")
    void getRates_httpClientError401_marksDegraded_throwsInvalidCredentials() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Clé API Shippo invalide");

        verify(providerConfigRepo, times(1)).save(any(ProviderConfig.class));
        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(config.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRates — HttpClientErrorException autre que 401 → erreur API générique, statut DEGRADED")
    void getRates_httpClientErrorOther_marksDegraded_throwsGenericApiError() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Erreur API Shippo");

        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("getRates — HttpClientErrorException, config absente au moment du markProviderStatus → pas de sauvegarde")
    void getRates_httpClientError_configAbsentDuringMarkStatus_noSave() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config))
                .thenReturn(Optional.empty());
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class);

        verify(providerConfigRepo, never()).save(any(ProviderConfig.class));
    }

    @Test
    @DisplayName("getRates — exception réseau générique → erreur générique, statut DEGRADED")
    void getRates_genericException_marksDegraded_throwsGenericError() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Erreur lors de l'appel à Shippo");

        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(config.getConsecutiveFailures()).isEqualTo(1);
    }

    // ---------- getRates — construction du corps de requête ----------

    @Test
    @DisplayName("getRates — weightKg null → poids par défaut 1000g dans le corps de la requête")
    void getRates_nullWeightKg_usesDefaultWeightInBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"results\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setWeightKg(null);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parcels = (List<Map<String, Object>>) body.get("parcels");
        assertThat(parcels.get(0).get("weight")).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("getRates — weightKg présent → converti en grammes dans le corps")
    void getRates_weightPresent_convertsToGramsInBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"results\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setWeightKg(2.5);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parcels = (List<Map<String, Object>>) body.get("parcels");
        assertThat(parcels.get(0).get("weight")).isEqualTo(2500.0);
    }

    @Test
    @DisplayName("getRates — volumeM3 présent → dimensions calculées et ajoutées au corps")
    void getRates_volumePresent_addsDimensionsToBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"results\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setVolumeM3(1.0);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parcels = (List<Map<String, Object>>) body.get("parcels");
        assertThat(parcels.get(0)).containsKeys("length", "width", "height");
    }

    @Test
    @DisplayName("getRates — volumeM3 null → pas de dimensions dans le corps")
    void getRates_volumeNull_noDimensionsInBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"results\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setVolumeM3(null);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parcels = (List<Map<String, Object>>) body.get("parcels");
        assertThat(parcels.get(0)).doesNotContainKeys("length", "width", "height");
    }

    @Test
    @DisplayName("getRates — corps de requête contient bien les pays d'origine et de destination")
    void getRates_body_containsAddressesFromRequest() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"results\":[]}", HttpStatus.OK);

        provider.getRates(request(), companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> addressFrom = (Map<String, Object>) body.get("address_from");
        @SuppressWarnings("unchecked")
        Map<String, Object> addressTo = (Map<String, Object>) body.get("address_to");
        assertThat(addressFrom.get("country")).isEqualTo("FR");
        assertThat(addressTo.get("country")).isEqualTo("DE");

        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("Authorization")).isEqualTo("ShippoToken api-key");
    }

    // ---------- mapShippoMode (méthode privée, testée par réflexion) ----------

    private String invokeMapShippoMode(String terms) throws Exception {
        Method m = ShippoCarrierProvider.class.getDeclaredMethod("mapShippoMode", String.class);
        m.setAccessible(true);
        return (String) m.invoke(provider, terms);
    }

    @Test
    @DisplayName("mapShippoMode — terms null → ROAD")
    void mapShippoMode_null_returnsRoad() throws Exception {
        assertThat(invokeMapShippoMode(null)).isEqualTo("ROAD");
    }

    @Test
    @DisplayName("mapShippoMode — contient express → AIR")
    void mapShippoMode_express_returnsAir() throws Exception {
        assertThat(invokeMapShippoMode("EXPRESS")).isEqualTo("AIR");
    }

    @Test
    @DisplayName("mapShippoMode — contient priority (sans express) → AIR")
    void mapShippoMode_priority_returnsAir() throws Exception {
        assertThat(invokeMapShippoMode("Priority Mail")).isEqualTo("AIR");
    }

    @Test
    @DisplayName("mapShippoMode — contient sea → SEA")
    void mapShippoMode_sea_returnsSea() throws Exception {
        assertThat(invokeMapShippoMode("Sea Freight")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapShippoMode — contient ocean (sans sea/freight) → SEA")
    void mapShippoMode_ocean_returnsSea() throws Exception {
        assertThat(invokeMapShippoMode("Ocean Standard")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapShippoMode — contient freight (sans sea/ocean) → SEA")
    void mapShippoMode_freight_returnsSea() throws Exception {
        assertThat(invokeMapShippoMode("Air Freight Cargo")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapShippoMode — terms inconnu → ROAD par défaut")
    void mapShippoMode_unknown_returnsRoadDefault() throws Exception {
        assertThat(invokeMapShippoMode("ground")).isEqualTo("ROAD");
    }

    // ---------- buildCacheKey (méthode privée, testée par réflexion) ----------

    @Test
    @DisplayName("buildCacheKey — transportMode fourni → utilisé dans la clé")
    void buildCacheKey_withTransportMode() throws Exception {
        Method m = ShippoCarrierProvider.class.getDeclaredMethod("buildCacheKey", QuoteRequestDTO.class);
        m.setAccessible(true);
        String key = (String) m.invoke(provider, request());
        assertThat(key).startsWith("SHIPPO:").contains("AIR");
    }

    @Test
    @DisplayName("buildCacheKey — transportMode null → ANY dans la clé")
    void buildCacheKey_nullTransportMode_usesAny() throws Exception {
        Method m = ShippoCarrierProvider.class.getDeclaredMethod("buildCacheKey", QuoteRequestDTO.class);
        m.setAccessible(true);
        QuoteRequestDTO req = request();
        req.setTransportMode(null);
        String key = (String) m.invoke(provider, req);
        assertThat(key).contains("ANY");
    }
}
