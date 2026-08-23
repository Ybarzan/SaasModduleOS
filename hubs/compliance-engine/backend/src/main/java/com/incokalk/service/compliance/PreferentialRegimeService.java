package com.incokalk.service.compliance;

import com.incokalk.dto.compliance.PreferentialDutyResult;
import com.incokalk.model.TradeAgreement;
import com.incokalk.model.TaricRate;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferentialRegimeService {

    private final RulesOfOriginService rulesOfOriginService;
    private final TaricRateRepository taricRepo;
    private final TradeAgreementRepository agreementRepo;

    public record PreferentialResult(
        boolean isPreferential,
        String agreementCode,
        String agreementName,
        double mfnDutyRate,
        double preferentialDutyRate,
        double savings,
        String originCriterion,
        String originExplanation,
        double valueAddedPct,
        boolean isOriginating
    ) implements Serializable {}

    /**
     * Calcule les droits de douane pr�f�rentiels pour un produit.
     */
    public PreferentialResult calculatePreferentialDuty(
            String hsCode,
            String originCountry,
            String destCountry,
            double goodsValue,
            double valueAdded,
            double totalCost) {

        // 1. V�rifier l'�ligibilit� pr�f�rentielle via les r�gles d'origine
        RulesOfOriginService.OriginVerificationResult originResult =
                rulesOfOriginService.verifyOrigin(hsCode, originCountry, destCountry, valueAdded, totalCost, List.of());

        if (!originResult.isOriginating()) {
            return new PreferentialResult(
                    false, null, null, 0, 0, 0, null,
                    originResult.explanation(), 0, false);
        }

        // 2. R�cup�rer le taux MFN (Most Favoured Nation)
        double mfnRate = getMfnRate(hsCode, originCountry, destCountry);

        // 3. R�cup�rer le taux pr�f�rentiel
        double prefRate = getPreferentialRate(hsCode, originCountry, destCountry, originResult.agreementCode());

        // 4. Calculer les �conomies
        double savings = goodsValue * (mfnRate - prefRate) / 100;

        return new PreferentialResult(
                true,
                originResult.agreementCode(),
                originResult.agreementName(),
                mfnRate,
                prefRate,
                savings,
                originResult.criterionUsed() != null ? originResult.criterionUsed().name() : null,
                originResult.explanation(),
                originResult.valueAddedPercentage(),
                true);
    }

    /**
     * Retourne tous les r�sultats pr�f�rentiels possibles pour un HS code.
     */
    public List<PreferentialResult> getAllPreferentialRates(
            String hsCode,
            String destCountry,
            double goodsValue,
            double valueAdded,
            double totalCost) {

        List<PreferentialResult> results = new ArrayList<>();
        List<TradeAgreement> agreements = agreementRepo.findByIsActiveTrue();

        for (TradeAgreement agreement : agreements) {
            try {
                PreferentialResult result = calculatePreferentialDuty(
                        hsCode, agreement.getPartnerCountry(), destCountry,
                        goodsValue, valueAdded, totalCost);
                if (result.isPreferential()) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("[PREF] Erreur calcul pr�f�rentiel pour {} -> {}: {}",
                        agreement.getPartnerCountry(), hsCode, e.getMessage());
            }
        }

        results.sort(Comparator.comparingDouble(PreferentialResult::savings).reversed());
        return results;
    }

    @Cacheable(value = "mfn-rates", key = "#hsCode + ':' + #origin + ':' + #dest")
    double getMfnRate(String hsCode, String origin, String dest) {
        List<TaricRate> rates = taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(
                hsCode, origin, dest);
        if (!rates.isEmpty()) {
            return rates.get(0).getDutyRate();
        }
        // Fallback: taux MFN simul� bas� sur le chapitre HS
        String chapter = hsCode.length() >= 2 ? hsCode.substring(0, 2) : "00";
        return getSimulatedMfnRate(chapter);
    }

    @Cacheable(value = "pref-rates", key = "#hsCode + ':' + #origin + ':' + #dest + ':' + #agreementCode")
    double getPreferentialRate(String hsCode, String origin, String dest, String agreementCode) {
        List<TaricRate> rates = taricRepo.findPreferentialRates(
                hsCode, origin, dest, true);
        if (!rates.isEmpty()) {
            return rates.get(0).getDutyRate();
        }
        // Fallback: taux pr�f�rentiel simul� (souvent 0% pour les accords UE)
        return 0.0;
    }

    private double getSimulatedMfnRate(String chapter) {
        Map<String, Double> mfnRates = Map.ofEntries(
            Map.entry("01", 8.3), Map.entry("02", 12.8), Map.entry("03", 7.5),
            Map.entry("04", 10.9), Map.entry("05", 6.5), Map.entry("06", 8.0),
            Map.entry("07", 10.4), Map.entry("08", 8.5), Map.entry("09", 6.0),
            Map.entry("10", 12.0), Map.entry("11", 9.2), Map.entry("12", 5.7),
            Map.entry("13", 4.0), Map.entry("14", 3.5), Map.entry("15", 10.5),
            Map.entry("16", 13.5), Map.entry("17", 15.0), Map.entry("18", 7.0),
            Map.entry("19", 9.5), Map.entry("20", 11.0), Map.entry("21", 8.5),
            Map.entry("22", 6.5), Map.entry("23", 5.0), Map.entry("24", 57.0),
            Map.entry("25", 3.0), Map.entry("26", 0.0), Map.entry("27", 2.5),
            Map.entry("28", 5.5), Map.entry("29", 5.5), Map.entry("30", 0.0),
            Map.entry("31", 4.0), Map.entry("32", 6.5), Map.entry("33", 0.0),
            Map.entry("34", 4.5), Map.entry("35", 7.0), Map.entry("36", 6.5),
            Map.entry("37", 0.0), Map.entry("38", 6.0), Map.entry("39", 6.5),
            Map.entry("40", 3.5), Map.entry("41", 3.0), Map.entry("42", 8.0),
            Map.entry("43", 4.5), Map.entry("44", 3.0), Map.entry("45", 5.5),
            Map.entry("46", 4.0), Map.entry("47", 0.0), Map.entry("48", 1.5),
            Map.entry("49", 2.0), Map.entry("50", 5.0), Map.entry("51", 4.0),
            Map.entry("52", 7.0), Map.entry("53", 3.5), Map.entry("54", 8.0),
            Map.entry("55", 8.0), Map.entry("56", 5.0), Map.entry("57", 6.5),
            Map.entry("58", 6.5), Map.entry("59", 6.0), Map.entry("60", 9.0),
            Map.entry("61", 12.0), Map.entry("62", 12.0), Map.entry("63", 10.0),
            Map.entry("64", 17.0), Map.entry("65", 3.0), Map.entry("66", 3.5),
            Map.entry("67", 3.5), Map.entry("68", 2.5), Map.entry("69", 5.0),
            Map.entry("70", 5.0), Map.entry("71", 2.5), Map.entry("72", 2.0),
            Map.entry("73", 2.5), Map.entry("74", 3.5), Map.entry("75", 2.0),
            Map.entry("76", 5.0), Map.entry("78", 3.5), Map.entry("79", 3.5),
            Map.entry("80", 2.0), Map.entry("81", 2.5), Map.entry("82", 2.5),
            Map.entry("83", 2.5), Map.entry("84", 1.8), Map.entry("85", 1.4),
            Map.entry("86", 1.5), Map.entry("87", 6.5), Map.entry("88", 2.5),
            Map.entry("89", 2.5), Map.entry("90", 2.5), Map.entry("91", 4.0),
            Map.entry("92", 3.0), Map.entry("93", 0.0), Map.entry("94", 3.5),
            Map.entry("95", 4.5), Map.entry("96", 5.0), Map.entry("97", 0.0)
        );
        return mfnRates.getOrDefault(chapter, 3.5);
    }
}
