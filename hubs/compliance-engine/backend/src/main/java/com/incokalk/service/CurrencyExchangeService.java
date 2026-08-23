package com.incokalk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeService {

    private final RestTemplate restTemplate;

    @Value("${incokalk.exchange-rate.api-key:}")
    private String apiKey;

    @Value("${incokalk.exchange-rate.base-url:https://api.exchangerate-api.com/v4}")
    private String baseUrl;

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
        "EUR", "USD", "GBP", "CHF", "JPY", "CAD", "AUD", "CNY", "INR",
        "BRL", "MXN", "KRW", "SGD", "HKD", "NOK", "SEK", "DKK", "PLN",
        "CZK", "HUF", "RON", "BGN", "HRK", "TRY", "AED", "SAR", "ZAR",
        "THB", "MYR", "IDR", "PHP", "VND", "EGP", "NGN", "KES", "MAD",
        "TND", "DZD", "XOF", "XAF"
    );

    @Cacheable(value = "exchange-rates", key = "#baseCurrency")
    public Map<String, Double> getRates(String baseCurrency) {
        try {
            String url = baseUrl + "/latest/" + baseCurrency;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("rates")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = (Map<String, Object>) response.get("rates");
                Map<String, Double> rates = new HashMap<>();
                if (raw != null) {
                    raw.forEach((cur, value) -> {
                        if (cur != null && value instanceof Number n) {
                            rates.put(cur.toUpperCase(), n.doubleValue());
                        }
                    });
                }
                log.info("[FX] Taux récupérés pour base={}: {} devises", baseCurrency, rates.size());
                return rates;
            }
        } catch (Exception e) {
            log.error("[FX] Erreur récupération taux pour {}: {}", baseCurrency, e.getMessage());
        }
        return Map.of();
    }

    public double convert(double amount, String from, String to) {
        if (from.equalsIgnoreCase(to)) return amount;
        Map<String, Double> rates = getRates(from);
        Double rate = rates.get(to.toUpperCase());
        if (rate == null) {
            log.warn("[FX] Taux non trouvé: {} → {}", from, to);
            return amount;
        }
        return Math.round(amount * rate * 100.0) / 100.0;
    }

    public double getRate(String from, String to) {
        if (from.equalsIgnoreCase(to)) return 1.0;
        Map<String, Double> rates = getRates(from);
        return rates.getOrDefault(to.toUpperCase(), 1.0);
    }

    public Set<String> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
