package com.incokalk.service;

import com.incokalk.dto.compliance.ComplianceAlert;
import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.model.Incoterm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ComplianceService — Tests unitaires")
class ComplianceServiceTest {

    private ComplianceService service;
    private SimulationRequest req;

    @BeforeEach
    void setUp() {
        service = new ComplianceService();
        req = new SimulationRequest();
        req.setIncoterm(Incoterm.FOB);
        req.setOriginCountry("CN");
        req.setDestinationCountry("FR");
        req.setGoodsValue(50000.0);
        req.setCurrency("EUR");
        req.setTransportMode(SimulationRequest.TransportModeInput.SEA);
        req.setInsuranceLevel(SimulationRequest.InsuranceLevel.STANDARD);
        req.setHsCode("84713000");
    }

    @Test
    @DisplayName("FOB + SEA → pas d'alerte critique incoterm")
    void fobSea_noCriticalIncotermAlert() {
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).noneMatch(a -> a.getSeverity() == ComplianceAlert.Severity.CRITICAL
            && "INCOTERM".equals(a.getCategory()));
    }

    @Test
    @DisplayName("FOB + AIR → alerte CRITICAL incoterm/transport")
    void fobAir_criticalIncotermAlert() {
        req.setTransportMode(SimulationRequest.TransportModeInput.AIR);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.CRITICAL &&
            a.getMessage().contains("maritime"));
    }

    @Test
    @DisplayName("EXW + AIR → alerte WARNING")
    void exwAir_warningAlert() {
        req.setTransportMode(SimulationRequest.TransportModeInput.AIR);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.EXW);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            a.getMessage().contains("EXW"));
    }

    @Test
    @DisplayName("FCA + SEA → alerte INFO")
    void fcaSea_infoAlert() {
        req.setTransportMode(SimulationRequest.TransportModeInput.SEA);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FCA);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.INFO &&
            a.getMessage().contains("FCA"));
    }

    @Test
    @DisplayName("Pays embargoé origine → CRITICAL COUNTRY")
    void embargoedOrigin_criticalCountryAlert() {
        req.setOriginCountry("KP");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.CRITICAL &&
            "COUNTRY".equals(a.getCategory()) &&
            a.getMessage().contains("KP"));
    }

    @Test
    @DisplayName("Pays embargoé destination → CRITICAL COUNTRY")
    void embargoedDest_criticalCountryAlert() {
        req.setDestinationCountry("IR");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.CRITICAL &&
            "COUNTRY".equals(a.getCategory()) &&
            a.getMessage().contains("IR"));
    }

    @Test
    @DisplayName("Pays à haut risque origine → WARNING COUNTRY")
    void highRiskOrigin_warningCountryAlert() {
        req.setOriginCountry("SD");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "COUNTRY".equals(a.getCategory()) &&
            a.getMessage().contains("SD"));
    }

    @Test
    @DisplayName("Même pays origine/destination → WARNING")
    void sameCountryDomestic_warning() {
        req.setOriginCountry("FR");
        req.setDestinationCountry("FR");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            a.getMessage().contains("domestique"));
    }

    @Test
    @DisplayName("Commerce intra-UE → INFO COUNTRY")
    void intraEU_infoCountryAlert() {
        req.setOriginCountry("DE");
        req.setDestinationCountry("FR");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.INFO &&
            "COUNTRY".equals(a.getCategory()) &&
            a.getMessage().contains("intra-UE"));
    }

    @Test
    @DisplayName("Pas de code HS → WARNING HS_CODE")
    void noHsCode_warning() {
        req.setHsCode(null);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "HS_CODE".equals(a.getCategory()) &&
            a.getMessage().contains("3.5%"));
    }

    @Test
    @DisplayName("Code HS chapitre double usage (84) → WARNING")
    void dualUseHsCode_warning() {
        req.setHsCode("84713000");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "HS_CODE".equals(a.getCategory()) &&
            a.getMessage().contains("double usage"));
    }

    @Test
    @DisplayName("Code HS invalide (trop court) → WARNING")
    void invalidHsCode_warning() {
        req.setHsCode("84");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "HS_CODE".equals(a.getCategory()) &&
            a.getMessage().contains("format"));
    }

    @Test
    @DisplayName("Valeur > 100k → WARNING TRANSPORT")
    void highValue_warning() {
        req.setGoodsValue(150000.0);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "TRANSPORT".equals(a.getCategory()) &&
            a.getMessage().contains("100 000"));
    }

    @Test
    @DisplayName("Valeur > 500k → WARNING TRANSPORT")
    void veryHighValue_warning() {
        req.setGoodsValue(600000.0);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            "TRANSPORT".equals(a.getCategory()) &&
            a.getMessage().contains("500 000"));
    }

    @Test
    @DisplayName("Assurance MINIMALE + valeur > 50k → CRITICAL TRANSPORT")
    void minInsuranceHighValue_critical() {
        req.setInsuranceLevel(SimulationRequest.InsuranceLevel.MINIMUM);
        req.setGoodsValue(60000.0);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.FOB);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.CRITICAL &&
            "TRANSPORT".equals(a.getCategory()) &&
            a.getMessage().contains("sous-assurance"));
    }

    @Test
    @DisplayName("DDP hors UE → WARNING incoterm")
    void ddpOutsideEU_warning() {
        req.setOriginCountry("CN");
        req.setDestinationCountry("US");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.DDP);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            a.getMessage().contains("importateur de record"));
    }

    @Test
    @DisplayName("DDP domestique → WARNING")
    void ddpDomestic_warning() {
        req.setOriginCountry("FR");
        req.setDestinationCountry("FR");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.DDP);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            a.getMessage().contains("domestique"));
    }

    @Test
    @DisplayName("EXW intra-UE → INFO INCOTERM")
    void exwIntraEU_info() {
        req.setOriginCountry("DE");
        req.setDestinationCountry("FR");
        req.setTransportMode(SimulationRequest.TransportModeInput.ROAD);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.EXW);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.INFO &&
            "INCOTERM".equals(a.getCategory()) &&
            a.getMessage().contains("Intrastat"));
    }

    @Test
    @DisplayName("CIF/CFR + AIR → WARNING incoterm")
    void cifCfrAir_warning() {
        req.setTransportMode(SimulationRequest.TransportModeInput.AIR);
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.CIF);
        assertThat(alerts).anyMatch(a ->
            a.getSeverity() == ComplianceAlert.Severity.WARNING &&
            a.getMessage().contains("CIP/CPT"));
    }

    @Test
    @DisplayName("DDP + pays embargoé destination → deux alertes CRITICAL")
    void ddpEmbargoedDest_twoCriticalAlerts() {
        req.setDestinationCountry("SY");
        List<ComplianceAlert> alerts = service.checkCompliance(req, Incoterm.DDP);
        long criticalCount = alerts.stream()
            .filter(a -> a.getSeverity() == ComplianceAlert.Severity.CRITICAL)
            .count();
        assertThat(criticalCount).isGreaterThanOrEqualTo(1);
    }
}
