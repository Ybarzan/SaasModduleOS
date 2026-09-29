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

        // Aucun taux TARIC réel : on ne chiffre pas d'économie (avant, MFN = moyenne de chapitre
        // inventée et préférentiel supposé à 0 % → « économies » fictives).
        if (Double.isNaN(mfnRate) || Double.isNaN(prefRate)) {
            return new PreferentialResult(
                    false, originResult.agreementCode(), originResult.agreementName(), 0, 0, 0,
                    originResult.criterionUsed() != null ? originResult.criterionUsed().name() : null,
                    originResult.explanation() + " — taux TARIC non disponibles pour " + hsCode
                        + ", économie non chiffrable (rapprochement transitaire requis).",
                    originResult.valueAddedPercentage(), true);
        }

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

    /** Taux MFN réel (TARIC, non préférentiel), ou NaN s'il n'y en a pas — jamais de moyenne inventée. */
    @Cacheable(value = "mfn-rates", key = "#hsCode + ':' + #origin + ':' + #dest")
    double getMfnRate(String hsCode, String origin, String dest) {
        List<TaricRate> rates = taricRepo.findPreferentialRates(hsCode, origin, dest, false);
        return rates.isEmpty() ? Double.NaN : rates.get(0).getDutyRate();
    }

    /** Taux préférentiel réel, ou NaN s'il n'y en a pas (avant : 0 % supposé). */
    @Cacheable(value = "pref-rates", key = "#hsCode + ':' + #origin + ':' + #dest + ':' + #agreementCode")
    double getPreferentialRate(String hsCode, String origin, String dest, String agreementCode) {
        List<TaricRate> rates = taricRepo.findPreferentialRates(hsCode, origin, dest, true);
        return rates.isEmpty() ? Double.NaN : rates.get(0).getDutyRate();
    }
}
