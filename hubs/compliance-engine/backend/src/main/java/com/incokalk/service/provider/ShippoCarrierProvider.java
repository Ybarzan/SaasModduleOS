package com.incokalk.service.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.exception.ProviderException;
import com.incokalk.model.ProviderConfig;
import com.incokalk.model.ProviderRateCache;
import com.incokalk.repository.ProviderConfigRepository;
import com.incokalk.repository.ProviderRateCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippoCarrierProvider implements CarrierProvider {

    private final ProviderConfigRepository providerConfigRepo;
    private final ProviderRateCacheRepository rateCacheRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.shippo.base-url:https://api.goshippo.com}")
    private String baseUrl;

    @Value("${incokalk.providers.cache.ttl-hours:1}")
    private int cacheTtlHours;

    private final Map<UUID, ProviderHealthDTO> healthMap = new ConcurrentHashMap<>();

    @Override
    public String getProviderType() {
        return "SHIPPO";
    }

    @Override
    public String getName() {
        return "Shippo";
    }

    @Override
    public String getLogoUrl() {
        return "https://goshippo.com/img/logo-shippo.svg";
    }

    @Override
    public boolean isAvailable(UUID companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO")
                .map(ProviderConfig::isActive)
                .orElse(false);
    }

    @Override
    public ProviderHealthDTO getHealth(UUID companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO")
                .map(pc -> ProviderHealthDTO.builder()
                        .providerType(getProviderType())
                        .healthStatus(pc.getHealthStatus())
                        .lastHealthCheck(pc.getLastHealthCheck())
                        .consecutiveFailures(pc.getConsecutiveFailures())
                        .isActive(pc.isActive())
                        .build())
                .orElse(ProviderHealthDTO.builder()
                        .providerType(getProviderType())
                        .healthStatus("UNKNOWN")
                        .isActive(false)
                        .build());
    }

    @Override
    public List<QuoteResponseDTO> getRates(QuoteRequestDTO request, UUID companyId) throws ProviderException {
        ProviderConfig config = providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO")
                .orElseThrow(() -> new ProviderException("SHIPPO", "Configuration Shippo non trouvée pour cette entreprise"));

        if (!config.isActive()) {
            throw new ProviderException("SHIPPO", "Le fournisseur Shippo est désactivé");
        }

        String cacheKey = buildCacheKey(request);
        Optional<ProviderRateCache> cached = rateCacheRepo.findByCacheKeyAndExpiresAtAfter(cacheKey, LocalDateTime.now());
        if (cached.isPresent()) {
            log.debug("[SHIPPO] Cache hit pour la clé {}", cacheKey);
            return parseRatesFromJson(cached.get().getResponseJson(), config.getId());
        }

        log.info("[SHIPPO] Appel API Shippo pour {} -> {}", request.getOriginCountry(), request.getDestinationCountry());

        try {
            String apiKey = config.getApiKeyEncrypted();
            if (apiKey == null || apiKey.isBlank()) {
                throw new ProviderException("SHIPPO", "Clé API Shippo non configurée");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "ShippoToken " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = buildShippoRequest(request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/rates",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<QuoteResponseDTO> rates = parseRatesFromJson(response.getBody(), config.getId());

                ProviderRateCache cacheEntry = ProviderRateCache.builder()
                        .providerType("SHIPPO")
                        .cacheKey(cacheKey)
                        .responseJson(response.getBody())
                        .expiresAt(LocalDateTime.now().plusHours(cacheTtlHours))
                        .build();
                rateCacheRepo.save(cacheEntry);

                log.info("[SHIPPO] {} tarifs récupérés", rates.size());
                return rates;
            } else {
                throw new ProviderException("SHIPPO", "Réponse inattendue de l'API Shippo: " + response.getStatusCode());
            }
        } catch (ProviderException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                markProviderStatus(companyId, "DEGRADED");
                throw new ProviderException("SHIPPO", "Clé API Shippo invalide (401)", e);
            }
            markProviderStatus(companyId, "DEGRADED");
            throw new ProviderException("SHIPPO", "Erreur API Shippo: " + e.getMessage(), e);
        } catch (Exception e) {
            markProviderStatus(companyId, "DEGRADED");
            throw new ProviderException("SHIPPO", "Erreur lors de l'appel à Shippo: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildShippoRequest(QuoteRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> addressFrom = new LinkedHashMap<>();
        addressFrom.put("country", request.getOriginCountry());
        body.put("address_from", addressFrom);

        Map<String, Object> addressTo = new LinkedHashMap<>();
        addressTo.put("country", request.getDestinationCountry());
        body.put("address_to", addressTo);

        Map<String, Object> parcel = new LinkedHashMap<>();
        parcel.put("weight", request.getWeightKg() != null ? request.getWeightKg() * 1000 : 1000);
        parcel.put("weight_unit", "g");
        parcel.put("distance_unit", "cm");
        if (request.getVolumeM3() != null) {
            double side = Math.cbrt(request.getVolumeM3() * 1_000_000);
            parcel.put("length", Math.round(side));
            parcel.put("width", Math.round(side));
            parcel.put("height", Math.round(side));
        }
        body.put("parcels", List.of(parcel));

        return body;
    }

    private List<QuoteResponseDTO> parseRatesFromJson(String json, UUID configId) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                return List.of();
            }

            List<QuoteResponseDTO> rates = new ArrayList<>();
            for (JsonNode rateNode : results) {
                String transportMode = mapShippoMode(rateNode.path("terms").asText(""));

                QuoteResponseDTO quote = QuoteResponseDTO.builder()
                        .rateId(UUID.randomUUID())
                        .carrierName(rateNode.path("provider").path("display_name").asText("Shippo"))
                        .carrierLogo(rateNode.path("provider").path("logo_url").asText(getLogoUrl()))
                        .rateName(rateNode.path("servicelevel").path("name").asText(""))
                        .transportMode(transportMode)
                        .baseRate(rateNode.path("amount").asDouble(0))
                        .totalCost(rateNode.path("amount").asDouble(0))
                        .currency(rateNode.path("currency").asText("USD"))
                        .transitDaysMin(rateNode.path("estimated_days").asInt(0))
                        .transitDaysMax(rateNode.path("estimated_days").asInt(0) + 2)
                        .providerType("SHIPPO")
                        .providerName(getName())
                        .providerLogo(getLogoUrl())
                        .build();
                rates.add(quote);
            }

            rates.sort(Comparator.comparingDouble(QuoteResponseDTO::getTotalCost));
            return rates;
        } catch (Exception e) {
            log.error("[SHIPPO] Erreur lors du parsing de la réponse: {}", e.getMessage());
            return List.of();
        }
    }

    private String mapShippoMode(String shippoTerms) {
        if (shippoTerms == null) return "ROAD";
        String lower = shippoTerms.toLowerCase();
        if (lower.contains("express") || lower.contains("priority")) return "AIR";
        if (lower.contains("sea") || lower.contains("ocean") || lower.contains("freight")) return "SEA";
        return "ROAD";
    }

    private String buildCacheKey(QuoteRequestDTO request) {
        return "SHIPPO:" + request.getOriginCountry() + ":" + request.getDestinationCountry()
                + ":" + (request.getTransportMode() != null ? request.getTransportMode() : "ANY")
                + ":" + request.getWeightKg() + ":" + request.getVolumeM3();
    }

    private void markProviderStatus(UUID companyId, String status) {
        providerConfigRepo.findByCompanyIdAndProviderType(companyId, "SHIPPO").ifPresent(pc -> {
            pc.setHealthStatus(status);
            pc.setLastHealthCheck(LocalDateTime.now());
            pc.setConsecutiveFailures(pc.getConsecutiveFailures() + 1);
            providerConfigRepo.save(pc);
        });
    }
}
