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

@DisplayName("DHLCarrierProvider — Tests unitaires")
class DHLCarrierProviderTest {

    ProviderConfigRepository providerConfigRepo;
    ProviderRateCacheRepository rateCacheRepo;
    RestTemplate restTemplate;
    ObjectMapper objectMapper;
    DHLCarrierProvider provider;

    UUID companyId;

    @BeforeEach
    void setUp() {
        providerConfigRepo = mock(ProviderConfigRepository.class);
        rateCacheRepo = mock(ProviderRateCacheRepository.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        provider = new DHLCarrierProvider(providerConfigRepo, rateCacheRepo, restTemplate, objectMapper);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api-eu.dhl.com/mydhlapi");
        ReflectionTestUtils.setField(provider, "cacheTtlHours", 1);
        companyId = UUID.randomUUID();
    }

    private ProviderConfig activeConfig() {
        return ProviderConfig.builder()
                .id(UUID.randomUUID())
                .providerType("DHL")
                .apiKeyEncrypted("api-key")
                .apiSecret("api-secret")
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
    @DisplayName("getProviderType → DHL")
    void getProviderType_returnsDHL() {
        assertThat(provider.getProviderType()).isEqualTo("DHL");
    }

    @Test
    @DisplayName("getName → DHL Express")
    void getName_returnsDhlExpress() {
        assertThat(provider.getName()).isEqualTo("DHL Express");
    }

    @Test
    @DisplayName("getLogoUrl → URL du logo DHL")
    void getLogoUrl_returnsLogoUrl() {
        assertThat(provider.getLogoUrl()).contains("dhl.com");
    }

    // ---------- isAvailable ----------

    @Test
    @DisplayName("isAvailable — config active → true")
    void isAvailable_configActive_true() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(activeConfig()));
        assertThat(provider.isAvailable(companyId)).isTrue();
    }

    @Test
    @DisplayName("isAvailable — config inactive → false")
    void isAvailable_configInactive_false() {
        ProviderConfig config = activeConfig();
        config.setActive(false);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        assertThat(provider.isAvailable(companyId)).isFalse();
    }

    @Test
    @DisplayName("isAvailable — config absente → false")
    void isAvailable_configAbsent_false() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
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
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));

        ProviderHealthDTO health = provider.getHealth(companyId);

        assertThat(health.getProviderType()).isEqualTo("DHL");
        assertThat(health.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(health.getConsecutiveFailures()).isEqualTo(3);
        assertThat(health.isActive()).isTrue();
    }

    @Test
    @DisplayName("getHealth — config absente → UNKNOWN, inactif")
    void getHealth_configAbsent_returnsUnknown() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.empty());

        ProviderHealthDTO health = provider.getHealth(companyId);

        assertThat(health.getProviderType()).isEqualTo("DHL");
        assertThat(health.getHealthStatus()).isEqualTo("UNKNOWN");
        assertThat(health.isActive()).isFalse();
    }

    // ---------- getRates — config errors ----------

    @Test
    @DisplayName("getRates — config absente → ProviderException")
    void getRates_configNotFound_throwsProviderException() {
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Configuration DHL non trouvée");

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("getRates — config inactive → ProviderException")
    void getRates_configInactive_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setActive(false);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
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
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));

        String cachedJson = """
                {"products":[{"localProductCode":"EXPRESS_WORLDWIDE","localProductName":"Express Worldwide",
                "totalPrice":[{"price":50.0,"priceCurrency":"EUR"}],
                "estimatedDeliveryDateAndTime":"2026-08-20T10:00:00"}]}
                """;
        ProviderRateCache cacheEntry = ProviderRateCache.builder()
                .providerType("DHL")
                .cacheKey("whatever")
                .responseJson(cachedJson)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(cacheEntry));

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getCarrierName()).isEqualTo("DHL Express");
        assertThat(rates.get(0).getTotalCost()).isEqualTo(50.0);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        verify(rateCacheRepo, never()).save(any());
    }

    // ---------- getRates — API key ----------

    @Test
    @DisplayName("getRates — clé API manquante → ProviderException")
    void getRates_apiKeyNull_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setApiKeyEncrypted(null);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Clé API DHL non configurée");
    }

    @Test
    @DisplayName("getRates — clé API vide → ProviderException")
    void getRates_apiKeyBlank_throwsProviderException() {
        ProviderConfig config = activeConfig();
        config.setApiKeyEncrypted("   ");
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Clé API DHL non configurée");
    }

    // ---------- getRates — success path ----------

    @Test
    @DisplayName("getRates — succès, mode AIR (EXPRESS) → tarifs retournés et mis en cache")
    void getRates_success_air_savesCacheAndReturnsRates() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"products":[{"localProductCode":"EXPRESS_WORLDWIDE","localProductName":"Express Worldwide",
                "totalPrice":[{"price":120.5,"priceCurrency":"EUR"}],
                "estimatedDeliveryDateAndTime":"2026-08-15T10:00:00"}]}
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
        assertThat(rate.getProviderType()).isEqualTo("DHL");
        assertThat(rate.getProviderName()).isEqualTo("DHL Express");

        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    @Test
    @DisplayName("getRates — succès, mode SEA (FREIGHT), sans date de livraison → transitDaysMax=5")
    void getRates_success_sea_noDeliveryDate() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"products":[{"localProductCode":"OCEAN_FREIGHT_LCL","localProductName":"Ocean Freight LCL",
                "totalPrice":[{"price":80.0,"priceCurrency":"USD"}]}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        QuoteResponseDTO rate = rates.get(0);
        assertThat(rate.getTransportMode()).isEqualTo("SEA");
        assertThat(rate.getCurrency()).isEqualTo("USD");
        assertThat(rate.getTransitDaysMin()).isEqualTo(1);
        assertThat(rate.getTransitDaysMax()).isEqualTo(5);
    }

    @Test
    @DisplayName("getRates — succès, plusieurs produits triés par coût total croissant")
    void getRates_success_multipleProducts_sortedByCost() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"products":[
                  {"localProductCode":"EXPRESS_WORLDWIDE","localProductName":"Express",
                   "totalPrice":[{"price":200.0,"priceCurrency":"EUR"}]},
                  {"localProductCode":"FCL_FREIGHT","localProductName":"Freight FCL",
                   "totalPrice":[{"price":50.0,"priceCurrency":"EUR"}]}
                ]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(2);
        assertThat(rates.get(0).getTotalCost()).isEqualTo(50.0);
        assertThat(rates.get(1).getTotalCost()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("getRates — produit sans totalPrice → baseRate/totalCost=0, devise EUR par défaut")
    void getRates_success_noTotalPrice_defaultsToZero() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"products":[{"localProductCode":"EASY_START","localProductName":"Easy Start"}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getBaseRate()).isEqualTo(0);
        assertThat(rates.get(0).getTotalCost()).isEqualTo(0);
        assertThat(rates.get(0).getCurrency()).isEqualTo("EUR");
        assertThat(rates.get(0).getTransportMode()).isEqualTo("AIR");
    }

    @Test
    @DisplayName("getRates — produit avec totalPrice vide (tableau vide) → baseRate/totalCost=0")
    void getRates_success_emptyTotalPriceArray_defaultsToZero() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        String body = """
                {"products":[{"localProductCode":"EXPRESS_WORLDWIDE","localProductName":"Express","totalPrice":[]}]}
                """;
        mockHttpResponse(body, HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getBaseRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("getRates — pas de champ products → liste vide, tarifs mis en cache quand même")
    void getRates_success_noProductsField_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{}", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    @Test
    @DisplayName("getRates — products n'est pas un tableau → liste vide")
    void getRates_success_productsNotArray_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{\"products\":\"not-an-array\"}", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
    }

    @Test
    @DisplayName("getRates — JSON malformé → parsing échoue silencieusement, liste vide")
    void getRates_success_malformedJson_returnsEmptyList() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{not-valid-json!!", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
        verify(rateCacheRepo, times(1)).save(any(ProviderRateCache.class));
    }

    @Test
    @DisplayName("getRates — apiSecret null → authentification basique avec mot de passe vide")
    void getRates_apiSecretNull_usesEmptyPassword() {
        ProviderConfig config = activeConfig();
        config.setApiSecret(null);
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{\"products\":[]}", HttpStatus.OK);

        List<QuoteResponseDTO> rates = provider.getRates(request(), companyId);

        assertThat(rates).isEmpty();
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ---------- getRates — réponse inattendue ----------

    @Test
    @DisplayName("getRates — statut non-OK → ProviderException")
    void getRates_responseNotOk_throwsProviderException() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        mockHttpResponse("{\"products\":[]}", HttpStatus.ACCEPTED);

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Réponse inattendue");
        verify(rateCacheRepo, never()).save(any());
    }

    @Test
    @DisplayName("getRates — corps de réponse null malgré statut OK → ProviderException")
    void getRates_responseBodyNull_throwsProviderException() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
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
    @DisplayName("getRates — HttpClientErrorException 401 → identifiants invalides, statut DEGRADED")
    void getRates_httpClientError401_marksDegraded_throwsInvalidCredentials() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Identifiants DHL invalides");

        verify(providerConfigRepo, times(1)).save(any(ProviderConfig.class));
        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(config.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRates — HttpClientErrorException autre que 401 → erreur API générique, statut DEGRADED")
    void getRates_httpClientErrorOther_marksDegraded_throwsGenericApiError() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Erreur API DHL");

        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("getRates — HttpClientErrorException, config absente au moment du markProviderStatus → pas de sauvegarde")
    void getRates_httpClientError_configAbsentDuringMarkStatus_noSave() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
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
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> provider.getRates(request(), companyId))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Erreur lors de l'appel à DHL");

        assertThat(config.getHealthStatus()).isEqualTo("DEGRADED");
        assertThat(config.getConsecutiveFailures()).isEqualTo(1);
    }

    // ---------- getRates — construction du corps de requête ----------

    @Test
    @DisplayName("getRates — weightKg null → poids par défaut 1.0 dans le corps de la requête")
    void getRates_nullWeightKg_usesDefaultWeightInBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"products\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setWeightKg(null);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) body.get("plannedPackages");
        @SuppressWarnings("unchecked")
        Map<String, Object> weight = (Map<String, Object>) packages.get(0).get("weight");
        assertThat(weight.get("value")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getRates — volumeM3 présent → dimensions calculées et ajoutées au corps")
    void getRates_volumePresent_addsDimensionsToBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"products\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setVolumeM3(1.0);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) body.get("plannedPackages");
        assertThat(packages.get(0)).containsKey("dimensions");
    }

    @Test
    @DisplayName("getRates — volumeM3 null → pas de dimensions dans le corps")
    void getRates_volumeNull_noDimensionsInBody() {
        ProviderConfig config = activeConfig();
        when(providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL"))
                .thenReturn(Optional.of(config));
        when(rateCacheRepo.findByCacheKeyAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        mockHttpResponse("{\"products\":[]}", HttpStatus.OK);

        QuoteRequestDTO req = request();
        req.setVolumeM3(null);

        provider.getRates(req, companyId);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) body.get("plannedPackages");
        assertThat(packages.get(0)).doesNotContainKey("dimensions");
    }

    // ---------- mapDhlMode (méthode privée, testée par réflexion) ----------

    private String invokeMapDhlMode(String productCode) throws Exception {
        Method m = DHLCarrierProvider.class.getDeclaredMethod("mapDhlMode", String.class);
        m.setAccessible(true);
        return (String) m.invoke(provider, productCode);
    }

    @Test
    @DisplayName("mapDhlMode — code null → AIR")
    void mapDhlMode_null_returnsAir() throws Exception {
        assertThat(invokeMapDhlMode(null)).isEqualTo("AIR");
    }

    @Test
    @DisplayName("mapDhlMode — contient EXPRESS → AIR")
    void mapDhlMode_express_returnsAir() throws Exception {
        assertThat(invokeMapDhlMode("EXPRESS_WORLDWIDE")).isEqualTo("AIR");
    }

    @Test
    @DisplayName("mapDhlMode — contient EASY (sans EXPRESS) → AIR")
    void mapDhlMode_easy_returnsAir() throws Exception {
        assertThat(invokeMapDhlMode("EASY_START")).isEqualTo("AIR");
    }

    @Test
    @DisplayName("mapDhlMode — contient FREIGHT → SEA")
    void mapDhlMode_freight_returnsSea() throws Exception {
        assertThat(invokeMapDhlMode("OCEAN_FREIGHT")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapDhlMode — contient FCL (sans FREIGHT) → SEA")
    void mapDhlMode_fcl_returnsSea() throws Exception {
        assertThat(invokeMapDhlMode("FCL_STANDARD")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapDhlMode — contient LCL (sans FREIGHT/FCL) → SEA")
    void mapDhlMode_lcl_returnsSea() throws Exception {
        assertThat(invokeMapDhlMode("LCL_STANDARD")).isEqualTo("SEA");
    }

    @Test
    @DisplayName("mapDhlMode — code inconnu → AIR par défaut")
    void mapDhlMode_unknown_returnsAirDefault() throws Exception {
        assertThat(invokeMapDhlMode("SOME_OTHER_PRODUCT")).isEqualTo("AIR");
    }

    // ---------- buildCacheKey (méthode privée, testée par réflexion) ----------

    @Test
    @DisplayName("buildCacheKey — transportMode fourni → utilisé dans la clé")
    void buildCacheKey_withTransportMode() throws Exception {
        Method m = DHLCarrierProvider.class.getDeclaredMethod("buildCacheKey", QuoteRequestDTO.class);
        m.setAccessible(true);
        String key = (String) m.invoke(provider, request());
        assertThat(key).contains("AIR");
    }

    @Test
    @DisplayName("buildCacheKey — transportMode null → ANY dans la clé")
    void buildCacheKey_nullTransportMode_usesAny() throws Exception {
        Method m = DHLCarrierProvider.class.getDeclaredMethod("buildCacheKey", QuoteRequestDTO.class);
        m.setAccessible(true);
        QuoteRequestDTO req = request();
        req.setTransportMode(null);
        String key = (String) m.invoke(provider, req);
        assertThat(key).contains("ANY");
    }
}
