package com.incokalk.service;

import com.incokalk.dto.taric.TaricMeasureDto;
import com.incokalk.model.TaricRate;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import com.incokalk.service.taric.TaricApiClient;
import com.incokalk.service.taric.TaricSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomsDutyService {

    private final TaricRateRepository taricRepo;
    private final TradeAgreementRepository agreementRepo;
    private final TaricApiClient taricApiClient;
    private final TaricSyncService taricSyncService;

    private static final Set<String> EU = Set.of(
        "FR","DE","IT","ES","PT","NL","BE","LU","AT","FI","SE","DK","IE","GR",
        "PL","CZ","SK","HU","RO","BG","HR","SI","EE","LV","LT","CY","MT"
    );

    /** Code pays des mesures erga omnes (toutes origines) dans taric_rates. */
    private static final String ERGA_OMNES = "XX";

    /** Base de calcul du droit : valeur CIF, méthode de l'Union (art. 70 Règl. d'exécution 2015/2447). */
    public static final String BASIS_CIF_EU = "CIF_EU";
    public static final String CURRENCY = "EUR";

    /** Au-delà, un taux « ad valorem » est presque sûrement un droit spécifique (€/t, €/100 kg) mal encodé. */
    static final double MAX_PLAUSIBLE_AD_VALOREM = 100.0;

    /**
     * @param rateAvailable false = aucun taux fiable pour ce code/cette lane : dutyAmount vaut 0 mais
     *                      NE DOIT PAS être lu comme « 0 % de droits » (la raison est dans notes).
     * @param basisType     base de calcul (BASIS_CIF_EU), null si indisponible
     * @param currency      devise de dutyAmount / savings
     */
    public record DutyResult(
        double dutyAmount,
        double dutyRate,
        String dutyType,
        boolean isPrefential,
        String agreementCode,
        String agreementName,
        double mfnRate,
        double savings,
        String notes,
        boolean rateAvailable,
        String basisType,
        String currency
    ) implements Serializable {
        /** Résultat calculé (taux trouvé) — base CIF UE, en EUR. */
        public DutyResult(double dutyAmount, double dutyRate, String dutyType, boolean isPrefential,
                          String agreementCode, String agreementName, double mfnRate, double savings,
                          String notes) {
            this(dutyAmount, dutyRate, dutyType, isPrefential, agreementCode, agreementName,
                mfnRate, savings, notes, true, BASIS_CIF_EU, CURRENCY);
        }

        public static DutyResult unavailable(String reason) {
            return new DutyResult(0.0, 0.0, "UNAVAILABLE", false, null, null, 0.0, 0.0,
                "Données tarifaires non disponibles — rapprochement transitaire requis. " + reason,
                false, null, CURRENCY);
        }
    }

    public double calculate(String hsCode, String origin, String dest,
                             double goodsValue, double freight, double insurance) {
        return calculate(hsCode, origin, dest, goodsValue, freight, insurance, 0.0, null);
    }

    /** Montant seul. Vaut 0 si le taux est indisponible : préférer calculateDetailed + rateAvailable. */
    public double calculate(String hsCode, String origin, String dest,
                             double goodsValue, double freight, double insurance,
                             double weightKg, Double quantity) {
        DutyResult result = calculateDetailed(hsCode, origin, dest, goodsValue, freight, insurance, weightKg, quantity);
        return result.dutyAmount();
    }

    @Cacheable("customs-duties-detailed-v2")
    public DutyResult calculateDetailed(String hsCode, String origin, String dest,
                                         double goodsValue, double freight, double insurance) {
        return calculateDetailed(hsCode, origin, dest, goodsValue, freight, insurance, 0.0, null);
    }

    @Cacheable("customs-duties-detailed-v2")
    public DutyResult calculateDetailed(String hsCode, String origin, String dest,
                                         double goodsValue, double freight, double insurance,
                                         double weightKg, Double quantity) {
        if (EU.contains(origin.toUpperCase()) && EU.contains(dest.toUpperCase())) {
            return new DutyResult(0.0, 0.0, "NONE", false, null, null, 0.0, 0.0,
                "Commerce intracommunautaire — droits de douane = 0%");
        }

        String originUpper = origin.toUpperCase();
        String destUpper = dest.toUpperCase();

        // Seul le tarif douanier commun de l'UE (TARIC) est chargé. L'appliquer à une importation
        // aux États-Unis, en Chine ou au Royaume-Uni n'a pas de sens (autre nomenclature, autre base
        // de valeur) : mieux vaut « indisponible » qu'un chiffre crédible et faux.
        if (!EU.contains(destUpper)) {
            return DutyResult.unavailable("Aucun référentiel tarifaire chargé pour la destination "
                + destUpper + " (seul le TARIC de l'Union européenne est disponible).");
        }

        String digits = hsCode == null ? "" : hsCode.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return DutyResult.unavailable("Code HS manquant ou incomplet (4 chiffres minimum).");
        }

        ensureTaricDataLoaded(digits, originUpper, destUpper);

        LocalDate today = LocalDate.now();
        Optional<TaricRate> mfnOpt = bestCandidate(taricRepo.findRateCandidates(
            prefixes(digits), List.of(originUpper, ERGA_OMNES), EU, false, today), originUpper);
        if (mfnOpt.isEmpty()) {
            return DutyResult.unavailable("Aucun taux TARIC trouvé pour " + digits
                + " (origine " + originUpper + ").");
        }
        TaricRate mfn = mfnOpt.get();
        String mfnProblem = unusableReason(mfn);
        if (mfnProblem != null) {
            return DutyResult.unavailable(mfnProblem);
        }
        double mfnRate = mfn.getDutyRate();

        List<TaricRate> prefentialRates = taricRepo.findRateCandidates(
                prefixes(digits), List.of(originUpper), EU, true, today).stream()
            .filter(r -> unusableReason(r) == null)
            .toList();

        TaricRate bestPref = null;
        for (TaricRate pr : prefentialRates) {
            if (bestPref == null || pr.getDutyRate() < bestPref.getDutyRate()
                || (pr.getDutyRate() == bestPref.getDutyRate() && bestPref.getTradeAgreementCode() == null
                    && pr.getTradeAgreementCode() != null)) {
                bestPref = pr;
            }
        }

        String bestAgreementCode = bestPref != null ? bestPref.getTradeAgreementCode() : null;
        String bestAgreementName = null;
        if (bestAgreementCode != null) {
            Optional<TradeAgreement> agrOpt = agreementRepo.findByCode(bestAgreementCode);
            bestAgreementName = agrOpt.map(TradeAgreement::getName).orElse(bestAgreementCode);
        }

        boolean usePrefential = bestAgreementCode != null && bestPref.getDutyRate() <= mfnRate;
        double finalRate = usePrefential ? bestPref.getDutyRate() : mfnRate;

        String dutyType = "AD";
        double specificAmount = 0.0;
        String specificUnit = null;
        if (usePrefential && bestPref.getSpecificAmount() != null) {
            dutyType = "MIX";
            specificAmount = bestPref.getSpecificAmount();
            specificUnit = bestPref.getSpecificUnit();
        }

        // Taux en points de % (1.8 = 1,8 %) : montant = CIF × taux / 100, arrondi au centime.
        double cifValue = goodsValue + freight + insurance;
        double dutyAmount = Math.round(cifValue * finalRate) / 100.0;
        if (specificAmount > 0) {
            dutyAmount += computeSpecificDuty(specificAmount, specificUnit, weightKg, quantity);
        }

        double savings = Math.round(cifValue * (mfnRate - finalRate)) / 100.0;

        String notes = usePrefential
            ? String.format("Droit préférentiel applicable via %s (critère origine: %s)",
                bestAgreementName, bestPref.getPrefentialOriginCriteria())
            : "Droit MFN standard appliqué (TARIC " + mfn.getHsCode()
                + (ERGA_OMNES.equals(mfn.getOriginCountry()) ? ", erga omnes)" : ", origine " + mfn.getOriginCountry() + ")");

        return new DutyResult(
            dutyAmount, finalRate, dutyType,
            usePrefential, bestAgreementCode, bestAgreementName,
            mfnRate, Math.max(savings, 0.0), notes
        );
    }

    /** "847130" -> ["847130", "84713", "8471"] : du plus précis au moins précis (position à 4 chiffres). */
    static List<String> prefixes(String digits) {
        List<String> out = new ArrayList<>();
        for (int len = Math.min(digits.length(), 10); len >= 4; len--) {
            out.add(digits.substring(0, len));
        }
        return out;
    }

    /** Code le plus long d'abord, puis origine spécifique avant erga omnes, puis taux le plus bas. */
    private static Optional<TaricRate> bestCandidate(List<TaricRate> candidates, String origin) {
        return candidates.stream().min(Comparator
            .comparingInt((TaricRate r) -> -r.getHsCode().replace(".", "").length())
            .thenComparingInt(r -> origin.equals(r.getOriginCountry()) ? 0 : 1)
            .thenComparingDouble(TaricRate::getDutyRate));
    }

    /** null si le taux peut être appliqué en ad valorem, sinon la raison du refus. */
    private static String unusableReason(TaricRate r) {
        String type = r.getDutyType() == null ? "AD" : r.getDutyType().toUpperCase();
        if (!"AD".equals(type) && r.getSpecificAmount() == null) {
            return "Droit de type " + type + " (" + r.getDutyRate() + ") pour " + r.getHsCode()
                + " : droit spécifique non calculable automatiquement.";
        }
        if (r.getDutyRate() < 0 || r.getDutyRate() > MAX_PLAUSIBLE_AD_VALOREM) {
            return "Taux " + r.getDutyRate() + " % incohérent pour " + r.getHsCode()
                + " (probablement un droit spécifique €/t mal encodé) — vérification requise.";
        }
        return null;
    }

    /** Taux appliqué, ou NaN si aucun taux fiable (ne jamais afficher un taux inventé). */
    @Cacheable("customs-rate-v2")
    public double findRate(String hsCode, String origin, String dest) {
        DutyResult r = calculateDetailed(hsCode, origin, dest, 0, 0, 0);
        return r.rateAvailable() ? r.dutyRate() : Double.NaN;
    }

    public String getEUAgreement(String countryCode) {
        return agreementRepo.findByPartnerCountryAndIsActiveTrue(countryCode.toUpperCase())
            .stream().findFirst()
            .map(TradeAgreement::getName)
            .orElse(null);
    }

    public boolean isEU(String countryCode) {
        return EU.contains(countryCode.toUpperCase());
    }

    public boolean isIntraEU(String origin, String dest) {
        return EU.contains(origin.toUpperCase()) && EU.contains(dest.toUpperCase());
    }

    public Set<String> getEUCountries() {
        return Collections.unmodifiableSet(EU);
    }

    public List<TradeAgreement> findActiveAgreements() {
        return agreementRepo.findByIsActiveTrue();
    }

    public List<TradeAgreement> findAgreementsByCountry(String country) {
        return agreementRepo.findByPartnerCountryAndIsActiveTrue(country.toUpperCase());
    }

    public Optional<TradeAgreement> findAgreementByCode(String code) {
        return agreementRepo.findByCode(code);
    }

    public List<TradeAgreement> findAgreementsByChapter(String chapter) {
        return agreementRepo.findByHsChaptersCoveredContaining(chapter);
    }

    public Map<String, Object> searchTariff(String keyword, String dest) {
        List<TaricRate> rates = taricRepo.searchByKeyword(keyword.toLowerCase(), dest.toUpperCase(), LocalDate.now());
        List<String> hsCodes = taricRepo.findHsCodesByKeyword(keyword.toLowerCase(), dest.toUpperCase());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("keyword", keyword);
        response.put("destination", dest);
        response.put("hsCodesFound", hsCodes.size());
        response.put("hsCodes", hsCodes);
        response.put("rates", rates.stream().map(t -> Map.of(
            "hsCode", t.getHsCode(),
            "description", t.getDescription() != null ? t.getDescription() : "",
            "origin", t.getOriginCountry(),
            "dutyRate", t.getDutyRate(),
            "dutyType", t.getDutyType(),
            "isPrefential", t.isPrefential(),
            "agreementCode", t.getTradeAgreementCode() != null ? t.getTradeAgreementCode() : ""
        )).toList());
        return response;
    }

    public Map<String, Object> getTariffInfo(String hsCode, String origin, String dest) {
        DutyResult result = calculateDetailed(hsCode, origin, dest, 1000, 100, 10);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("hsCode", hsCode);
        info.put("origin", origin);
        info.put("destination", dest);
        info.put("rateAvailable", result.rateAvailable());
        info.put("mfnRate", result.rateAvailable() ? result.mfnRate() : null);
        info.put("appliedRate", result.rateAvailable() ? result.dutyRate() : null);
        info.put("isPrefential", result.isPrefential());
        info.put("agreement", result.agreementName());
        info.put("savings", result.savings());
        info.put("notes", result.notes());

        List<TradeAgreement> available = agreementRepo.findByPartnerCountryAndIsActiveTrue(origin.toUpperCase());
        info.put("availableAgreements", available.stream().map(a -> Map.of(
            "code", a.getCode(),
            "name", a.getName(),
            "type", a.getType().name()
        )).toList());

        return info;
    }

    private static final Pattern SPECIFIC_UNIT_FACTOR = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(.+)$");
    private static final Set<String> KG_UNITS = Set.of("KG", "KGM", "KILOGRAM", "KILOGRAMS", "KILOS");
    private static final Set<String> TON_UNITS = Set.of("TON", "TNE", "TONNE", "TONNES");
    private static final Set<String> LTR_UNITS = Set.of("LTR", "LT", "L", "LITER", "LITRE", "LITERS");

    private double computeSpecificDuty(double specificAmount, String unit, double weightKg, Double quantity) {
        if (specificAmount <= 0) return 0.0;
        if (unit == null || unit.isBlank()) {
            return quantity != null ? specificAmount * quantity : specificAmount;
        }
        String u = unit.trim().toUpperCase();
        double factor = 1.0;
        Matcher m = SPECIFIC_UNIT_FACTOR.matcher(u);
        if (m.matches()) {
            factor = Double.parseDouble(m.group(1));
            u = m.group(2).trim();
        }
        double qty;
        boolean available;
        if (KG_UNITS.contains(u)) {
            qty = weightKg;
            available = weightKg > 0;
            qty = qty / factor;
        } else if (TON_UNITS.contains(u)) {
            qty = weightKg / 1000.0;
            available = weightKg > 0;
            qty = qty / factor;
        } else if (LTR_UNITS.contains(u)) {
            qty = quantity != null ? quantity : 0.0;
            available = quantity != null;
            qty = qty / factor;
        } else {
            qty = quantity != null ? quantity : 1.0;
            available = quantity != null;
        }
        if (!available) {
            return specificAmount;
        }
        return specificAmount * qty;
    }

    /**
     * Charge les mesures depuis la vraie source TARIC si elle est branchée. En mode simulation,
     * TaricApiClient invente un taux par chapitre : on ne l'appelle pas, et surtout on ne le
     * persiste pas (avant correctif, ces taux « MFN simulés » étaient enregistrés dans
     * taric_rates et relus ensuite comme de vraies données, y compris pour FR→US).
     */
    private void ensureTaricDataLoaded(String hsCode, String origin, String dest) {
        if (taricApiClient.isSimulationMode()) return;
        if (hsCode == null || hsCode.length() < 4) return;
        boolean hasData = !taricRepo.findRateCandidates(prefixes(hsCode), List.of(origin, ERGA_OMNES),
            EU, false, LocalDate.now()).isEmpty();
        if (hasData) return;

        log.info("[TARIC] Aucune donnée en cache pour {} ({}->{}), appel API", hsCode, origin, dest);
        try {
            List<TaricMeasureDto> apiRates = taricApiClient.fetchRates(hsCode, origin, dest);
            if (!apiRates.isEmpty()) {
                taricSyncService.saveRates(apiRates);
                log.info("[TARIC] {} taux chargés depuis API pour {} ({}->{})",
                    apiRates.size(), hsCode, origin, dest);
            }
        } catch (Exception e) {
            log.warn("[TARIC] Erreur chargement API {} ({}->{}): {}",
                hsCode, origin, dest, e.getMessage());
        }
    }
}
