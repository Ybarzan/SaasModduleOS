package com.incokalk.service.compliance;

import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.TradeAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulesOfOriginService {

    private final TradeAgreementRepository agreementRepo;

    // Origin criteria codes per EU trade agreement
    private static final Map<String, Set<String>> AGREEMENT_ORIGIN_CRITERIA = Map.of(
        "EVFTA", Set.of("WH", "WO", "PE", "CTH", "CTSH"),
        "CETA", Set.of("WH", "WO", "PE", "CTH", "CTSH"),
        "EUJEPA", Set.of("WH", "WO", "PE", "CTH"),
        "EUSFTA", Set.of("WH", "WO", "PE", "CTH"),
        "EUCLEA", Set.of("WH", "WO", "PE", "CTH"),
        "EUMXA", Set.of("WH", "WO", "PE", "CTH"),
        "EUSADC", Set.of("WH", "WO", "PE", "CTH"),
        "EURO-MED", Set.of("WH", "WO", "PE", "CTH")
    );

    // HS chapters where Wholly Obtained (WO) applies
    private static final Set<String> WO_CHAPTERS = Set.of(
        "01", "02", "03", "04", "05", "06", "07", "08", "09",
        "10", "11", "12", "13", "14", "15", "16", "17", "18",
        "19", "20", "21", "22", "23", "24", "25", "26", "27",
        "28", "29", "30", "31", "32", "33", "34", "35", "36",
        "37", "38", "39", "40", "41", "42", "43", "44", "45",
        "46", "47", "48", "49", "50", "51", "52", "53", "54",
        "55", "56", "57", "58", "59", "60", "61", "62", "63",
        "64", "65", "66", "67", "68", "69", "70", "71", "72",
        "73", "74", "75", "76", "77", "78", "79", "80", "81",
        "82", "83", "84", "85", "86", "87", "88", "89", "90",
        "91", "92", "93", "94", "95", "96", "97"
    );

    public enum OriginCriterion {
        WH,   // Wholly obtained
        WO,   // Wholly obtained (alternative)
        PE,   // Produced exclusively
        CTH,  // Change in tariff classification
        CTSH, // Change in tariff classification + value added
        CUM   // Origine obtenue par cumulation (bilatérale / régionale)
    }

    public record OriginVerificationResult(
        boolean isOriginating,
        OriginCriterion criterionUsed,
        String agreementCode,
        String agreementName,
        String explanation,
        double valueAddedPercentage,
        List<String> warnings
    ) {}

    /**
     * Vérifie si un produit est originaire d'un pays partenaire
     * selon les règles d'origine de l'accord commercial applicable.
     */
    public OriginVerificationResult verifyOrigin(
            String hsCode,
            String originCountry,
            String destCountry,
            double valueAdded,
            double totalCost,
            List<String> manufacturingSteps) {

        List<String> warnings = new java.util.ArrayList<>();

        // 1. Vérifier que le pays d'origine est éligible
        Optional<TradeAgreement> agreementOpt = agreementRepo
                .findByPartnerCountryAndIsActiveTrue(originCountry)
                .stream()
                .findFirst();

        if (agreementOpt.isEmpty()) {
            return new OriginVerificationResult(
                    false, null, null, null,
                    "Aucun accord commercial actif trouvé pour le pays d'origine : " + originCountry,
                    0.0, warnings);
        }

        TradeAgreement agreement = agreementOpt.get();
        String agreementCode = agreement.getCode();
        Set<String> criteria = AGREEMENT_ORIGIN_CRITERIA.getOrDefault(
                agreementCode, Set.of("WH", "WO", "PE", "CTH"));

        // 2. Vérifier le critère Wholly Obtained / Wholly Produced
        if (criteria.contains(OriginCriterion.WH.name()) || criteria.contains(OriginCriterion.WO.name())) {
            String chapter = hsCode.length() >= 2 ? hsCode.substring(0, 2) : "";
            if (WO_CHAPTERS.contains(chapter)) {
                return new OriginVerificationResult(
                        true, OriginCriterion.WO, agreementCode, agreement.getName(),
                        "Produit entièrement obtenu (WO) - chapitre HS " + chapter + " éligible",
                        100.0, warnings);
            }
        }

        // 3. Vérifier le critère Produced Exclusively (PE)
        if (criteria.contains(OriginCriterion.PE.name())) {
            if (manufacturingSteps != null && !manufacturingSteps.isEmpty()) {
                boolean allLocal = manufacturingSteps.stream()
                        .allMatch(step -> step.equalsIgnoreCase("local") || step.equalsIgnoreCase("fr"));
                if (allLocal) {
                    return new OriginVerificationResult(
                            true, OriginCriterion.PE, agreementCode, agreement.getName(),
                            "Produit fabriqué exclusivement sur le territoire PE",
                            100.0, warnings);
                } else {
                    warnings.add("Certaines étapes de fabrication ne sont pas locales (PE non applicable)");
                }
            }
        }

        // 4. Vérifier le critère Change in Tariff Classification (CTH)
        if (criteria.contains(OriginCriterion.CTH.name())) {
            if (manufacturingSteps != null && !manufacturingSteps.isEmpty()) {
                boolean hasClassificationChange = manufacturingSteps.stream()
                        .anyMatch(step -> step.toLowerCase().contains("transform") || step.toLowerCase().contains("manufacture"));
                if (hasClassificationChange) {
                    double va = totalCost > 0 ? (valueAdded / totalCost) * 100 : 0;
                    return new OriginVerificationResult(
                            true, OriginCriterion.CTH, agreementCode, agreement.getName(),
                            "Changement de classification tarifaire (CTH) validé - VA: " + String.format("%.1f", va) + "%",
                            va, warnings);
                }
            }
        }

        // 5. Vérifier le critère CTH + valeur ajoutée (CTSH)
        if (criteria.contains(OriginCriterion.CTSH.name())) {
            double va = totalCost > 0 ? (valueAdded / totalCost) * 100 : 0;
            boolean meetsVaThreshold = va >= 35.0;

            if (manufacturingSteps != null && !manufacturingSteps.isEmpty()) {
                boolean hasClassificationChange = manufacturingSteps.stream()
                        .anyMatch(step -> step.toLowerCase().contains("transform") || step.toLowerCase().contains("manufacture"));

                if (hasClassificationChange && meetsVaThreshold) {
                    return new OriginVerificationResult(
                            true, OriginCriterion.CTSH, agreementCode, agreement.getName(),
                            "CTH + VA (" + String.format("%.1f", va) + "%) >= 35% - Origine confirmée",
                            va, warnings);
                } else if (hasClassificationChange && !meetsVaThreshold) {
                    warnings.add("VA (" + String.format("%.1f", va) + "%) inférieure au seuil de 35% pour CTSH");
                }
            }

            // Même sans changement de classification, si VA >= 60% c'est suffisant pour certains accords
            if (va >= 60.0) {
                return new OriginVerificationResult(
                        true, OriginCriterion.CTSH, agreementCode, agreement.getName(),
                        "VA (" + String.format("%.1f", va) + "%) >= 60% - Origine confirmée (seuil élevé)",
                        va, warnings);
            }
        }

        // Si aucun critère n'est satisfait
        return new OriginVerificationResult(
                false, null, agreementCode, agreement.getName(),
                "Aucun critère d'origine satisfait pour ce produit. Vérifiez les étapes de fabrication.",
                0.0, warnings);
    }

    /**
     * Vérifie si un accord commercial est applicable pour un couple pays.
     */
    public Optional<TradeAgreement> getApplicableAgreement(String originCountry, String destCountry) {
        if (!"FR".equalsIgnoreCase(destCountry) && !"EU".equalsIgnoreCase(destCountry)) {
            return Optional.empty();
        }
        return agreementRepo.findByPartnerCountryAndIsActiveTrue(originCountry)
                .stream()
                .findFirst();
    }

    /**
     * Retourne les critères d'origine applicables pour un pays partenaire.
     */
    public Set<String> getApplicableCriteria(String originCountry) {
        return agreementRepo.findByPartnerCountryAndIsActiveTrue(originCountry)
                .stream()
                .findFirst()
                .map(a -> AGREEMENT_ORIGIN_CRITERIA.getOrDefault(a.getCode(), Set.of("WH", "WO", "PE", "CTH")))
                .orElse(Set.of());
    }

    /**
     * Calcule la valeur ajoutée pour un produit.
     */
    public double calculateValueAdded(double totalCost, double localCost) {
        if (totalCost <= 0) return 0.0;
        return ((totalCost - localCost) / totalCost) * 100;
    }
}
