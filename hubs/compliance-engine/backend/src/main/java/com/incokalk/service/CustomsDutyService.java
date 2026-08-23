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

    public record DutyResult(
        double dutyAmount,
        double dutyRate,
        String dutyType,
        boolean isPrefential,
        String agreementCode,
        String agreementName,
        double mfnRate,
        double savings,
        String notes
    ) implements Serializable {}

    public double calculate(String hsCode, String origin, String dest,
                             double goodsValue, double freight, double insurance) {
        return calculate(hsCode, origin, dest, goodsValue, freight, insurance, 0.0, null);
    }

    public double calculate(String hsCode, String origin, String dest,
                             double goodsValue, double freight, double insurance,
                             double weightKg, Double quantity) {
        DutyResult result = calculateDetailed(hsCode, origin, dest, goodsValue, freight, insurance, weightKg, quantity);
        return result.dutyAmount();
    }

    @Cacheable("customs-duties-detailed")
    public DutyResult calculateDetailed(String hsCode, String origin, String dest,
                                         double goodsValue, double freight, double insurance) {
        return calculateDetailed(hsCode, origin, dest, goodsValue, freight, insurance, 0.0, null);
    }

    @Cacheable("customs-duties-detailed")
    public DutyResult calculateDetailed(String hsCode, String origin, String dest,
                                         double goodsValue, double freight, double insurance,
                                         double weightKg, Double quantity) {
        if (EU.contains(origin.toUpperCase()) && EU.contains(dest.toUpperCase())) {
            return new DutyResult(0.0, 0.0, "NONE", false, null, null, 0.0, 0.0,
                "Commerce intracommunautaire — droits de douane = 0%");
        }

        double cifValue = goodsValue + freight + insurance;
        String originUpper = origin.toUpperCase();
        String destUpper = dest.toUpperCase();

        ensureTaricDataLoaded(hsCode, originUpper, destUpper);

        List<TaricRate> mfnRates = taricRepo.findMFNRates(hsCode, originUpper, destUpper, LocalDate.now());
        double mfnRate = mfnRates.isEmpty() ? fallbackRate(hsCode) : mfnRates.get(0).getDutyRate();

        List<TaricRate> prefentialRates = taricRepo.findPrefentialRates(hsCode, originUpper, destUpper, LocalDate.now());

        double bestPrefentialRate = Double.MAX_VALUE;
        String bestAgreementCode = null;
        String bestAgreementName = null;
        String bestOriginCriteria = null;

        for (TaricRate pr : prefentialRates) {
            if (pr.getDutyRate() < bestPrefentialRate ||
                (pr.getDutyRate() == bestPrefentialRate && bestAgreementCode == null && pr.getTradeAgreementCode() != null)) {
                bestPrefentialRate = pr.getDutyRate();
                bestAgreementCode = pr.getTradeAgreementCode();
                bestOriginCriteria = pr.getPrefentialOriginCriteria();
            }
        }

        if (bestAgreementCode != null) {
            Optional<TradeAgreement> agrOpt = agreementRepo.findByCode(bestAgreementCode);
            bestAgreementName = agrOpt.map(TradeAgreement::getName).orElse(bestAgreementCode);
        }

        boolean usePrefential = bestAgreementCode != null && bestPrefentialRate <= mfnRate;
        double finalRate = usePrefential ? bestPrefentialRate : mfnRate;

        String dutyType = "AD";
        double specificAmount = 0.0;
        String specificUnit = null;
        if (usePrefential && !prefentialRates.isEmpty()) {
            final double targetRate = bestPrefentialRate;
            TaricRate bestRate = prefentialRates.stream()
                .filter(t -> Double.compare(t.getDutyRate(), targetRate) == 0)
                .findFirst().orElse(null);
            if (bestRate != null && bestRate.getSpecificAmount() != null) {
                dutyType = "MIX";
                specificAmount = bestRate.getSpecificAmount();
                specificUnit = bestRate.getSpecificUnit();
            }
        }

        double dutyAmount = Math.round(cifValue * finalRate) / 100.0;
        if (specificAmount > 0) {
            dutyAmount += computeSpecificDuty(specificAmount, specificUnit, weightKg, quantity);
        }

        double savings = Math.round(cifValue * (mfnRate - finalRate)) / 100.0;

        String notes = usePrefential
            ? String.format("Droit préférentiel applicable via %s (critère origine: %s)", bestAgreementName, bestOriginCriteria)
            : "Droit MFN standard appliqué";

        return new DutyResult(
            dutyAmount, finalRate, dutyType,
            usePrefential, bestAgreementCode, bestAgreementName,
            mfnRate, Math.max(savings, 0.0), notes
        );
    }

    @Cacheable("customs-fallback-rate")
    public double findRate(String hsCode, String origin, String dest) {
        return calculateDetailed(hsCode, origin, dest, 0, 0, 0).dutyRate();
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
        info.put("mfnRate", result.mfnRate());
        info.put("appliedRate", result.dutyRate());
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

    private void ensureTaricDataLoaded(String hsCode, String origin, String dest) {
        if (hsCode == null || hsCode.length() < 2) return;
        boolean hasData = !taricRepo.findMFNRates(hsCode, origin, dest, LocalDate.now()).isEmpty()
            || !taricRepo.findPrefentialRates(hsCode, origin, dest, LocalDate.now()).isEmpty();
        if (hasData) return;

        log.info("[TARIC] Aucune donnée en cache pour {} ({}->{}), appel API", hsCode, origin, dest);
        try {
            List<TaricMeasureDto> apiRates = taricApiClient.fetchRates(hsCode, origin, dest);
            if (!apiRates.isEmpty()) {
                taricSyncService.saveRates(apiRates);
                log.info("[TARIC] {} taux chargés depuis API pour {} ({}->{})",
                    apiRates.size(), hsCode, origin, dest);
            } else {
                log.debug("[TARIC] Aucun taux trouvé via API, utilisation fallback");
            }
        } catch (Exception e) {
            log.warn("[TARIC] Erreur chargement API {} ({}->{}): {}",
                hsCode, origin, dest, e.getMessage());
        }
    }

    private double fallbackRate(String hsCode) {
        if (hsCode == null || hsCode.length() < 2) return 3.5;

        try {
            List<TaricMeasureDto> apiRates = taricApiClient.fetchRates(hsCode, "CN", "FR");
            if (!apiRates.isEmpty()) {
                double rate = apiRates.get(0).getDutyRate();
                if (rate > 0) return rate;
            }
        } catch (Exception ignored) {}

        Map<String, Double> fallbacks = Map.ofEntries(
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
        return fallbacks.getOrDefault(hsCode.substring(0, 2), 3.5);
    }
}
