package com.incokalk.service.compliance;

import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.TradeAgreementRepository;
import com.incokalk.service.compliance.RulesOfOriginService.OriginCriterion;
import com.incokalk.service.compliance.RulesOfOriginService.OriginVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RulesOfOriginService — Règles d'origine préférentielle")
class RulesOfOriginServiceTest {

    @Mock
    TradeAgreementRepository agreementRepo;

    RulesOfOriginService service;

    private static final String DEST = "FR";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RulesOfOriginService(agreementRepo);
    }

    private void stubAgreement(TradeAgreement agreement) {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue(anyString()))
                .thenReturn(List.of(agreement));
    }

    private void stubNoAgreement() {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue(anyString()))
                .thenReturn(List.of());
    }

    private TradeAgreement evfta() {
        return TradeAgreement.builder()
                .code("EVFTA")
                .name("Accord UE-Vietnam")
                .partnerCountry("VN")
                .build();
    }

    private TradeAgreement eujepa() {
        return TradeAgreement.builder()
                .code("EUJEPA")
                .name("Accord UE-Japon")
                .partnerCountry("JP")
                .build();
    }

    private TradeAgreement unknownCodeAgreement() {
        return TradeAgreement.builder()
                .code("ZZZZ")
                .name("Accord inconnu")
                .partnerCountry("ZZ")
                .build();
    }

    // ---------------------------------------------------------------
    // verifyOrigin
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Aucun accord actif pour le pays d'origine -> refus")
    void verifyOrigin_noActiveAgreement() {
        stubNoAgreement();

        OriginVerificationResult result = service.verifyOrigin(
                "0101", "XX", DEST, 10, 100, List.of());

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.criterionUsed()).isNull();
        assertThat(result.agreementCode()).isNull();
        assertThat(result.agreementName()).isNull();
        assertThat(result.explanation()).contains("Aucun accord commercial actif");
        assertThat(result.valueAddedPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Chapitre HS éligible WO -> origine confirmée (wholly obtained)")
    void verifyOrigin_whollyObtainedChapterEligible() {
        stubAgreement(evfta());

        OriginVerificationResult result = service.verifyOrigin(
                "0101", "VN", DEST, 0, 0, null);

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(OriginCriterion.WO);
        assertThat(result.agreementCode()).isEqualTo("EVFTA");
        assertThat(result.agreementName()).isEqualTo("Accord UE-Vietnam");
        assertThat(result.valueAddedPercentage()).isEqualTo(100.0);
        assertThat(result.explanation()).contains("chapitre HS 01");
    }

    @Test
    @DisplayName("hsCode trop court (<2 caractères) -> chapitre vide, aucun critère satisfait")
    void verifyOrigin_hsCodeTooShort_fallsThroughToNoMatch() {
        stubAgreement(evfta());

        OriginVerificationResult result = service.verifyOrigin(
                "5", "VN", DEST, 0, 0, null);

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.criterionUsed()).isNull();
        assertThat(result.agreementCode()).isEqualTo("EVFTA");
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Critère PE : toutes les étapes de fabrication locales -> origine confirmée")
    void verifyOrigin_producedExclusively_allLocal() {
        stubAgreement(eujepa());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "JP", DEST, 0, 0, List.of("local", "FR"));

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(OriginCriterion.PE);
        assertThat(result.valueAddedPercentage()).isEqualTo(100.0);
        assertThat(result.explanation()).contains("PE");
    }

    @Test
    @DisplayName("Critère PE : étapes non locales -> avertissement puis aucun critère satisfait")
    void verifyOrigin_producedExclusively_notAllLocal() {
        stubAgreement(eujepa());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "JP", DEST, 0, 0, List.of("local", "foreign-step"));

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.warnings()).anyMatch(w -> w.contains("PE non applicable"));
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
    }

    @Test
    @DisplayName("Critère CTH : changement de classification tarifaire avec coût total positif")
    void verifyOrigin_cth_classificationChange_positiveCost() {
        stubAgreement(eujepa());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "JP", DEST, 40, 100, List.of("transformation du matériau"));

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(OriginCriterion.CTH);
        assertThat(result.valueAddedPercentage()).isEqualTo(40.0);
        assertThat(result.explanation()).contains("VA:");
    }

    @Test
    @DisplayName("Critère CTH : changement de classification avec coût total nul -> VA = 0")
    void verifyOrigin_cth_classificationChange_zeroCost() {
        stubAgreement(eujepa());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "JP", DEST, 40, 0, List.of("manufacture locale"));

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(OriginCriterion.CTH);
        assertThat(result.valueAddedPercentage()).isEqualTo(0.0);
        assertThat(result.explanation()).contains("VA:");
    }

    @Test
    @DisplayName("Critère CTH : aucune étape ne change la classification -> aucun critère satisfait")
    void verifyOrigin_cth_noClassificationChange() {
        stubAgreement(eujepa());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "JP", DEST, 40, 100, List.of("découpe simple"));

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
    }

    @Test
    @DisplayName("Critère CTSH : VA >= 60% même sans étapes de fabrication -> origine confirmée")
    void verifyOrigin_ctsh_highValueAdded_noSteps() {
        stubAgreement(evfta());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "VN", DEST, 70, 100, null);

        assertThat(result.isOriginating()).isTrue();
        assertThat(result.criterionUsed()).isEqualTo(OriginCriterion.CTSH);
        assertThat(result.valueAddedPercentage()).isEqualTo(70.0);
        assertThat(result.explanation()).contains("seuil élevé");
    }

    @Test
    @DisplayName("Critère CTSH : VA < 60% et pas d'étapes -> aucun critère satisfait")
    void verifyOrigin_ctsh_lowValueAdded_noSteps() {
        stubAgreement(evfta());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "VN", DEST, 20, 100, null);

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.criterionUsed()).isNull();
        assertThat(result.valueAddedPercentage()).isEqualTo(0.0);
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
    }

    @Test
    @DisplayName("Critère CTSH : étapes présentes sans changement de classification, VA < 60% -> aucun critère satisfait")
    void verifyOrigin_ctsh_stepsPresentNoClassificationChange_belowThreshold() {
        stubAgreement(evfta());

        OriginVerificationResult result = service.verifyOrigin(
                "9901", "VN", DEST, 20, 100, List.of("découpe", "assemblage simple"));

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.criterionUsed()).isNull();
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
    }

    @Test
    @DisplayName("Agrément avec code inconnu -> critères par défaut (WH, WO, PE, CTH), CTSH indisponible")
    void verifyOrigin_unknownAgreementCode_usesDefaultCriteria() {
        stubAgreement(unknownCodeAgreement());

        // VA élevée mais CTSH non applicable pour ce code -> pas de critère satisfait
        OriginVerificationResult result = service.verifyOrigin(
                "9901", "ZZ", DEST, 70, 100, null);

        assertThat(result.isOriginating()).isFalse();
        assertThat(result.agreementCode()).isEqualTo("ZZZZ");
        assertThat(result.explanation()).contains("Aucun critère d'origine satisfait");
    }

    // ---------------------------------------------------------------
    // getApplicableAgreement
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getApplicableAgreement : destination ni FR ni EU -> Optional vide sans appel repo")
    void getApplicableAgreement_invalidDestination_returnsEmpty() {
        Optional<TradeAgreement> result = service.getApplicableAgreement("VN", "US");

        assertThat(result).isEmpty();
        verifyNoInteractions(agreementRepo);
    }

    @Test
    @DisplayName("getApplicableAgreement : destination FR (insensible à la casse) avec accord trouvé")
    void getApplicableAgreement_validDestination_agreementFound() {
        stubAgreement(evfta());

        Optional<TradeAgreement> result = service.getApplicableAgreement("VN", "fr");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("EVFTA");
    }

    @Test
    @DisplayName("getApplicableAgreement : destination EU sans accord trouvé -> Optional vide")
    void getApplicableAgreement_euDestination_noAgreement() {
        stubNoAgreement();

        Optional<TradeAgreement> result = service.getApplicableAgreement("XX", "EU");

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------
    // getApplicableCriteria
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getApplicableCriteria : accord connu -> critères spécifiques")
    void getApplicableCriteria_knownAgreement() {
        stubAgreement(evfta());

        Set<String> criteria = service.getApplicableCriteria("VN");

        assertThat(criteria).containsExactlyInAnyOrder("WH", "WO", "PE", "CTH", "CTSH");
    }

    @Test
    @DisplayName("getApplicableCriteria : accord avec code inconnu -> critères par défaut")
    void getApplicableCriteria_unknownAgreementCode() {
        stubAgreement(unknownCodeAgreement());

        Set<String> criteria = service.getApplicableCriteria("ZZ");

        assertThat(criteria).containsExactlyInAnyOrder("WH", "WO", "PE", "CTH");
    }

    @Test
    @DisplayName("getApplicableCriteria : aucun accord -> ensemble vide")
    void getApplicableCriteria_noAgreement() {
        stubNoAgreement();

        Set<String> criteria = service.getApplicableCriteria("XX");

        assertThat(criteria).isEmpty();
    }

    // ---------------------------------------------------------------
    // calculateValueAdded
    // ---------------------------------------------------------------

    @Test
    @DisplayName("calculateValueAdded : coût total positif -> calcul du pourcentage")
    void calculateValueAdded_positiveTotalCost() {
        double result = service.calculateValueAdded(100, 40);

        assertThat(result).isEqualTo(60.0);
    }

    @Test
    @DisplayName("calculateValueAdded : coût total nul -> retourne 0")
    void calculateValueAdded_zeroTotalCost() {
        double result = service.calculateValueAdded(0, 40);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculateValueAdded : coût total négatif -> retourne 0")
    void calculateValueAdded_negativeTotalCost() {
        double result = service.calculateValueAdded(-10, 40);

        assertThat(result).isEqualTo(0.0);
    }
}
