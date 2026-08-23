package com.incokalk.service.compliance;

import com.incokalk.model.CumulationGroup;
import com.incokalk.model.CumulationType;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.CumulationGroupRepository;
import com.incokalk.repository.TradeAgreementRepository;
import com.incokalk.service.compliance.CumulationService.CumulationResult;
import com.incokalk.service.compliance.CumulationService.Material;
import com.incokalk.service.compliance.CumulationService.MaterialStatus;
import com.incokalk.service.compliance.RulesOfOriginService.OriginVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CumulationService — Cumul bilatéral & régional")
class CumulationServiceTest {

    @Mock TradeAgreementRepository agreementRepo;
    @Mock CumulationGroupRepository groupRepo;
    @Mock RulesOfOriginService rulesOfOriginService;

    @InjectMocks CumulationService service;

    private static final String HS = "6101";
    private static final String DEST = "FR";

    private CumulationGroup aseanGroup;
    private TradeAgreement bilateral;
    private TradeAgreement regional;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        aseanGroup = CumulationGroup.builder()
                .code("ASEAN")
                .name("ASEAN")
                .memberCountries(List.of("VN", "SG", "TH", "MY", "ID"))
                .build();

        bilateral = TradeAgreement.builder()
                .code("EVFTA")
                .name("Accord UE-Vietnam")
                .partnerCountry("VN")
                .cumulationType(CumulationType.BILATERAL)
                .vaThresholdPct(35.0)
                .build();

        regional = TradeAgreement.builder()
                .code("EVFTA")
                .name("Accord UE-Vietnam (cumul ASEAN)")
                .partnerCountry("VN")
                .cumulationType(CumulationType.DIAGONAL)
                .cumulationGroupCode("ASEAN")
                .vaThresholdPct(35.0)
                .build();
    }

    private void stubAgreement(TradeAgreement agreement) {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue(anyString()))
                .thenReturn(List.of(agreement));
    }

    @Test
    @DisplayName("Cumul bilatéral : matières UE cumulées → origine confirmée")
    void bilateral_euMaterialsCount() {
        stubAgreement(bilateral);
        when(groupRepo.findByCode(anyString())).thenReturn(Optional.empty());

        CumulationResult result = service.assess(HS, "VN", DEST, 30, 100, List.of(
                new Material(HS, "DE", 30, false, "tissu UE"),
                new Material(HS, "CN", 40, false, "boutons")));

        assertThat(result.qualifies()).isTrue();
        assertThat(result.cumulationType()).isEqualTo(CumulationType.BILATERAL);
        assertThat(result.originatingContentPct()).isEqualTo(60.0);
        assertThat(result.materials()).extracting(m -> m.status())
                .contains(MaterialStatus.CUMULATED_BILATERAL)
                .contains(MaterialStatus.NON_ORIGINATING);
    }

    @Test
    @DisplayName("Cumul bilatéral : contenu originaire sous le seuil → refus")
    void bilateral_belowThreshold() {
        stubAgreement(bilateral);

        CumulationResult result = service.assess(HS, "VN", DEST, 10, 100, List.of(
                new Material(HS, "DE", 20, false, "tissu UE"),
                new Material(HS, "CN", 70, false, "matière chinoise")));

        assertThat(result.qualifies()).isFalse();
        assertThat(result.originatingContentPct()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Cumul régional : matière originaire d'un membre ASEAN cumulée")
    void regional_memberMaterialWithProof() {
        stubAgreement(regional);
        when(groupRepo.findByCode("ASEAN")).thenReturn(Optional.of(aseanGroup));

        CumulationResult result = service.assess(HS, "VN", DEST, 20, 100, List.of(
                new Material(HS, "SG", 50, true, "tissu singapourien (EUR.1)"),
                new Material(HS, "CN", 30, false, "matière chinoise")));

        assertThat(result.qualifies()).isTrue();
        assertThat(result.originatingContentPct()).isEqualTo(70.0);
        assertThat(result.materials()).extracting(m -> m.status())
                .contains(MaterialStatus.CUMULATED_REGIONAL);
    }

    @Test
    @DisplayName("Cumul régional : membre sans preuve d'origine → non cumulé + avertissement")
    void regional_memberWithoutProof() {
        stubAgreement(regional);
        when(groupRepo.findByCode("ASEAN")).thenReturn(Optional.of(aseanGroup));

        CumulationResult result = service.assess(HS, "VN", DEST, 20, 100, List.of(
                new Material(HS, "SG", 50, false, "tissu sans preuve"),
                new Material(HS, "CN", 30, false, "matière chinoise")));

        assertThat(result.qualifies()).isFalse();
        assertThat(result.materials()).extracting(m -> m.status())
                .doesNotContain(MaterialStatus.CUMULATED_REGIONAL);
        assertThat(result.warnings()).anyMatch(w -> w.contains("preuve d'origine requise"));
    }

    @Test
    @DisplayName("Pays sans accord actif → pas de cumul possible")
    void noAgreement() {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue(anyString()))
                .thenReturn(List.of());

        CumulationResult result = service.assess(HS, "XX", DEST, 50, 100, List.of(
                new Material(HS, "DE", 30, false, "tissu UE")));

        assertThat(result.qualifies()).isFalse();
        assertThat(result.explanation()).contains("Aucun accord commercial actif");
    }

    @Test
    @DisplayName("Accord sans cumul (NONE) : matières UE non cumulables")
    void noCumulationType() {
        stubAgreement(TradeAgreement.builder()
                .code("EUIDN")
                .name("Accord UE-Indonesia")
                .partnerCountry("ID")
                .cumulationType(CumulationType.NONE)
                .build());

        CumulationResult result = service.assess(HS, "ID", DEST, 50, 100, List.of(
                new Material(HS, "DE", 30, false, "tissu UE")));

        assertThat(result.qualifies()).isFalse();
        assertThat(result.cumulationType()).isEqualTo(CumulationType.NONE);
    }

    @Test
    @DisplayName("Matière du pays exportateur = contenu originaire (LOCAL)")
    void localMaterialsCount() {
        stubAgreement(bilateral);

        CumulationResult result = service.assess(HS, "VN", DEST, 10, 100, List.of(
                new Material(HS, "VN", 60, false, "fabrication locale"),
                new Material(HS, "CN", 30, false, "matière chinoise")));

        assertThat(result.qualifies()).isTrue();
        assertThat(result.originatingContentPct()).isEqualTo(70.0);
        assertThat(result.materials()).extracting(m -> m.status())
                .contains(MaterialStatus.LOCAL);
    }

    @Test
    @DisplayName("verifyWithCumulation : règles classiques KO puis cumul OK → CUM")
    void verifyWithCumulation_fallsBackToCumulation() {
        stubAgreement(bilateral);

        OriginVerificationResult base = new OriginVerificationResult(
                false, null, "EVFTA", "Accord UE-Vietnam",
                "Aucun critère d'origine satisfait", 0.0, List.of());
        when(rulesOfOriginService.verifyOrigin(anyString(), anyString(), anyString(),
                anyDouble(), anyDouble(), anyList())).thenReturn(base);

        OriginVerificationResult result = service.verifyWithCumulation(
                HS, "VN", DEST, 30, 100, List.of(), List.of(
                        new Material(HS, "DE", 30, false, "tissu UE"),
                        new Material(HS, "CN", 40, false, "boutons")));

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(RulesOfOriginService.OriginCriterion.CUM);
    }

    @Test
    @DisplayName("verifyWithCumulation : règles classiques OK → résultat direct")
    void verifyWithCumulation_classicRulesWin() {
        OriginVerificationResult base = new OriginVerificationResult(
                true, RulesOfOriginService.OriginCriterion.WO, "EVFTA", "Accord UE-Vietnam",
                "Produit entièrement obtenu", 100.0, List.of());
        when(rulesOfOriginService.verifyOrigin(anyString(), anyString(), anyString(),
                anyDouble(), anyDouble(), anyList())).thenReturn(base);

        OriginVerificationResult result = service.verifyWithCumulation(
                HS, "VN", DEST, 100, 100, List.of("local"), List.of());

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(RulesOfOriginService.OriginCriterion.WO);
        verify(groupRepo, never()).findByCode(anyString());
    }
}
