package com.incokalk.service;

import com.incokalk.dto.compliance.ComplianceAlert;
import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.model.Incoterm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ComplianceService {

    private static final Set<String> EU_COUNTRIES = Set.of(
        "AT","BE","BG","HR","CY","CZ","DK","EE","FI","FR",
        "DE","GR","HU","IE","IT","LV","LT","LU","MT","NL",
        "PL","PT","RO","SK","SI","ES","SE"
    );

    private static final Set<String> EMBARGOED_COUNTRIES = Set.of(
        "KP","IR","SY","CU","VE","BY","MM","RU"
    );

    private static final Set<String> HIGH_RISK_ORIGINS = Set.of(
        "KP","IR","SY","CU","VE","BY","MM","SD","SO","YE","LY","AF","IQ"
    );

    private static final Set<String> DUAL_USE_CHAPTERS = Set.of(
        "84","85","87","88","89","93"
    );

    private static final Set<String> RESTRICTED_CHAPTERS = Set.of(
        "22","24","30","33","71","93"
    );

    public List<ComplianceAlert> checkCompliance(SimulationRequest request, Incoterm incoterm) {
        List<ComplianceAlert> alerts = new ArrayList<>();

        checkIncotermTransportCompatibility(request, incoterm, alerts);
        checkCountryRisks(request, alerts);
        checkHsCode(request, alerts);
        checkValueThreshold(request, alerts);
        checkInsuranceAdequacy(request, alerts);
        checkDDPImporterOfRecord(request, incoterm, alerts);
        checkIntraEUTrade(request, incoterm, alerts);

        return alerts;
    }

    private void checkIncotermTransportCompatibility(SimulationRequest request, Incoterm incoterm, List<ComplianceAlert> alerts) {
        if (incoterm.mode == Incoterm.TransportMode.SEA_ONLY &&
            request.getTransportMode() != SimulationRequest.TransportModeInput.SEA) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.CRITICAL,
                "Cet Incoterm est exclusivement réservé au transport maritime ou fluvial.",
                "INCOTERM"
            ));
        }

        if (incoterm == Incoterm.EXW && request.getTransportMode() == SimulationRequest.TransportModeInput.AIR) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "L'Incoterm EXW est déconseillé pour le transport aérien international en raison de la complexité du ramassage par l'acheteur.",
                "INCOTERM"
            ));
        }

        if (incoterm == Incoterm.FCA && request.getTransportMode() == SimulationRequest.TransportModeInput.SEA) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.INFO,
                "FCA est souvent préféré à FOB pour les conteneurs — le point de livraison est le terminal, pas le bord du navire.",
                "INCOTERM"
            ));
        }

        if ((incoterm == Incoterm.CIF || incoterm == Incoterm.CFR) &&
            request.getTransportMode() == SimulationRequest.TransportModeInput.AIR) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "CIF/CFR sont réservés au maritime. Utilisez CIP/CPT pour l'aérien avec une couverture d'assurance équivalente.",
                "INCOTERM"
            ));
        }
    }

    private void checkCountryRisks(SimulationRequest request, List<ComplianceAlert> alerts) {
        String origin = request.getOriginCountry() != null ? request.getOriginCountry().toUpperCase() : "";
        String dest = request.getDestinationCountry() != null ? request.getDestinationCountry().toUpperCase() : "";

        if (EMBARGOED_COUNTRIES.contains(origin)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.CRITICAL,
                "Le pays d'origine (" + origin + ") fait l'objet de sanctions ou d'embargo de l'UE. Vérifiez la légalité de l'opération.",
                "COUNTRY"
            ));
        }

        if (EMBARGOED_COUNTRIES.contains(dest)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.CRITICAL,
                "Le pays de destination (" + dest + ") fait l'objet de sanctions ou d'embargo de l'UE. L'exportation peut être interdite.",
                "COUNTRY"
            ));
        }

        if (HIGH_RISK_ORIGINS.contains(origin) && !EMBARGOED_COUNTRIES.contains(origin)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Le pays d'origine (" + origin + ") est classé à haut risque. Des vérifications supplémentaires sont recommandées.",
                "COUNTRY"
            ));
        }

        if (!origin.isEmpty() && !dest.isEmpty() && origin.equals(dest)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Expédition domestique : les Incoterms internationaux ne s'appliquent pas pleinement. Considérez une livraison locale.",
                "COUNTRY"
            ));
        }

        if (EU_COUNTRIES.contains(origin) && EU_COUNTRIES.contains(dest) && !origin.equals(dest)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.INFO,
                "Commerce intra-UE : pas de droits de douane. La TVA est soumise au mécanisme de reverse charge.",
                "COUNTRY"
            ));
        }
    }

    private void checkHsCode(SimulationRequest request, List<ComplianceAlert> alerts) {
        String hsCode = request.getHsCode();

        if (hsCode == null || hsCode.isBlank()) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Aucun code HS fourni. Les droits de douane seront estimés à un taux moyen de 3.5%. Fournissez le code HS pour un calcul précis.",
                "HS_CODE"
            ));
            return;
        }

        String cleaned = hsCode.replaceAll("[^0-9]", "");
        if (cleaned.length() < 4 || cleaned.length() > 10) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Le code HS fourni (" + hsCode + ") n'a pas un format standard (4 à 10 chiffres). Vérifiez sa validité.",
                "HS_CODE"
            ));
        }

        if (cleaned.length() >= 2) {
            String chapter = cleaned.substring(0, 2);
            if (DUAL_USE_CHAPTERS.contains(chapter)) {
                alerts.add(new ComplianceAlert(
                    ComplianceAlert.Severity.WARNING,
                    "Le chapitre SH (" + chapter + ") inclut des biens à double usage. Vérifiez les licences d'exportation requises.",
                    "HS_CODE"
                ));
            }
            if (RESTRICTED_CHAPTERS.contains(chapter)) {
                alerts.add(new ComplianceAlert(
                    ComplianceAlert.Severity.WARNING,
                    "Le chapitre SH (" + chapter + ") concerne des biens soumis à restrictions (alcool, tabac, armes, etc.).",
                    "HS_CODE"
                ));
            }
        }
    }

    private void checkValueThreshold(SimulationRequest request, List<ComplianceAlert> alerts) {
        Double value = request.getGoodsValue();
        if (value == null) return;

        if (value > 15000) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.INFO,
                "Valeur élevée (" + String.format("%,.0f", value) + " €). Une facture douanière détaillée est obligatoire.",
                "TRANSPORT"
            ));
        }

        if (value > 100000) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Marchandise de grande valeur (>100 000 €). Vérifiez l'adéquation de la couverture d'assurance et les exigences documentaires.",
                "TRANSPORT"
            ));
        }

        if (value > 500000) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Valeur exceptionnelle (>500 000 €). Un examen douanier renforcé est probable. Préparez les documents justificatifs.",
                "TRANSPORT"
            ));
        }
    }

    private void checkInsuranceAdequacy(SimulationRequest request, List<ComplianceAlert> alerts) {
        if (request.getInsuranceLevel() == null) return;

        Double value = request.getGoodsValue();
        if (value == null || value < 10000) return;

        if (request.getInsuranceLevel() == SimulationRequest.InsuranceLevel.MINIMUM && value > 10000) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "Assurance MINIMALE pour une marchandise de valeur (>10 000 €). Envisagez une couverture STANDARD ou ALL_RISKS.",
                "TRANSPORT"
            ));
        }

        if (request.getInsuranceLevel() == SimulationRequest.InsuranceLevel.MINIMUM && value > 50000) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.CRITICAL,
                "Couverture MINIMALE inadéquate pour une marchandise de grande valeur (>50 000 €). Risque de sous-assurance.",
                "TRANSPORT"
            ));
        }
    }

    private void checkDDPImporterOfRecord(SimulationRequest request, Incoterm incoterm, List<ComplianceAlert> alerts) {
        if (incoterm != Incoterm.DDP) return;

        String dest = request.getDestinationCountry() != null ? request.getDestinationCountry().toUpperCase() : "";
        String origin = request.getOriginCountry() != null ? request.getOriginCountry().toUpperCase() : "";

        if (!dest.isEmpty() && !EU_COUNTRIES.contains(dest)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "DDP hors UE : le vendeur doit s'identifier comme importateur de record dans le pays de destination. Complexité administrative élevée.",
                "INCOTERM"
            ));
        }

        if (origin.equals(dest)) {
            alerts.add(new ComplianceAlert(
                ComplianceAlert.Severity.WARNING,
                "DDP domestique : cet Incoterm est rarement utilisé pour des expéditions nationales.",
                "INCOTERM"
            ));
        }
    }

    private void checkIntraEUTrade(SimulationRequest request, Incoterm incoterm, List<ComplianceAlert> alerts) {
        String origin = request.getOriginCountry() != null ? request.getOriginCountry().toUpperCase() : "";
        String dest = request.getDestinationCountry() != null ? request.getDestinationCountry().toUpperCase() : "";

        if (EU_COUNTRIES.contains(origin) && EU_COUNTRIES.contains(dest)) {
            if (incoterm == Incoterm.EXW) {
                alerts.add(new ComplianceAlert(
                    ComplianceAlert.Severity.INFO,
                    "EXW intra-UE : l'acheteur gère l'enlèvement et le transport. Vérifiez les obligations de déclaration Intrastat.",
                    "INCOTERM"
                ));
            }

            if (request.getTransportMode() == SimulationRequest.TransportModeInput.SEA &&
                !(origin.equals("GR") || origin.equals("IT") || origin.equals("ES") || origin.equals("PT") ||
                  origin.equals("FR") || origin.equals("NL") || origin.equals("BE") || origin.equals("DE") ||
                  dest.equals("GR") || dest.equals("IT") || dest.equals("ES") || dest.equals("PT") ||
                  dest.equals("FR") || dest.equals("NL") || dest.equals("BE") || dest.equals("DE"))) {
                alerts.add(new ComplianceAlert(
                    ComplianceAlert.Severity.WARNING,
                    "Transport maritime intra-UE pour des pays sans façade maritime significative. Vérifiez la pertinence du mode.",
                    "TRANSPORT"
                ));
            }
        }
    }
}
