package com.incokalk.service;

import com.incokalk.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeclarationValidationService {

    private static final Pattern EORI_PATTERN = Pattern.compile("^[A-Z]{2}\\d{8,15}$");
    private static final Pattern HS_CODE_PATTERN = Pattern.compile("^\\d{4,10}$");

    public List<Alert> validateDau(CustomsDeclaration d) {
        List<Alert> alerts = new ArrayList<>();
        if (d.getHsCode() == null || d.getHsCode().isBlank()) {
            alerts.add(new Alert("WARNING", "HS_CODE_MISSING", "Code SH manquant"));
        } else if (!HS_CODE_PATTERN.matcher(d.getHsCode()).matches()) {
            alerts.add(new Alert("ERROR", "HS_CODE_INVALID", "Code SH invalide: " + d.getHsCode()));
        }
        if (d.getDeclaredValue() == null || d.getDeclaredValue().compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add(new Alert("ERROR", "VALUE_MISSING", "Valeur déclarée manquante ou nulle"));
        }
        if (d.getNetWeight() != null && d.getGrossWeight() != null && d.getNetWeight().compareTo(d.getGrossWeight()) > 0) {
            alerts.add(new Alert("WARNING", "WEIGHT_INCOHERENT", "Poids net supérieur au poids brut"));
        }
        if (d.getOriginCountry() == null || d.getOriginCountry().isBlank()) {
            alerts.add(new Alert("WARNING", "ORIGIN_MISSING", "Pays d'origine manquant"));
        }
        if (d.getDestinationCountry() == null || d.getDestinationCountry().isBlank()) {
            alerts.add(new Alert("WARNING", "DESTINATION_MISSING", "Pays de destination manquant"));
        }
        return alerts;
    }

    public List<Alert> validateDeb(DebDeclaration d) {
        List<Alert> alerts = new ArrayList<>();
        if (d.getHsCode8() == null || d.getHsCode8().isBlank()) {
            alerts.add(new Alert("WARNING", "HS_CODE_MISSING", "Code SH 8 chiffres manquant"));
        } else if (d.getHsCode8().length() != 8) {
            alerts.add(new Alert("ERROR", "HS_CODE_LENGTH", "Code SH doit faire 8 chiffres (actuel: " + d.getHsCode8().length() + ")"));
        }
        if (d.getPartnerCountry() == null || d.getPartnerCountry().isBlank()) {
            alerts.add(new Alert("ERROR", "PARTNER_MISSING", "Pays partenaire manquant"));
        } else if (d.getPartnerCountry().length() != 2) {
            alerts.add(new Alert("ERROR", "PARTNER_INVALID", "Code pays partenaire invalide (2 caractères requis)"));
        }
        if (d.getStatisticalValue() == null || d.getStatisticalValue().compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add(new Alert("WARNING", "VALUE_MISSING", "Valeur statistique manquante ou nulle"));
        }
        if (d.getNetMass() != null && d.getNetMass().compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add(new Alert("WARNING", "MASS_ZERO", "Masse nette nulle ou négative"));
        }
        return alerts;
    }

    public List<Alert> validateIcs2(Ics2Declaration d) {
        List<Alert> alerts = new ArrayList<>();
        if (d.getSenderEori() == null || d.getSenderEori().isBlank()) {
            alerts.add(new Alert("ERROR", "SENDER_EORI_MISSING", "EORI expéditeur manquant"));
        } else if (!EORI_PATTERN.matcher(d.getSenderEori()).matches()) {
            alerts.add(new Alert("ERROR", "SENDER_EORI_INVALID", "EORI expéditeur invalide: " + d.getSenderEori()));
        }
        if (d.getReceiverEori() == null || d.getReceiverEori().isBlank()) {
            alerts.add(new Alert("ERROR", "RECEIVER_EORI_MISSING", "EORI destinataire manquant"));
        } else if (!EORI_PATTERN.matcher(d.getReceiverEori()).matches()) {
            alerts.add(new Alert("ERROR", "RECEIVER_EORI_INVALID", "EORI destinataire invalide: " + d.getReceiverEori()));
        }
        if (d.getHsCode6() == null || d.getHsCode6().isBlank()) {
            alerts.add(new Alert("WARNING", "HS_CODE_MISSING", "Code SH manquant"));
        } else if (d.getHsCode6().length() != 6) {
            alerts.add(new Alert("ERROR", "HS_CODE_LENGTH", "Code SH ICS2 doit faire 6 chiffres (actuel: " + d.getHsCode6().length() + ")"));
        }
        if (d.getGrossWeight() == null || d.getGrossWeight().compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add(new Alert("WARNING", "WEIGHT_MISSING", "Poids brut manquant"));
        }
        return alerts;
    }

    public List<Alert> validateExport(ExportDeclaration d) {
        List<Alert> alerts = new ArrayList<>();
        if (d.getExporterEori() == null || d.getExporterEori().isBlank()) {
            alerts.add(new Alert("ERROR", "EXPORTER_EORI_MISSING", "EORI exportateur manquant"));
        } else if (!EORI_PATTERN.matcher(d.getExporterEori()).matches()) {
            alerts.add(new Alert("ERROR", "EXPORTER_EORI_INVALID", "EORI exportateur invalide: " + d.getExporterEori()));
        }
        if (d.getHsCode() == null || d.getHsCode().isBlank()) {
            alerts.add(new Alert("WARNING", "HS_CODE_MISSING", "Code SH manquant"));
        } else if (d.getHsCode().length() < 6) {
            alerts.add(new Alert("ERROR", "HS_CODE_SHORT", "Code SH trop court (minimum 6 chiffres)"));
        }
        if (d.getDestinationCountry() == null || d.getDestinationCountry().isBlank()) {
            alerts.add(new Alert("ERROR", "DESTINATION_MISSING", "Pays de destination manquant"));
        }
        if (d.getDeclaredValue() == null || d.getDeclaredValue().compareTo(BigDecimal.ZERO) <= 0) {
            alerts.add(new Alert("ERROR", "VALUE_MISSING", "Valeur déclarée manquante ou nulle"));
        }
        if (d.getNetWeight() != null && d.getGrossWeight() != null && d.getNetWeight().compareTo(d.getGrossWeight()) > 0) {
            alerts.add(new Alert("WARNING", "WEIGHT_INCOHERENT", "Poids net supérieur au poids brut"));
        }
        return alerts;
    }

    public record Alert(String level, String code, String message) {}
}
