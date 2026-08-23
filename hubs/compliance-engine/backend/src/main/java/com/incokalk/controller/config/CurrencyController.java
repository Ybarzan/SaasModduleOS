package com.incokalk.controller.config;

import com.incokalk.service.CurrencyExchangeService;
import com.incokalk.service.FinancialReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/currencies")
@RequiredArgsConstructor
@Tag(name = "Currencies", description = "Taux de change et conversion de devises")
public class CurrencyController {

    private final CurrencyExchangeService exchangeService;
    private final FinancialReportingService financialReportingService;

    @GetMapping
    @Operation(summary = "Lister les devises supportées")
    public ResponseEntity<Set<String>> listCurrencies() {
        return ResponseEntity.ok(exchangeService.getSupportedCurrencies());
    }

    @GetMapping("/rate")
    @Operation(summary = "Obtenir le taux entre deux devises")
    public ResponseEntity<Map<String, Object>> getRate(
            @RequestParam String from,
            @RequestParam String to) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();
        if (!exchangeService.getSupportedCurrencies().contains(f)) {
            throw new IllegalArgumentException("Devise source non supportée : " + f);
        }
        if (!exchangeService.getSupportedCurrencies().contains(t)) {
            throw new IllegalArgumentException("Devise cible non supportée : " + t);
        }
        double rate = exchangeService.getRate(f, t);
        return ResponseEntity.ok(Map.of(
                "from", f,
                "to", t,
                "rate", rate
        ));
    }

    @GetMapping("/rates")
    @Operation(summary = "Obtenir tous les taux depuis une devise de base")
    public ResponseEntity<Map<String, Object>> getRates(@RequestParam(defaultValue = "EUR") String base) {
        Map<String, Double> rates = exchangeService.getRates(base.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "base", base.toUpperCase(),
                "rates", rates,
                "supported", exchangeService.getSupportedCurrencies()
        ));
    }

    @GetMapping("/convert")
    @Operation(summary = "Convertir un montant entre deux devises")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam double amount,
            @RequestParam String from,
            @RequestParam String to) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();
        if (!exchangeService.getSupportedCurrencies().contains(f)) {
            throw new IllegalArgumentException("Devise source non supportée : " + f);
        }
        if (!exchangeService.getSupportedCurrencies().contains(t)) {
            throw new IllegalArgumentException("Devise cible non supportée : " + t);
        }
        double converted = exchangeService.convert(amount, f, t);
        double rate = exchangeService.getRate(f, t);
        return ResponseEntity.ok(Map.of(
                "from", f,
                "to", t,
                "originalAmount", amount,
                "convertedAmount", converted,
                "rate", rate
        ));
    }

    @GetMapping("/exposure-report")
    @Operation(summary = "Rapport d exposition aux changes (FX risk)")
    public ResponseEntity<Map<String, Object>> getFxExposureReport() {
        return ResponseEntity.ok(financialReportingService.getFxExposureReport());
    }
}
