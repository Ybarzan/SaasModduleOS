package com.incokalk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FrenchFiscalService {

    public record FrenchDutyBreakdown(
            String customsDutyCode,
            BigDecimal customsDutyAmount,
            BigDecimal customsDutyRate,
            String taiCode,
            BigDecimal taiAmount,
            BigDecimal taiRate,
            String acciseCode,
            BigDecimal acciseAmount,
            BigDecimal acciseRate,
            BigDecimal safeguardDutyAmount,
            BigDecimal safeguardRate,
            BigDecimal totalDuties,
            String regime,
            String notes
    ) {}

    public record TAIProduct(String hsCode, String description, BigDecimal rate, boolean isSpecific, BigDecimal specificAmountPerKg) {}

    private static final Map<String, BigDecimal> TAI_RATES = Map.ofEntries(
            Map.entry("220300", new BigDecimal("31.76")),
            Map.entry("220410", new BigDecimal("31.76")),
            Map.entry("220421", new BigDecimal("31.76")),
            Map.entry("220430", new BigDecimal("31.76")),
            Map.entry("220510", new BigDecimal("31.76")),
            Map.entry("220590", new BigDecimal("31.76")),
            Map.entry("220600", new BigDecimal("31.76")),
            Map.entry("220710", new BigDecimal("31.76")),
            Map.entry("220720", new BigDecimal("31.76")),
            Map.entry("220830", new BigDecimal("17.72")),
            Map.entry("220840", new BigDecimal("17.72")),
            Map.entry("220850", new BigDecimal("17.72")),
            Map.entry("220860", new BigDecimal("17.72")),
            Map.entry("220870", new BigDecimal("17.72")),
            Map.entry("220890", new BigDecimal("17.72")),
            Map.entry("240210", new BigDecimal("97.20")),
            Map.entry("240220", new BigDecimal("97.20")),
            Map.entry("270900", BigDecimal.ZERO),
            Map.entry("271012", BigDecimal.ZERO),
            Map.entry("271019", BigDecimal.ZERO)
    );

    private static final Map<String, BigDecimal> ACCISES = Map.ofEntries(
            Map.entry("220300", new BigDecimal("31.76")),
            Map.entry("220410", new BigDecimal("16.30")),
            Map.entry("220421", new BigDecimal("16.30")),
            Map.entry("220430", new BigDecimal("16.30")),
            Map.entry("220510", new BigDecimal("16.30")),
            Map.entry("220590", new BigDecimal("16.30")),
            Map.entry("220830", new BigDecimal("23.57")),
            Map.entry("220840", new BigDecimal("23.57")),
            Map.entry("220850", new BigDecimal("23.57")),
            Map.entry("220860", new BigDecimal("23.57")),
            Map.entry("220870", new BigDecimal("23.57")),
            Map.entry("220890", new BigDecimal("23.57")),
            Map.entry("240210", new BigDecimal("59.88")),
            Map.entry("240220", new BigDecimal("59.88"))
    );

    private static final Map<String, BigDecimal> SAFEGUARD_DUTIES = Map.ofEntries(
            Map.entry("7203", new BigDecimal("25.0")),
            Map.entry("7207", new BigDecimal("18.0")),
            Map.entry("7208", new BigDecimal("25.0")),
            Map.entry("7209", new BigDecimal("25.0")),
            Map.entry("7210", new BigDecimal("25.0")),
            Map.entry("7211", new BigDecimal("25.0")),
            Map.entry("7212", new BigDecimal("25.0")),
            Map.entry("7213", new BigDecimal("25.0")),
            Map.entry("7214", new BigDecimal("25.0")),
            Map.entry("7215", new BigDecimal("25.0")),
            Map.entry("7217", new BigDecimal("25.0")),
            Map.entry("7218", new BigDecimal("25.0")),
            Map.entry("7219", new BigDecimal("25.0")),
            Map.entry("7220", new BigDecimal("25.0")),
            Map.entry("7221", new BigDecimal("25.0")),
            Map.entry("7222", new BigDecimal("25.0")),
            Map.entry("7223", new BigDecimal("25.0")),
            Map.entry("7224", new BigDecimal("25.0")),
            Map.entry("7225", new BigDecimal("25.0")),
            Map.entry("7226", new BigDecimal("25.0")),
            Map.entry("7227", new BigDecimal("25.0")),
            Map.entry("7228", new BigDecimal("25.0")),
            Map.entry("7229", new BigDecimal("25.0")),
            Map.entry("7301", new BigDecimal("15.0")),
            Map.entry("7302", new BigDecimal("15.0")),
            Map.entry("7303", new BigDecimal("15.0")),
            Map.entry("7304", new BigDecimal("15.0")),
            Map.entry("7305", new BigDecimal("15.0")),
            Map.entry("7306", new BigDecimal("15.0")),
            Map.entry("7307", new BigDecimal("15.0")),
            Map.entry("7308", new BigDecimal("15.0")),
            Map.entry("7309", new BigDecimal("15.0")),
            Map.entry("7310", new BigDecimal("15.0")),
            Map.entry("7311", new BigDecimal("15.0")),
            Map.entry("7312", new BigDecimal("15.0")),
            Map.entry("7313", new BigDecimal("15.0")),
            Map.entry("7314", new BigDecimal("15.0")),
            Map.entry("7315", new BigDecimal("15.0")),
            Map.entry("7316", new BigDecimal("15.0")),
            Map.entry("7317", new BigDecimal("15.0")),
            Map.entry("7318", new BigDecimal("15.0")),
            Map.entry("7319", new BigDecimal("15.0")),
            Map.entry("7320", new BigDecimal("15.0")),
            Map.entry("7321", new BigDecimal("15.0")),
            Map.entry("7322", new BigDecimal("15.0")),
            Map.entry("7323", new BigDecimal("15.0")),
            Map.entry("7324", new BigDecimal("15.0")),
            Map.entry("7325", new BigDecimal("15.0")),
            Map.entry("7326", new BigDecimal("15.0"))
    );

    public FrenchDutyBreakdown calculateFrenchDuties(
            String hsCode, BigDecimal cifValue, BigDecimal netWeightKg,
            String originCountry, String destinationCountry, BigDecimal mfnDutyRate) {

        String sixDigitHs = hsCode.length() >= 6 ? hsCode.substring(0, 6) : hsCode;
        String fourDigitHs = hsCode.length() >= 4 ? hsCode.substring(0, 4) : hsCode;

        BigDecimal customsDutyAmount = BigDecimal.ZERO;
        BigDecimal customsDutyRate = mfnDutyRate != null ? mfnDutyRate : BigDecimal.ZERO;
        String taiCode = null;
        BigDecimal taiAmount = BigDecimal.ZERO;
        BigDecimal taiRate = BigDecimal.ZERO;
        String acciseCode = null;
        BigDecimal acciseAmount = BigDecimal.ZERO;
        BigDecimal acciseRate = BigDecimal.ZERO;
        BigDecimal safeguardAmount = BigDecimal.ZERO;
        BigDecimal safeguardRate = BigDecimal.ZERO;
        String regime = "MFN";
        String notes = "";

        customsDutyAmount = cifValue.multiply(customsDutyRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if (TAI_RATES.containsKey(sixDigitHs)) {
            taiCode = "TAI-" + sixDigitHs;
            taiRate = TAI_RATES.get(sixDigitHs);
            taiAmount = cifValue.multiply(taiRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            notes += "TAI applicable. ";
        }

        if (ACCISES.containsKey(sixDigitHs)) {
            acciseCode = "ACC-" + sixDigitHs;
            acciseRate = ACCISES.get(sixDigitHs);
            if (netWeightKg != null && netWeightKg.compareTo(BigDecimal.ZERO) > 0) {
                acciseAmount = acciseRate.multiply(netWeightKg)
                        .setScale(2, RoundingMode.HALF_UP);
            }
            notes += "Accises applicables. ";
        }

        if (SAFEGUARD_DUTIES.containsKey(fourDigitHs)) {
            safeguardRate = SAFEGUARD_DUTIES.get(fourDigitHs);
            safeguardAmount = cifValue.multiply(safeguardRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            notes += "Droits de sauvegarde applicables (acier). ";
        }

        BigDecimal totalDuties = customsDutyAmount.add(taiAmount)
                .add(acciseAmount).add(safeguardAmount);

        return new FrenchDutyBreakdown(
                "MC", customsDutyAmount, customsDutyRate,
                taiCode, taiAmount, taiRate,
                acciseCode, acciseAmount, acciseRate,
                safeguardAmount, safeguardRate,
                totalDuties, regime, notes.trim()
        );
    }

    public BigDecimal calculateTAI(String hsCode, BigDecimal cifValue) {
        String sixDigit = hsCode.length() >= 6 ? hsCode.substring(0, 6) : hsCode;
        BigDecimal rate = TAI_RATES.getOrDefault(sixDigit, BigDecimal.ZERO);
        return cifValue.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAccises(String hsCode, BigDecimal netWeightKg) {
        String sixDigit = hsCode.length() >= 6 ? hsCode.substring(0, 6) : hsCode;
        BigDecimal rate = ACCISES.getOrDefault(sixDigit, BigDecimal.ZERO);
        return rate.multiply(netWeightKg)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSafeguardDuty(String hsCode, BigDecimal cifValue) {
        String fourDigit = hsCode.length() >= 4 ? hsCode.substring(0, 4) : hsCode;
        BigDecimal rate = SAFEGUARD_DUTIES.getOrDefault(fourDigit, BigDecimal.ZERO);
        return cifValue.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public boolean hasTAI(String hsCode) {
        String sixDigit = hsCode.length() >= 6 ? hsCode.substring(0, 6) : hsCode;
        return TAI_RATES.containsKey(sixDigit) && TAI_RATES.get(sixDigit).compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasAccises(String hsCode) {
        String sixDigit = hsCode.length() >= 6 ? hsCode.substring(0, 6) : hsCode;
        return ACCISES.containsKey(sixDigit);
    }

    public boolean hasSafeguardDuty(String hsCode) {
        String fourDigit = hsCode.length() >= 4 ? hsCode.substring(0, 4) : hsCode;
        return SAFEGUARD_DUTIES.containsKey(fourDigit);
    }

    public List<String> getActiveRegimes() {
        return List.of("1000 - Mise à la consommation",
                "1100 - Importation définitive",
                "2100 - Admission temporaire avec restitution",
                "3000 - Transit externe",
                "4000 - Transfert",
                "5100 - Réimportation",
                "6100 - Régime de perfectionnement actif",
                "7100 - Exportation définitive",
                "7150 - Exportation temporaire",
                "8000 - Perfectionnement passif",
                "9000 - Entrepôt douanier");
    }

    public boolean isRegimePerfectionnementActif(String regime) {
        return regime != null && regime.startsWith("6100");
    }
}
