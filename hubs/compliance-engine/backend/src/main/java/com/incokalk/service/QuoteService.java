package com.incokalk.service;

import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.dto.shipment.SimulationRequest.TransportModeInput;
import com.incokalk.exception.ProviderException;
import com.incokalk.service.provider.CarrierProvider;
import com.incokalk.service.provider.CarrierProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {

    private final CarrierProviderRegistry providerRegistry;
    private final ProviderHealthService healthService;
    private final CurrencyExchangeService exchangeService;
    private final FreightRateService freightRateService;

    public List<QuoteResponseDTO> getQuotes(QuoteRequestDTO request, UUID companyId) {
        List<CarrierProvider> activeProviders = providerRegistry.getAllAvailableProviders(companyId);

        if (activeProviders.isEmpty()) {
            log.warn("[Quote] Aucun fournisseur actif pour companyId={} — tarif interne de secours", companyId);
            return List.of(internalFallbackQuote(request));
        }

        log.info("[Quote] {} fournisseur(s) actif(s) pour companyId={}: {}",
                activeProviders.size(), companyId,
                activeProviders.stream().map(CarrierProvider::getProviderType).collect(Collectors.joining(", ")));

        List<QuoteResponseDTO> allQuotes = new ArrayList<>();

        for (CarrierProvider provider : activeProviders) {
            if (healthService.isCircuitBroken(provider.getProviderType(), companyId)) {
                log.warn("[Quote] Circuit breaker actif pour {}, ignoré", provider.getProviderType());
                continue;
            }

            try {
                List<QuoteResponseDTO> rates = provider.getRates(request, companyId);
                allQuotes.addAll(rates);
                healthService.recordSuccess(provider.getProviderType(), companyId);
                log.info("[Quote] {} a retourné {} tarif(s)", provider.getProviderType(), rates.size());
            } catch (ProviderException e) {
                log.error("[Quote] Échec pour {}: {}", provider.getProviderType(), e.getMessage());
                healthService.recordFailure(provider.getProviderType(), companyId);
            }
        }

        if (allQuotes.isEmpty()) {
            log.warn("[Quote] Aucun tarif transporteur — tarif interne de secours");
            allQuotes.add(internalFallbackQuote(request));
        }

        allQuotes.sort(Comparator.comparingDouble(QuoteResponseDTO::getTotalCost));

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            String targetCurrency = request.getCurrency().toUpperCase();
            for (QuoteResponseDTO q : allQuotes) {
                if (!q.getCurrency().equalsIgnoreCase(targetCurrency)) {
                    double converted = exchangeService.convert(q.getTotalCost(), q.getCurrency(), targetCurrency);
                    q.setTotalCostConverted(converted);
                    q.setDisplayCurrency(targetCurrency);
                    q.setConversionRate(exchangeService.getRate(q.getCurrency(), targetCurrency));
                }
            }
        }

        log.info("[Quote] {} tarif(s) total(s) combinés pour companyId={}", allQuotes.size(), companyId);
        return allQuotes;
    }

    private QuoteResponseDTO internalFallbackQuote(QuoteRequestDTO request) {
        double goodsValue = request.getGoodsValue() != null ? request.getGoodsValue() : 10000.0;
        TransportModeInput mode = parseMode(request.getTransportMode());
        if (mode == null) {
            mode = freightRateService.guess(request.getOriginCountry(), goodsValue);
        }
        FreightRateService.FreightEstimate est = freightRateService.estimate(
            request.getOriginCountry(), request.getDestinationCountry(), mode,
            request.getWeightKg(), request.getVolumeM3(), goodsValue);
        return QuoteResponseDTO.builder()
            .carrierName("IncoKalk Standard")
            .rateName("Tarif standard " + mode.name())
            .transportMode(mode.name())
            .baseRate(est.cost())
            .totalCost(est.cost())
            .currency("EUR")
            .transitDaysMin(est.days())
            .transitDaysMax(est.days())
            .providerType("INCALK")
            .providerName("IncoKalk")
            .build();
    }

    private TransportModeInput parseMode(String mode) {
        if (mode != null && !mode.isBlank()) {
            try {
                return TransportModeInput.valueOf(mode.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // mode inconnu → laisse FreightRateService décider
            }
        }
        return null;
    }
}
