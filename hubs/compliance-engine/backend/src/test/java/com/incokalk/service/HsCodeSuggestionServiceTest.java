package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.HsCodeSuggestion;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.HsCodeSuggestionRepository;
import com.incokalk.service.ml.HsMlService;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("HsCodeSuggestionService — Tests unitaires")
class HsCodeSuggestionServiceTest {

    @Mock HsCodeSuggestionRepository suggestionRepo;
    @Mock CompanyRepository companyRepo;
    @Mock TaricClassificationService taricClassification;
    @Mock HsMlService hsMlService;
    @Mock SemanticClassificationService semanticClassification;
    @InjectMocks HsCodeSuggestionService service;

    UUID companyId;
    Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("ACME").build();
        // Source par defaut : pas de contribution semantique, sauf surcharge explicite
        // par un test qui veut exercer le blending avec cette 4e source.
        when(semanticClassification.classify(anyString(), eq(3))).thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    // suggest()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("suggest → entreprise introuvable")
    void suggest_companyNotFound_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.suggest("une description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Entreprise introuvable");

            verifyNoInteractions(hsMlService);
            verifyNoInteractions(taricClassification);
        }
    }

    @Test
    @DisplayName("suggest → modèle entrainé, ML + TARIC sur le même code se combinent")
    void suggest_modelTrained_mergesMlAndTaricSameCode() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(5); // modelTrained = true
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(
                List.of(new HsMlService.HsPrediction("1234", "D1", 0.9, "ml"))
            );
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(
                List.of(new TaricClassificationService.ClassificationResult("1234", "D2", 0.5))
            );

            HsCodeSuggestion result = service.suggest("un produit");

            assertThat(result.getSuggestedCode1()).isEqualTo("1234");
            assertThat(result.getSuggestedDescription1()).isEqualTo("D1");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(0.55));
            assertThat(result.getSuggestedCode2()).isNull();
            assertThat(result.getSuggestedCode3()).isNull();
            assertThat(result.getCompany()).isEqualTo(company);
            assertThat(result.getProductDescription()).isEqualTo("un produit");

            verify(suggestionRepo).save(result);
        }
    }

    @Test
    @DisplayName("suggest → modèle non entrainé, ML et TARIC distincts, TARIC faible ignoré")
    void suggest_modelNotTrained_distinctCodesAndLowConfidenceSkipped() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0); // modelTrained = false
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(
                List.of(new HsMlService.HsPrediction("A001", "DescA", 0.9, "ml"))
            );
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(
                List.of(
                    new TaricClassificationService.ClassificationResult("B002", "DescB", 0.5),
                    new TaricClassificationService.ClassificationResult("C003", "DescC", 0.1)
                )
            );

            HsCodeSuggestion result = service.suggest("un autre produit");

            // B002 (0.50) ranks above A001 (0.45); C003 dropped (tConfidence 0.1 < 0.15)
            assertThat(result.getSuggestedCode1()).isEqualTo("B002");
            assertThat(result.getSuggestedDescription1()).isEqualTo("DescB");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(0.50));

            assertThat(result.getSuggestedCode2()).isEqualTo("A001");
            assertThat(result.getSuggestedDescription2()).isEqualTo("DescA");
            assertThat(result.getConfidence2()).isEqualByComparingTo(BigDecimal.valueOf(0.45));

            assertThat(result.getSuggestedCode3()).isNull();
        }
    }

    @Test
    @DisplayName("suggest → aucune prédiction ML/TARIC, repli sur mot-clé trouvé")
    void suggest_emptyMlAndTaric_fallbackKeywordMatch() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0);
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(List.of());
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(List.of());

            HsCodeSuggestion result = service.suggest("Achat d'une voiture rouge");

            assertThat(result.getSuggestedCode1()).isEqualTo("8703");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(1.00));
            assertThat(result.getSuggestedCode2()).isEqualTo("8708");
            assertThat(result.getConfidence2()).isEqualByComparingTo(BigDecimal.valueOf(0.78));
            assertThat(result.getSuggestedCode3()).isEqualTo("8711");
            assertThat(result.getConfidence3()).isEqualByComparingTo(BigDecimal.valueOf(0.44));
        }
    }

    @Test
    @DisplayName("suggest → aucune prédiction, aucun mot-clé, codes génériques par défaut")
    void suggest_emptyMlAndTaric_noKeywordMatch_defaultCodes() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0);
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(List.of());
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(List.of());

            HsCodeSuggestion result = service.suggest("abcdefgh ijklmnop qrstuvwx yz123456");

            assertThat(result.getSuggestedCode1()).isEqualTo("4819");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(1.00));
            assertThat(result.getSuggestedCode2()).isEqualTo("3926");
            assertThat(result.getConfidence2()).isEqualByComparingTo(BigDecimal.valueOf(0.83));
            assertThat(result.getSuggestedCode3()).isEqualTo("7326");
            assertThat(result.getConfidence3()).isEqualByComparingTo(BigDecimal.valueOf(0.67));
        }
    }

    @Test
    @DisplayName("suggest → blended non vide mais confiance <= 0.1, repli mot-clé")
    void suggest_blendedNonEmptyButLowConfidence_fallsBackToKeyword() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0); // modelTrained = false
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(
                List.of(new HsMlService.HsPrediction("X999", "DescX", 0.2, "ml"))
                // confidence becomes 0.2 * 0.5 = 0.1, which is NOT > 0.1
            );
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(List.of());

            HsCodeSuggestion result = service.suggest("Vente de meubles design");

            // fallback keyword path triggered instead of the low-confidence blended result
            assertThat(result.getSuggestedCode1()).isEqualTo("9403");
            assertThat(result.getSuggestedCode1()).isNotEqualTo("X999");
        }
    }

    @Test
    @DisplayName("suggest → un seul résultat blended, uniquement code1 renseigné")
    void suggest_singleBlendedResult_onlyFirstSlotFilled() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(3); // modelTrained = true
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(
                List.of(new HsMlService.HsPrediction("9999", "Unique", 0.8, "ml"))
            );
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(List.of());

            HsCodeSuggestion result = service.suggest("produit unique");

            assertThat(result.getSuggestedCode1()).isEqualTo("9999");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(0.80));
            assertThat(result.getSuggestedCode2()).isNull();
            assertThat(result.getSuggestedDescription2()).isNull();
            assertThat(result.getSuggestedCode3()).isNull();
        }
    }

    @Test
    @DisplayName("suggest → source sémantique seule contribue un code absent du ML/TARIC")
    void suggest_semanticOnlySource_addsNewCode() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0);
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(List.of());
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(List.of());
            when(semanticClassification.classify(anyString(), eq(3))).thenReturn(
                List.of(new SemanticClassificationService.ClassificationResult("8517", "Téléphonie", 0.9))
            );

            HsCodeSuggestion result = service.suggest("GSM dernier cri");

            assertThat(result.getSuggestedCode1()).isEqualTo("8517");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(0.90));
        }
    }

    @Test
    @DisplayName("suggest → source sémantique et TARIC sur le même code se combinent, tag '+semantic'")
    void suggest_semanticMergesWithTaricSameCode() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

            when(hsMlService.getTotalCorrections()).thenReturn(0);
            when(hsMlService.predict(anyString(), eq(3))).thenReturn(List.of());
            when(taricClassification.classify(anyString(), eq(3))).thenReturn(
                List.of(new TaricClassificationService.ClassificationResult("8517", "Appareils télécom", 0.4))
            );
            when(semanticClassification.classify(anyString(), eq(3))).thenReturn(
                List.of(new SemanticClassificationService.ClassificationResult("8517", "Téléphonie", 0.8))
            );

            HsCodeSuggestion result = service.suggest("GSM dernier cri");

            assertThat(result.getSuggestedCode1()).isEqualTo("8517");
            assertThat(result.getConfidence1()).isEqualByComparingTo(BigDecimal.valueOf(0.60));
        }
    }

    // ------------------------------------------------------------------
    // getHistory()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getHistory → délègue au repository pour l'entreprise courante")
    void getHistory_returnsRepoResult() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            HsCodeSuggestion s1 = HsCodeSuggestion.builder().id(UUID.randomUUID()).build();
            when(suggestionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(s1));

            List<HsCodeSuggestion> history = service.getHistory();

            assertThat(history).containsExactly(s1);
        }
    }

    // ------------------------------------------------------------------
    // confirmSelection()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmSelection → code HS invalide (non numerique) rejete avant toute lecture repo")
    void confirmSelection_invalidCodeFormat_throwsAndNeverTouchesRepo() {
        UUID suggestionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.confirmSelection(suggestionId, "DROP TABLE"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(suggestionRepo);
        verifyNoInteractions(hsMlService);
    }

    @Test
    @DisplayName("confirmSelection → code HS vide/blanc rejete")
    void confirmSelection_blankCode_throws() {
        UUID suggestionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.confirmSelection(suggestionId, "   "))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(suggestionRepo);
    }

    @Test
    @DisplayName("confirmSelection → suggestion introuvable")
    void confirmSelection_notFound_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            UUID suggestionId = UUID.randomUUID();
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(suggestionRepo.findById(suggestionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmSelection(suggestionId, "1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Suggestion introuvable");
        }
    }

    @Test
    @DisplayName("confirmSelection → suggestion d'une autre entreprise")
    void confirmSelection_wrongCompany_throws() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            UUID suggestionId = UUID.randomUUID();
            Company otherCompany = Company.builder().id(UUID.randomUUID()).build();
            HsCodeSuggestion suggestion = HsCodeSuggestion.builder()
                .id(suggestionId)
                .company(otherCompany)
                .build();

            ctx.when(TenantContext::get).thenReturn(companyId);
            when(suggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

            assertThatThrownBy(() -> service.confirmSelection(suggestionId, "1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Suggestion introuvable");

            verify(suggestionRepo, never()).save(any());
        }
    }

    @Test
    @DisplayName("confirmSelection → succès, sélection enregistrée")
    void confirmSelection_success() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            UUID suggestionId = UUID.randomUUID();
            HsCodeSuggestion suggestion = HsCodeSuggestion.builder()
                .id(suggestionId)
                .company(company)
                .build();

            ctx.when(TenantContext::get).thenReturn(companyId);
            when(suggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
            when(suggestionRepo.save(suggestion)).thenReturn(suggestion);

            HsCodeSuggestion result = service.confirmSelection(suggestionId, "6101");

            assertThat(result.getUserSelection()).isEqualTo("6101");
            verify(suggestionRepo).save(suggestion);
        }
    }
}
