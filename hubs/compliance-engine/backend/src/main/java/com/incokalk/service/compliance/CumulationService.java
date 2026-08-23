package com.incokalk.service.compliance;

import com.incokalk.model.CumulationGroup;
import com.incokalk.model.CumulationType;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.CumulationGroupRepository;
import com.incokalk.repository.TradeAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Logique de cumulation préférentielle : bilatérale (matières UE) et régionale
 * / diagonale (groupes de pays disposant de règles d'origine harmonisées, ex. ASEAN,
 * pan-euro-méditerranéen, EPA). Complète la vérification classique des règles d'origine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CumulationService {

    /** Seuil de valeur ajoutée par défaut (%), aligné sur le critère CTSH existant. */
    public static final double DEFAULT_VA_THRESHOLD_PCT = 35.0;

    private static final Set<String> EU_ORIGIN_COUNTRIES = Set.of(
        "FR", "DE", "IT", "ES", "PT", "NL", "BE", "LU", "AT", "FI", "SE", "DK", "IE",
        "GR", "PL", "CZ", "SK", "HU", "RO", "BG", "HR", "SI", "EE", "LV", "LT", "CY", "MT");

    private final TradeAgreementRepository agreementRepo;
    private final CumulationGroupRepository groupRepo;
    private final RulesOfOriginService rulesOfOriginService;

    /**
     * Matière entrant dans un produit. {@code isOriginating} indique si la matière
     * dispose déjà d'une preuve d'origine (certificat EUR.1, déclaration d'origine...).
     */
    public record Material(
            String hsCode,
            String originCountry,
            double value,
            boolean isOriginating,
            String description) {}

    public enum MaterialStatus {
        ORIGINATING,           // matière avec preuve d'origine
        LOCAL,                 // matière produite dans le pays exportateur
        CUMULATED_BILATERAL,   // matière originaire UE, cumulée bilatéralement
        CUMULATED_REGIONAL,    // matière originaire d'un membre du groupe de cumul
        NON_ORIGINATING        // matière non cumulable
    }

    public record MaterialAssessment(
            String originCountry,
            double value,
            boolean isOriginating,
            MaterialStatus status,
            String reason) {}

    public record CumulationResult(
            boolean qualifies,
            String agreementCode,
            String agreementName,
            CumulationType cumulationType,
            String groupCode,
            String groupName,
            double originatingContentValue,
            double nonOriginatingValue,
            double originatingContentPct,
            double valueAddedPct,
            double thresholdPct,
            List<MaterialAssessment> materials,
            List<String> warnings,
            String explanation) {}

    /**
     * Évalue si un produit peut obtenir l'origine préférentielle par cumulation.
     *
     * @param hsCode        code SH du produit fini
     * @param originCountry pays exportateur (pays partenaire de l'accord)
     * @param destCountry   pays de destination (UE, typiquement FR)
     * @param valueAdded    valeur ajoutée locale (main-d'œuvre, coûts de transformation)
     * @param totalCost     coût total du produit fini
     * @param materials     liste des matières et composants entrants
     */
    public CumulationResult assess(
            String hsCode,
            String originCountry,
            String destCountry,
            double valueAdded,
            double totalCost,
            List<Material> materials) {

        List<String> warnings = new ArrayList<>();
        Optional<TradeAgreement> agreementOpt = agreementRepo
                .findByPartnerCountryAndIsActiveTrue(norm(originCountry))
                .stream()
                .findFirst();

        if (agreementOpt.isEmpty()) {
            return new CumulationResult(
                    false, null, null, CumulationType.NONE, null, null,
                    0, 0, 0, pct(valueAdded, totalCost), DEFAULT_VA_THRESHOLD_PCT,
                    List.of(), warnings,
                    "Aucun accord commercial actif pour le pays exportateur " + originCountry);
        }

        TradeAgreement agreement = agreementOpt.get();
        CumulationType type = agreement.getCumulationType() == null
                ? CumulationType.NONE : agreement.getCumulationType();
        double threshold = agreement.getVaThresholdPct() != null
                ? agreement.getVaThresholdPct() : DEFAULT_VA_THRESHOLD_PCT;

        CumulationGroup group = resolveGroup(agreement, type);

        List<MaterialAssessment> assessments = new ArrayList<>();
        double originatingContentValue = 0.0;
        double nonOriginatingValue = 0.0;

        for (Material material : materials) {
            MaterialAssessment ma = classify(material, originCountry, type, group, warnings);
            assessments.add(ma);
            switch (ma.status()) {
                case ORIGINATING, LOCAL, CUMULATED_BILATERAL, CUMULATED_REGIONAL ->
                    originatingContentValue += ma.value();
                case NON_ORIGINATING -> nonOriginatingValue += ma.value();
            }
        }

        double valueAddedPct = pct(valueAdded, totalCost);
        double originatingContentPct = pct(valueAdded + originatingContentValue, totalCost);
        boolean qualifies = type != CumulationType.NONE && originatingContentPct >= threshold;

        String typeLabel = typeLabel(type);
        StringBuilder explanation = new StringBuilder();
        if (type == CumulationType.NONE) {
            explanation.append("Aucune cumulation prévue par l'accord ")
                    .append(agreement.getCode()).append(".");
        } else {
            explanation.append("Cumulation ").append(typeLabel)
                    .append(" accordée par ").append(agreement.getCode());
            if (group != null) {
                explanation.append(" — groupe ").append(group.getName());
            }
            explanation.append(". Contenu originaire/cumulé : ")
                    .append(String.format(Locale.FRENCH, "%.1f%%", originatingContentPct))
                    .append(" (seuil : ").append(String.format(Locale.FRENCH, "%.0f%%", threshold)).append(").");
        }

        return new CumulationResult(
                qualifies,
                agreement.getCode(),
                agreement.getName(),
                type,
                group != null ? group.getCode() : null,
                group != null ? group.getName() : null,
                originatingContentValue,
                nonOriginatingValue,
                originatingContentPct,
                valueAddedPct,
                threshold,
                assessments,
                warnings,
                explanation.toString());
    }

    /**
     * Vérification d'origine intégrée : applique d'abord les règles classiques
     * (WO/PE/CTH/CTSH), puis, en cas d'échec, tente l'origine par cumulation.
     */
    public RulesOfOriginService.OriginVerificationResult verifyWithCumulation(
            String hsCode,
            String originCountry,
            String destCountry,
            double valueAdded,
            double totalCost,
            List<String> manufacturingSteps,
            List<Material> materials) {

        RulesOfOriginService.OriginVerificationResult base = rulesOfOriginService.verifyOrigin(
                hsCode, originCountry, destCountry, valueAdded, totalCost, manufacturingSteps);

        if (base.isOriginating()) {
            return base;
        }

        CumulationResult cumulation = assess(
                hsCode, originCountry, destCountry, valueAdded, totalCost, materials);

        if (cumulation.qualifies()) {
            return new RulesOfOriginService.OriginVerificationResult(
                    true,
                    RulesOfOriginService.OriginCriterion.CUM,
                    cumulation.agreementCode(),
                    cumulation.agreementName(),
                    cumulation.explanation(),
                    cumulation.originatingContentPct(),
                    new ArrayList<>(cumulation.warnings()));
        }

        List<String> mergedWarnings = new ArrayList<>(base.warnings());
        mergedWarnings.addAll(cumulation.warnings());
        if (!cumulation.explanation().isBlank()) {
            mergedWarnings.add(cumulation.explanation());
        }
        return new RulesOfOriginService.OriginVerificationResult(
                false, null, base.agreementCode(), base.agreementName(),
                "Origine non confirmée, y compris par cumulation : "
                        + base.explanation(),
                0.0, mergedWarnings);
    }

    private MaterialAssessment classify(
            Material material,
            String exporterCountry,
            CumulationType type,
            CumulationGroup group,
            List<String> warnings) {

        String origin = norm(material.originCountry());
        String exporter = norm(exporterCountry);

        if (origin.equals(exporter)) {
            return new MaterialAssessment(material.originCountry(), material.value(), false,
                    MaterialStatus.LOCAL, "Matière produite dans le pays exportateur");
        }

        boolean euOrigin = EU_ORIGIN_COUNTRIES.contains(origin);
        boolean inGroup = group != null && group.getMemberCountries() != null
                && group.getMemberCountries().stream().anyMatch(m -> norm(m).equals(origin));

        if (material.isOriginating()) {
            if ((type == CumulationType.DIAGONAL || type == CumulationType.FULL) && inGroup) {
                return new MaterialAssessment(material.originCountry(), material.value(), true,
                        MaterialStatus.CUMULATED_REGIONAL,
                        "Matière originaire d'un membre du groupe " + group.getCode()
                                + " (cumul régional)");
            }
            return new MaterialAssessment(material.originCountry(), material.value(), true,
                    MaterialStatus.ORIGINATING, "Matière avec preuve d'origine");
        }

        if ((type == CumulationType.BILATERAL || type == CumulationType.FULL) && euOrigin) {
            return new MaterialAssessment(material.originCountry(), material.value(), false,
                    MaterialStatus.CUMULATED_BILATERAL,
                    "Matière originaire UE cumulée bilatéralement");
        }

        if ((type == CumulationType.DIAGONAL || type == CumulationType.FULL) && inGroup) {
            warnings.add("Matière de " + material.originCountry() + " (" + group.getCode()
                    + ") : preuve d'origine requise pour la cumulation régionale — traitée comme non originaire.");
        }

        return new MaterialAssessment(material.originCountry(), material.value(), false,
                MaterialStatus.NON_ORIGINATING, "Matière non originaire / non cumulable");
    }

    private CumulationGroup resolveGroup(TradeAgreement agreement, CumulationType type) {
        if (type != CumulationType.DIAGONAL && type != CumulationType.FULL) {
            return null;
        }
        if (agreement.getCumulationGroupCode() == null) {
            return null;
        }
        return groupRepo.findByCode(agreement.getCumulationGroupCode()).orElse(null);
    }

    private static double pct(double value, double total) {
        return total > 0 ? (value / total) * 100.0 : 0.0;
    }

    private static String norm(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String typeLabel(CumulationType type) {
        return switch (type) {
            case BILATERAL -> "bilatérale";
            case DIAGONAL -> "régionale (diagonale)";
            case FULL -> "étendue";
            default -> "";
        };
    }
}
