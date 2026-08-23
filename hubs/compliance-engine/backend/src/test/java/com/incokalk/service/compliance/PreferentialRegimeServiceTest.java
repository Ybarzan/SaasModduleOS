package com.incokalk.service.compliance;

import com.incokalk.model.TaricRate;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import com.incokalk.service.compliance.RulesOfOriginService.OriginCriterion;
import com.incokalk.service.compliance.RulesOfOriginService.OriginVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PreferentialRegimeService — Calcul des droits de douane préférentiels")
class PreferentialRegimeServiceTest {

    @Mock RulesOfOriginService rulesOfOriginService;
    @Mock TaricRateRepository taricRepo;
    @Mock TradeAgreementRepository agreementRepo;

    @InjectMocks PreferentialRegimeService service;

    private static final String HS = "0101";
    private static final String DEST = "FR";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private TaricRate rate(double dutyRate) {
        return TaricRate.builder().dutyRate(dutyRate).build();
    }

    private TradeAgreement agreement(String code, String partnerCountry) {
        return TradeAgreement.builder()
                .code(code)
                .name("Accord " + code)
                .partnerCountry(partnerCountry)
                .build();
    }

    // ---------------------------------------------------------------
    // calculatePreferentialDuty
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Origine non confirmée -> résultat non préférentiel, aucun appel aux dépôts TARIC")
    void calculatePreferentialDuty_notOriginating_returnsNonPreferential() {
        OriginVerificationResult origin = new OriginVerificationResult(
                false, null, null, null,
                "Aucun accord commercial actif trouvé pour le pays d'origine : XX",
                0.0, List.of());
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("XX"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(origin);

        PreferentialRegimeService.PreferentialResult result =
                service.calculatePreferentialDuty(HS, "XX", DEST, 1000, 60, 100);

        assertThat(result.isPreferential()).isFalse();
        assertThat(result.agreementCode()).isNull();
        assertThat(result.agreementName()).isNull();
        assertThat(result.mfnDutyRate()).isEqualTo(0.0);
        assertThat(result.preferentialDutyRate()).isEqualTo(0.0);
        assertThat(result.savings()).isEqualTo(0.0);
        assertThat(result.originCriterion()).isNull();
        assertThat(result.originExplanation()).contains("Aucun accord commercial actif");
        assertThat(result.valueAddedPct()).isEqualTo(0.0);
        assertThat(result.isOriginating()).isFalse();

        verifyNoInteractions(taricRepo);
    }

    @Test
    @DisplayName("Origine confirmée avec critère connu -> taux trouvés en base, économies calculées")
    void calculatePreferentialDuty_originating_ratesFoundInRepo() {
        OriginVerificationResult origin = new OriginVerificationResult(
                true, OriginCriterion.WO, "EVFTA", "Accord UE-Vietnam",
                "Produit entièrement obtenu (WO) - chapitre HS 01 éligible",
                100.0, List.of());
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("VN"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(origin);
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(HS, "VN", DEST))
                .thenReturn(List.of(rate(8.3)));
        when(taricRepo.findPreferentialRates(HS, "VN", DEST, true))
                .thenReturn(List.of(rate(0.0)));

        PreferentialRegimeService.PreferentialResult result =
                service.calculatePreferentialDuty(HS, "VN", DEST, 1000, 60, 100);

        assertThat(result.isPreferential()).isTrue();
        assertThat(result.agreementCode()).isEqualTo("EVFTA");
        assertThat(result.agreementName()).isEqualTo("Accord UE-Vietnam");
        assertThat(result.mfnDutyRate()).isEqualTo(8.3);
        assertThat(result.preferentialDutyRate()).isEqualTo(0.0);
        assertThat(result.savings()).isEqualTo(83.0);
        assertThat(result.originCriterion()).isEqualTo("WO");
        assertThat(result.valueAddedPct()).isEqualTo(100.0);
        assertThat(result.isOriginating()).isTrue();
    }

    @Test
    @DisplayName("Origine confirmée sans critère explicite (CUM) -> originCriterion=null, taux simulés en fallback")
    void calculatePreferentialDuty_originating_nullCriterion_fallbackSimulatedRates() {
        OriginVerificationResult origin = new OriginVerificationResult(
                true, null, "EVFTA", "Accord UE-Vietnam",
                "Origine confirmée par cumul", 45.0, List.of());
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("VN"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(origin);
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(HS, "VN", DEST))
                .thenReturn(List.of());
        when(taricRepo.findPreferentialRates(HS, "VN", DEST, true))
                .thenReturn(List.of());

        PreferentialRegimeService.PreferentialResult result =
                service.calculatePreferentialDuty(HS, "VN", DEST, 1000, 45, 100);

        assertThat(result.isPreferential()).isTrue();
        assertThat(result.originCriterion()).isNull();
        // chapitre "01" -> taux MFN simulé = 8.3, fallback préférentiel = 0.0
        assertThat(result.mfnDutyRate()).isEqualTo(8.3);
        assertThat(result.preferentialDutyRate()).isEqualTo(0.0);
        assertThat(result.savings()).isEqualTo(83.0);
        assertThat(result.valueAddedPct()).isEqualTo(45.0);
    }

    // ---------------------------------------------------------------
    // getMfnRate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMfnRate : taux trouvé en base -> premier taux retourné")
    void getMfnRate_ratesFound_returnsFirstRate() {
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("0202", "XX", DEST))
                .thenReturn(List.of(rate(12.8), rate(99.0)));

        double result = service.getMfnRate("0202", "XX", DEST);

        assertThat(result).isEqualTo(12.8);
    }

    @Test
    @DisplayName("getMfnRate : aucun taux en base, chapitre connu -> taux simulé du chapitre")
    void getMfnRate_noRates_knownChapter_simulatedRate() {
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("0202", "XX", DEST))
                .thenReturn(List.of());

        double result = service.getMfnRate("0202", "XX", DEST);

        assertThat(result).isEqualTo(12.8);
    }

    @Test
    @DisplayName("getMfnRate : hsCode trop court -> chapitre '00', taux par défaut")
    void getMfnRate_hsCodeTooShort_defaultFallback() {
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry("5", "XX", DEST))
                .thenReturn(List.of());

        double result = service.getMfnRate("5", "XX", DEST);

        assertThat(result).isEqualTo(3.5);
    }

    // ---------------------------------------------------------------
    // getPreferentialRate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getPreferentialRate : taux trouvé en base -> premier taux retourné")
    void getPreferentialRate_ratesFound_returnsFirstRate() {
        when(taricRepo.findPreferentialRates(HS, "VN", DEST, true))
                .thenReturn(List.of(rate(2.5)));

        double result = service.getPreferentialRate(HS, "VN", DEST, "EVFTA");

        assertThat(result).isEqualTo(2.5);
    }

    @Test
    @DisplayName("getPreferentialRate : aucun taux en base -> fallback 0.0")
    void getPreferentialRate_noRates_fallbackZero() {
        when(taricRepo.findPreferentialRates(HS, "VN", DEST, true))
                .thenReturn(List.of());

        double result = service.getPreferentialRate(HS, "VN", DEST, "EVFTA");

        assertThat(result).isEqualTo(0.0);
    }

    // ---------------------------------------------------------------
    // getAllPreferentialRates
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getAllPreferentialRates : aucun accord actif -> liste vide")
    void getAllPreferentialRates_noActiveAgreements_returnsEmpty() {
        when(agreementRepo.findByIsActiveTrue()).thenReturn(List.of());

        List<PreferentialRegimeService.PreferentialResult> results =
                service.getAllPreferentialRates(HS, DEST, 1000, 60, 100);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("getAllPreferentialRates : filtre les non-préférentiels, absorbe les exceptions, trie par économies décroissantes")
    void getAllPreferentialRates_filtersSortsAndAbsorbsExceptions() {
        when(agreementRepo.findByIsActiveTrue()).thenReturn(List.of(
                agreement("EVFTA", "VN"),
                agreement("EUJEPA", "JP"),
                agreement("EUIDN", "DE"),
                agreement("EUCHN", "CN")));

        // VN -> origine confirmée, forte économie
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("VN"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(new OriginVerificationResult(
                        true, OriginCriterion.WO, "EVFTA", "Accord UE-Vietnam",
                        "Produit entièrement obtenu", 100.0, List.of()));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(HS, "VN", DEST))
                .thenReturn(List.of(rate(10.0)));
        when(taricRepo.findPreferentialRates(HS, "VN", DEST, true))
                .thenReturn(List.of(rate(0.0)));

        // JP -> origine confirmée, économie plus faible
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("JP"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(new OriginVerificationResult(
                        true, OriginCriterion.CTH, "EUJEPA", "Accord UE-Japon",
                        "Changement de classification tarifaire", 40.0, List.of()));
        when(taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(HS, "JP", DEST))
                .thenReturn(List.of(rate(5.0)));
        when(taricRepo.findPreferentialRates(HS, "JP", DEST, true))
                .thenReturn(List.of(rate(2.0)));

        // DE -> origine non confirmée -> exclu des résultats
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("DE"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenReturn(new OriginVerificationResult(
                        false, null, "EUIDN", "Accord UE-Indonésie",
                        "Aucun critère d'origine satisfait", 0.0, List.of()));

        // CN -> lève une exception, doit être absorbée sans interrompre le traitement
        when(rulesOfOriginService.verifyOrigin(eq(HS), eq("CN"), eq(DEST), anyDouble(), anyDouble(), anyList()))
                .thenThrow(new RuntimeException("boom"));

        List<PreferentialRegimeService.PreferentialResult> results =
                service.getAllPreferentialRates(HS, DEST, 1000, 60, 100);

        assertThat(results).hasSize(2);
        // VN : savings = 1000 * (10-0)/100 = 100 ; JP : savings = 1000 * (5-2)/100 = 30
        assertThat(results.get(0).agreementCode()).isEqualTo("EVFTA");
        assertThat(results.get(0).savings()).isEqualTo(100.0);
        assertThat(results.get(1).agreementCode()).isEqualTo("EUJEPA");
        assertThat(results.get(1).savings()).isEqualTo(30.0);
    }
}
