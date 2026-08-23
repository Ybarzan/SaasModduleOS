package com.incokalk.service;

import com.incokalk.model.VatRate;
import com.incokalk.repository.VatRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VatService {

    private static final Set<String> EU = Set.of(
        "FR","DE","IT","ES","PT","NL","BE","LU","AT","FI","SE","DK","IE","GR",
        "PL","CZ","SK","HU","RO","BG","HR","SI","EE","LV","LT","CY","MT"
    );

    private static final Map<String, Double> STANDARD_VAT = Map.ofEntries(
        Map.entry("FR", 20.0), Map.entry("DE", 19.0), Map.entry("IT", 22.0),
        Map.entry("ES", 21.0), Map.entry("NL", 21.0), Map.entry("BE", 21.0),
        Map.entry("PT", 23.0), Map.entry("PL", 23.0), Map.entry("AT", 20.0),
        Map.entry("IE", 23.0), Map.entry("FI", 24.0), Map.entry("SE", 25.0),
        Map.entry("DK", 25.0), Map.entry("GR", 24.0), Map.entry("CZ", 21.0),
        Map.entry("RO", 19.0), Map.entry("HU", 27.0), Map.entry("BG", 20.0),
        Map.entry("HR", 25.0), Map.entry("SI", 22.0), Map.entry("SK", 20.0),
        Map.entry("LT", 21.0), Map.entry("LV", 21.0), Map.entry("EE", 20.0),
        Map.entry("CY", 19.0), Map.entry("MT", 18.0), Map.entry("LU", 17.0),
        Map.entry("GB", 20.0)
    );

    private static final Map<String, List<Double>> REDUCED_VAT = Map.ofEntries(
        Map.entry("FR", List.of(5.5, 10.0)),
        Map.entry("DE", List.of(7.0)),
        Map.entry("IT", List.of(4.0, 5.0, 10.0)),
        Map.entry("ES", List.of(4.0, 10.0)),
        Map.entry("PT", List.of(6.0, 13.0)),
        Map.entry("NL", List.of(9.0)),
        Map.entry("BE", List.of(6.0, 12.0)),
        Map.entry("LU", List.of(3.0, 8.0)),
        Map.entry("AT", List.of(10.0, 13.0)),
        Map.entry("FI", List.of(10.0, 14.0)),
        Map.entry("SE", List.of(6.0, 12.0)),
        Map.entry("DK", List.of()),
        Map.entry("IE", List.of(9.0, 13.5)),
        Map.entry("GR", List.of(6.0, 13.0)),
        Map.entry("PL", List.of(5.0, 8.0)),
        Map.entry("CZ", List.of(10.0, 15.0)),
        Map.entry("SK", List.of(10.0)),
        Map.entry("HU", List.of(5.0, 18.0)),
        Map.entry("RO", List.of(5.0, 9.0)),
        Map.entry("BG", List.of(9.0)),
        Map.entry("HR", List.of(5.0, 13.0)),
        Map.entry("SI", List.of(5.0, 9.5)),
        Map.entry("EE", List.of(9.0)),
        Map.entry("LV", List.of(5.0, 12.0)),
        Map.entry("LT", List.of(5.0, 9.0)),
        Map.entry("CY", List.of(5.0, 9.0)),
        Map.entry("MT", List.of(5.0, 7.0)),
        Map.entry("GB", List.of(5.0))
    );

    private static final Pattern VIES_FORMAT = Pattern.compile("^[A-Z]{2}[0-9]{8,12}$");

    private final VatRateRepository vatRateRepository;

    @Autowired(required = false)
    private ViesClient viesClient;

    public record VatResult(
        double vatAmount,
        double vatRate,
        String vatType,
        String regime,
        boolean reverseCharge,
        boolean isExempt,
        String notes
    ) implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record ViesResult(
        String vatNumber,
        boolean valid,
        boolean formatValid,
        String country,
        String message,
        String holderName,
        String holderAddress
    ) {}

    @Cacheable("vat-calculation")
    public VatResult calculate(String originCountry, String destCountry,
                                double goodsValue, double freight, double insurance,
                                String incoterm, boolean isB2B) {
        String origin = originCountry.toUpperCase();
        String dest = destCountry.toUpperCase();

        if (EU.contains(origin) && EU.contains(dest)) {
            if (origin.equals(dest)) {
                return domesticVat(dest, goodsValue, incoterm);
            } else if (isB2B) {
                return intraCommunityB2B(dest, goodsValue, freight, insurance, incoterm);
            } else {
                return intraCommunityB2C(dest, goodsValue, freight, insurance, incoterm);
            }
        }

        if (!EU.contains(origin) && EU.contains(dest)) {
            return importVat(dest, goodsValue, freight, insurance, incoterm, isB2B);
        }

        if (EU.contains(origin) && !EU.contains(dest)) {
            return exportVat(dest, goodsValue, incoterm);
        }

        return exportOutsideEU(dest, goodsValue, incoterm);
    }

    public double getStandardRate(String countryCode) {
        String code = countryCode.toUpperCase();
        Optional<VatRate> dbRate = vatRateRepository
            .findFirstByCountryCodeAndRateTypeAndIsActiveTrueOrderByValidFromDesc(code, VatRate.RateType.STANDARD);
        if (dbRate.isPresent()) {
            return dbRate.get().getRate();
        }
        return STANDARD_VAT.getOrDefault(code, 20.0);
    }

    public boolean isEU(String countryCode) {
        return EU.contains(countryCode.toUpperCase());
    }

    public Map<String, Double> getAllStandardRates() {
        return Map.copyOf(STANDARD_VAT);
    }

    public Map<String, List<Double>> getAllReducedRates() {
        Map<String, List<Double>> result = new HashMap<>(REDUCED_VAT);
        List<VatRate> dbReduced = vatRateRepository.findByRateTypeAndIsActiveTrue(VatRate.RateType.REDUCED);
        Map<String, List<Double>> dbByCountry = new HashMap<>();
        for (VatRate vr : dbReduced) {
            dbByCountry.computeIfAbsent(vr.getCountryCode(), k -> new ArrayList<>()).add(vr.getRate());
        }
        if (!dbByCountry.isEmpty()) {
            result.putAll(dbByCountry);
        }
        return Collections.unmodifiableMap(result);
    }

    public ViesResult validateVies(String vatNumber) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return new ViesResult(vatNumber, false, false, null, "VAT number is empty", null, null);
        }

        String cleaned = vatNumber.trim().toUpperCase();
        boolean formatValid = VIES_FORMAT.matcher(cleaned).matches();

        if (!formatValid) {
            return new ViesResult(cleaned, false, false, null,
                "Invalid format. Expected: 2-letter country prefix + 8-12 digits (e.g. FR12345678901)", null, null);
        }

        String country = cleaned.substring(0, 2);
        boolean countryValid = EU.contains(country);

        if (!countryValid) {
            return new ViesResult(cleaned, false, true, country,
                "Country prefix '" + country + "' is not an EU member state", null, null);
        }

        if (viesClient != null) {
            ViesClient.ViesCheck online = viesClient.checkVat(cleaned);
            if (online.message() == null) {
                String msg = online.valid()
                    ? "Numéro de TVA valide (vérifié en ligne via VIES)"
                    : "Numéro de TVA non valide (rejeté par VIES)";
                return new ViesResult(cleaned, online.valid(), true, country, msg,
                    online.name(), online.address());
            }
            if (online.message().contains("disabled")) {
                return new ViesResult(cleaned, true, true, country,
                    "Format valid. VIES online validation disabled — format check passed for " + country, null, null);
            }
            return new ViesResult(cleaned, true, true, country,
                "Format valid. Vérification VIES en ligne indisponible (" + online.message() + ")", null, null);
        }

        return new ViesResult(cleaned, true, true, country,
            "Format valid. Real VIES SOAP verification pending — format check passed for " + country, null, null);
    }

    public VatResult calculateMarginScheme(String countryCode, double sellingPrice,
                                            double purchasePrice, boolean isB2B) {
        String code = countryCode.toUpperCase();
        double rate = getStandardRate(code);
        double margin = Math.max(0, sellingPrice - purchasePrice);
        double vat = Math.round(margin * rate / 100.0 * 100.0) / 100.0;

        String regime = isB2B ? "MARGIN_B2B_REVERSE_CHARGE" : "MARGIN_B2C";

        if (isB2B) {
            return new VatResult(vat, rate, "MARGIN", regime, true, false,
                String.format("Margin scheme B2B — VAT on margin %s€ (selling %s€ - purchase %s€) at %s%%. " +
                    "Reverse charge applies.", String.format("%.2f", margin),
                    String.format("%.2f", sellingPrice), String.format("%.2f", purchasePrice), rate));
        } else {
            return new VatResult(vat, rate, "MARGIN", regime, false, false,
                String.format("Margin scheme B2C — VAT on margin %s€ (selling %s€ - purchase %s€) at %s%%. " +
                    "Second-hand goods, art, antiques.", String.format("%.2f", margin),
                    String.format("%.2f", sellingPrice), String.format("%.2f", purchasePrice), rate));
        }
    }

    private VatResult domesticVat(String country, double goodsValue, String incoterm) {
        double rate = STANDARD_VAT.getOrDefault(country, 20.0);
        double base = goodsValue;
        if ("CIF".equalsIgnoreCase(incoterm) || "CIP".equalsIgnoreCase(incoterm) ||
            "DDP".equalsIgnoreCase(incoterm) || "DAP".equalsIgnoreCase(incoterm) ||
            "DPU".equalsIgnoreCase(incoterm)) {
            base = goodsValue;
        }
        double vat = Math.round(base * rate / 100.0 * 100.0) / 100.0;
        return new VatResult(vat, rate, "STANDARD", "DOMESTIC", false, false,
            String.format("TVA nationale %s — taux standard %s%%", country, rate));
    }

    private VatResult intraCommunityB2B(String destCountry, double goodsValue,
                                         double freight, double insurance,
                                         String incoterm) {
        double rate = STANDARD_VAT.getOrDefault(destCountry, 20.0);
        double base = goodsValue + freight + insurance;
        double vat = 0.0;

        return new VatResult(vat, rate, "STANDARD", "IC_B2B_REVERSE_CHARGE", true, false,
            String.format("Intracom B2B — Reverse charge (article 196 Directive 2006/112/CE). " +
                "Aucune TVA due à l'acquisition : auto-liquidation par l'acquéreur. " +
                "Base de référence: %s€, taux %s%%", String.format("%.2f", base), rate));
    }

    private VatResult intraCommunityB2C(String destCountry, double goodsValue,
                                          double freight, double insurance,
                                          String incoterm) {
        double rate = STANDARD_VAT.getOrDefault(destCountry, 20.0);
        double base = goodsValue + freight + insurance;
        double vat = Math.round(base * rate / 100.0 * 100.0) / 100.0;

        return new VatResult(vat, rate, "STANDARD", "IC_B2C_OSS", false, false,
            String.format("Intracom B2C — OSS (One Stop Shop). Taux du pays de destination %s%%. " +
                "Utiliser le guichet unique OSS.", rate));
    }

    private VatResult importVat(String destCountry, double goodsValue,
                                 double freight, double insurance,
                                 String incoterm, boolean isB2B) {
        double rate = STANDARD_VAT.getOrDefault(destCountry, 20.0);
        double cifValue = goodsValue + freight + insurance;
        double vat = Math.round(cifValue * rate / 100.0 * 100.0) / 100.0;

        if (isB2B) {
            return new VatResult(vat, rate, "STANDARD", "IMPORT_TAI_REVERSE_CHARGE", true, false,
                String.format("Import B2B — TAI (Taxe Assise à l'Importation) via reverse charge. " +
                    "Taux %s%%. TVA deductible immédiatement si assujetti.", rate));
        } else {
            return new VatResult(vat, rate, "STANDARD", "IMPORT_TAI", false, false,
                String.format("Import B2C — TAI acquittée à l'importation. " +
                    "Taux %s%% sur valeur CIF %s€", rate, String.format("%.2f", cifValue)));
        }
    }

    private VatResult exportVat(String destCountry, double goodsValue, String incoterm) {
        return new VatResult(0.0, 0.0, "EXEMPT", "EXPORT", false, true,
            String.format("Export depuis l'EU vers %s — exonéré de TVA (article 146 CGI)", destCountry));
    }

    private VatResult exportOutsideEU(String destCountry, double goodsValue, String incoterm) {
        return new VatResult(0.0, 0.0, "EXEMPT", "EXPORT", false, true,
            String.format("Export hors EU vers %s — exonéré de TVA", destCountry));
    }
}
