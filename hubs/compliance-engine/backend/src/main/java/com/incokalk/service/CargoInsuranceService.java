package com.incokalk.service;

import com.incokalk.dto.financial.CargoInsuranceRequest;
import com.incokalk.dto.financial.CargoInsuranceResult;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CargoInsuranceQuote;
import com.incokalk.repository.CargoInsuranceQuoteRepository;
import com.incokalk.service.insurance.CargoInsurerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CargoInsuranceService {

    private static final Map<String, Double> MODE_FACTORS = Map.of(
        "SEA", 0.003,
        "AIR", 0.005,
        "ROAD", 0.004
    );

    private static final Map<String, Double> CATEGORY_FACTORS = Map.of(
        "STANDARD", 1.0,
        "FRAGILE", 1.4,
        "HAUTE_VALEUR", 1.8,
        "PERISSABLE", 1.6,
        "DANGEREUX", 2.0,
        "ELECTRONIQUE", 1.3
    );

    private final CargoInsurerClient insurerClient;
    private final CargoInsuranceQuoteRepository quoteRepository;

    public CargoInsuranceResult calculate(CargoInsuranceRequest request) {
        String mode = request.getTransportMode() != null ? request.getTransportMode().toUpperCase() : "SEA";
        String category = request.getGoodsCategory() != null ? request.getGoodsCategory().toUpperCase() : "STANDARD";

        double baseRate = MODE_FACTORS.getOrDefault(mode, 0.004);
        double categoryFactor = CATEGORY_FACTORS.getOrDefault(category, 1.0);
        double marketFactor = insurerClient.fetchMarketRateFactor(request).orElse(1.0);

        double premiumRate = baseRate * categoryFactor * marketFactor;
        double coverageAmount = request.getGoodsValue() * 1.1;
        double premiumAmount = Math.round(coverageAmount * premiumRate * 100.0) / 100.0;

        String coverageType = switch (category) {
            case "HAUTE_VALEUR" -> "Tous risques (valeur majorée)";
            case "DANGEREUX" -> "Tous risques + responsabilité civile";
            case "PERISSABLE" -> "Température contrôlée + avaries";
            default -> "Institute Cargo Clauses (A)";
        };

        String note = "Estimation basée sur les tarifs moyens du marché. Consultez un courtier pour un devis définitif.";
        if (marketFactor != 1.0) {
            note += " Ajusté par le facteur de marché du courtier (x" + Math.round(marketFactor * 1000.0) / 1000.0 + ").";
        }

        return CargoInsuranceResult.builder()
            .goodsValue(request.getGoodsValue())
            .premiumRate(Math.round(premiumRate * 10000.0) / 10000.0)
            .premiumAmount(premiumAmount)
            .coverageAmount(Math.round(coverageAmount * 100.0) / 100.0)
            .coverageType(coverageType)
            .transportMode(mode)
            .note(note)
            .build();
    }

    @Transactional
    public CargoInsuranceQuote saveQuote(CargoInsuranceRequest request, CargoInsuranceResult result, UUID companyId) {
        CargoInsuranceQuote quote = CargoInsuranceQuote.builder()
            .companyId(companyId)
            .goodsValue(BigDecimal.valueOf(result.getGoodsValue()))
            .weightKg(request.getWeightKg() != null ? BigDecimal.valueOf(request.getWeightKg()) : null)
            .transportMode(result.getTransportMode())
            .goodsCategory(request.getGoodsCategory())
            .originCountry(request.getOriginCountry())
            .destinationCountry(request.getDestinationCountry())
            .currency(request.getCurrency() != null ? request.getCurrency() : "EUR")
            .premiumRate(BigDecimal.valueOf(result.getPremiumRate()))
            .premiumAmount(BigDecimal.valueOf(result.getPremiumAmount()))
            .coverageAmount(BigDecimal.valueOf(result.getCoverageAmount()))
            .coverageType(result.getCoverageType())
            .status(CargoInsuranceQuote.Status.QUOTE)
            .build();
        return quoteRepository.save(quote);
    }

    public List<CargoInsuranceQuote> listQuotes(UUID companyId) {
        return quoteRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public CargoInsuranceQuote activatePolicy(UUID id, UUID companyId) {
        CargoInsuranceQuote quote = quoteRepository.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Devis d'assurance introuvable"));
        quote.setStatus(CargoInsuranceQuote.Status.POLICY);
        quote.setPolicyNumber("POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return quoteRepository.save(quote);
    }
}
