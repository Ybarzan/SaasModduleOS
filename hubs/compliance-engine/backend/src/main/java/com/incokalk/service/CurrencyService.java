package com.incokalk.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CurrencyService {

    private static final Map<String, Double> FALLBACK_RATES = Map.of(
        "EUR", 1.0, "USD", 0.922, "GBP", 1.167,
        "CNY", 0.128, "JPY", 0.0062, "MAD", 0.092, "TRY", 0.028
    );

    private final Map<String, Double> rates = new ConcurrentHashMap<>(FALLBACK_RATES);
    private final CurrencyExchangeService exchangeService;

    public CurrencyService(CurrencyExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    public double toEur(double amount, String fromCurrency) {
        if (fromCurrency == null || fromCurrency.isBlank()) {
            throw new IllegalArgumentException("Devise manquante");
        }
        if ("EUR".equalsIgnoreCase(fromCurrency)) return amount;
        double rate = resolveRate(fromCurrency);
        return Math.round(amount * rate * 100.0) / 100.0;
    }

    public double fromEur(double amountEur, String toCurrency) {
        if (toCurrency == null || toCurrency.isBlank()) {
            throw new IllegalArgumentException("Devise manquante");
        }
        if ("EUR".equalsIgnoreCase(toCurrency)) return amountEur;
        double rate = resolveRate(toCurrency);
        return Math.round((amountEur / rate) * 100.0) / 100.0;
    }

    private double resolveRate(String currency) {
        String code = currency.toUpperCase();
        Double cached = rates.get(code);
        if (cached != null) return cached;
        Double live = fetchLiveRate(code);
        if (live != null) return live;
        throw new IllegalArgumentException(
            "Devise non supportée: '" + currency + "'. Devises supportées: " + supportedCodes());
    }

    private Double fetchLiveRate(String code) {
        try {
            Map<String, Double> live = exchangeService.getRates("EUR");
            if (live != null && !live.isEmpty()) {
                rates.put("EUR", 1.0);
                live.forEach((cur, rate) -> {
                    if (cur != null && rate != null && rate > 0) {
                        rates.put(cur.toUpperCase(), 1.0 / rate);
                    }
                });
                Double rate = rates.get(code);
                if (rate != null) {
                    log.info("[FX] Devise {} résolue via taux vivants", code);
                }
                return rate;
            }
        } catch (Exception e) {
            log.warn("[FX] Taux vivants indisponibles pour {}: {}", code, e.getMessage());
        }
        return null;
    }

    private String supportedCodes() {
        Set<String> codes = new TreeSet<>(rates.keySet());
        return String.join(", ", codes);
    }

    public Map<String, Double> getAllRates() {
        return Collections.unmodifiableMap(rates);
    }
}
