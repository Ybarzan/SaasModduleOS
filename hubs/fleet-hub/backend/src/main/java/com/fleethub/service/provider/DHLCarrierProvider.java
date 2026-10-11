package com.fleethub.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.dto.ProviderHealthDTO;
import com.fleethub.dto.QuoteRequestDTO;
import com.fleethub.dto.QuoteResponseDTO;
import com.fleethub.model.CarrierProviderConfig;
import com.fleethub.model.CarrierRateCache;
import com.fleethub.repository.CarrierProviderConfigRepository;
import com.fleethub.repository.CarrierRateCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DHLCarrierProvider implements CarrierProvider {

    private final CarrierProviderConfigRepository providerConfigRepo;
    private final CarrierRateCacheRepository rateCacheRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fleethub.providers.dhl.base-url:https://api-eu.dhl.com/mydhlapi}")
    private String baseUrl;

    @Value("${fleethub.providers.cache.ttl-hours:1}")
    private int cacheTtlHours;

    @Override
    public String getProviderType() {
        return "DHL";
    }

    @Override
    public String getName() {
        return "DHL Express";
    }

    @Override
    public String getLogoUrl() {
        return "https://www.dhl.com/content/dam/dhl/global/core/images/logo-dhl.svg";
    }

    @Override
    public boolean isAvailable(Long companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL")
                .map(CarrierProviderConfig::isActive)
                .orElse(false);
    }

    @Override
    public ProviderHealthDTO getHealth(Long companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL")
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
    public List<QuoteResponseDTO> getRates(QuoteRequestDTO request, Long companyId) throws CarrierProviderException {
        CarrierProviderConfig config = providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL")
                .orElseThrow(() -> new CarrierProviderException("DHL", "Configuration DHL non trouvée pour cette entreprise"));

        if (!config.isActive()) {
            throw new CarrierProviderException("DHL", "Le fournisseur DHL est désactivé");
        }

        String cacheKey = buildCacheKey(request);
        Optional<CarrierRateCache> cached = rateCacheRepo.findByCacheKeyAndExpiresAtAfter(cacheKey, LocalDateTime.now());
        if (cached.isPresent()) {
            log.debug("[DHL] Cache hit pour la clé {}", cacheKey);
            return parseRatesFromJson(cached.get().getResponseJson());
        }

        log.info("[DHL] Appel API DHL pour {} -> {}", request.getOriginCountry(), request.getDestinationCountry());

        try {
            String apiKey = config.getApiKeyEncrypted();
            String apiSecret = config.getApiSecret();
            if (apiKey == null || apiKey.isBlank()) {
                throw new CarrierProviderException("DHL", "Clé API DHL non configurée");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, apiSecret != null ? apiSecret : "");
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> body = buildDhlRequest(request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/shipments/rates",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<QuoteResponseDTO> rates = parseRatesFromJson(response.getBody());

                CarrierRateCache cacheEntry = CarrierRateCache.builder()
                        .providerType("DHL")
                        .cacheKey(cacheKey)
                        .responseJson(response.getBody())
                        .expiresAt(LocalDateTime.now().plusHours(cacheTtlHours))
                        .build();
                rateCacheRepo.save(cacheEntry);

                log.info("[DHL] {} tarifs récupérés", rates.size());
                return rates;
            } else {
                throw new CarrierProviderException("DHL", "Réponse inattendue de l'API DHL: " + response.getStatusCode());
            }
        } catch (CarrierProviderException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                markProviderStatus(companyId, "DEGRADED");
                throw new CarrierProviderException("DHL", "Identifiants DHL invalides (401)", e);
            }
            markProviderStatus(companyId, "DEGRADED");
            throw new CarrierProviderException("DHL", "Erreur API DHL: " + e.getMessage(), e);
        } catch (Exception e) {
            markProviderStatus(companyId, "DEGRADED");
            throw new CarrierProviderException("DHL", "Erreur lors de l'appel à DHL: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildDhlRequest(QuoteRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> customerDetails = new LinkedHashMap<>();
        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("countryCode", request.getOriginCountry());
        customerDetails.put("shipperDetails", shipper);
        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("countryCode", request.getDestinationCountry());
        customerDetails.put("receiverDetails", receiver);
        body.put("customerDetails", customerDetails);

        Map<String, Object> accounts = new LinkedHashMap<>();
        body.put("accounts", accounts);

        List<Map<String, Object>> plannedPackages = new ArrayList<>();
        Map<String, Object> weight = new LinkedHashMap<>();
        weight.put("uom", "kg");
        weight.put("value", request.getWeightKg() != null ? request.getWeightKg() : 1.0);
        Map<String, Object> dimensions = new LinkedHashMap<>();
        if (request.getVolumeM3() != null) {
            double side = Math.cbrt(request.getVolumeM3());
            dimensions.put("length", Math.round(side * 100));
            dimensions.put("width", Math.round(side * 100));
            dimensions.put("height", Math.round(side * 100));
            dimensions.put("uom", "cm");
        }
        Map<String, Object> plannedPackage = new LinkedHashMap<>();
        plannedPackage.put("weight", weight);
        if (!dimensions.isEmpty()) {
            plannedPackage.put("dimensions", dimensions);
        }
        plannedPackages.add(plannedPackage);
        body.put("plannedPackages", plannedPackages);

        return body;
    }

    private List<QuoteResponseDTO> parseRatesFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode products = root.path("products");
            if (products == null || !products.isArray()) {
                return List.of();
            }

            List<QuoteResponseDTO> rates = new ArrayList<>();
            for (JsonNode product : products) {
                String productCode = product.path("localProductCode").asText("");
                String transportMode = mapDhlMode(productCode);

                QuoteResponseDTO.QuoteResponseDTOBuilder builder = QuoteResponseDTO.builder()
                        .rateId(UUID.randomUUID())
                        .carrierName("DHL Express")
                        .carrierLogo(getLogoUrl())
                        .rateName(product.path("localProductName").asText(productCode))
                        .transportMode(transportMode)
                        .providerType("DHL")
                        .providerName(getName())
                        .providerLogo(getLogoUrl());

                JsonNode totalPrice = product.path("totalPrice");
                if (totalPrice.isArray() && totalPrice.size() > 0) {
                    builder.baseRate(totalPrice.get(0).path("price").asDouble(0));
                    builder.totalCost(totalPrice.get(0).path("price").asDouble(0));
                    builder.currency(totalPrice.get(0).path("priceCurrency").asText("EUR"));
                } else {
                    builder.baseRate(0);
                    builder.totalCost(0);
                    builder.currency("EUR");
                }

                JsonNode estimatedDelivery = product.path("estimatedDeliveryDateAndTime");
                if (estimatedDelivery.isTextual()) {
                    builder.transitDaysMin(1);
                    builder.transitDaysMax(3);
                } else {
                    builder.transitDaysMin(1);
                    builder.transitDaysMax(5);
                }

                rates.add(builder.build());
            }

            rates.sort(Comparator.comparingDouble(QuoteResponseDTO::getTotalCost));
            return rates;
        } catch (Exception e) {
            log.error("[DHL] Erreur lors du parsing de la réponse: {}", e.getMessage());
            return List.of();
        }
    }

    private String mapDhlMode(String productCode) {
        if (productCode == null) return "AIR";
        String upper = productCode.toUpperCase();
        if (upper.contains("EXPRESS") || upper.contains("EASY")) return "AIR";
        if (upper.contains("FREIGHT") || upper.contains("FCL") || upper.contains("LCL")) return "SEA";
        return "AIR";
    }

    private String buildCacheKey(QuoteRequestDTO request) {
        return "DHL:" + request.getOriginCountry() + ":" + request.getDestinationCountry()
                + ":" + (request.getTransportMode() != null ? request.getTransportMode() : "ANY")
                + ":" + request.getWeightKg() + ":" + request.getVolumeM3();
    }

    private void markProviderStatus(Long companyId, String status) {
        providerConfigRepo.findByCompanyIdAndProviderType(companyId, "DHL").ifPresent(pc -> {
            pc.setHealthStatus(status);
            pc.setLastHealthCheck(LocalDateTime.now());
            pc.setConsecutiveFailures(pc.getConsecutiveFailures() + 1);
            providerConfigRepo.save(pc);
        });
    }
}
