package com.incokalk.service;

import com.incokalk.repository.CompanyHsEmbeddingRepository;
import com.incokalk.repository.NencEmbeddingRepository;
import com.incokalk.repository.TaricEmbeddingRepository;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticClassificationService")
class SemanticClassificationServiceTest {

    @Mock
    private EmbeddingsClient embeddingsClient;

    @Mock
    private TaricEmbeddingRepository embeddingRepo;

    @Mock
    private CompanyHsEmbeddingRepository companyEmbeddingRepo;

    @Mock
    private TaricRateRepository taricRateRepo;

    @Mock
    private NencEmbeddingRepository nencEmbeddingRepo;

    @InjectMocks
    private SemanticClassificationService service;

    @Test
    @DisplayName("Encode la description puis cherche les plus proches voisins, confiance = 1 - distance")
    void classify_returnsNearestNeighborsAsConfidenceScores() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingsClient.encodeOne("téléphone intelligent")).thenReturn(vector);
        when(embeddingRepo.findNearest(eq(vector), eq(3))).thenReturn(List.of(
            new TaricEmbeddingRepository.Neighbor("8517", "Appareils de télécommunication", 0.05),
            new TaricEmbeddingRepository.Neighbor("8471", "Machines de traitement de données", 0.4)
        ));

        List<SemanticClassificationService.ClassificationResult> results =
            service.classify("téléphone intelligent", 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).hsCode()).isEqualTo("8517");
        assertThat(results.get(0).confidence()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(results.get(1).confidence()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Service d'embeddings indisponible (vecteur null) -> aucune interrogation de l'index, liste vide")
    void classify_embeddingsUnavailable_returnsEmptyWithoutQueryingIndex() {
        when(embeddingsClient.encodeOne(any())).thenReturn(null);

        List<SemanticClassificationService.ClassificationResult> results = service.classify("un produit", 3);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Distance cosinus > 1 (bruit) -> confiance bornée à 0, jamais négative")
    void classify_clampsConfidenceToZeroFloor() {
        float[] vector = {0.1f, 0.2f};
        when(embeddingsClient.encodeOne("produit obscur")).thenReturn(vector);
        when(embeddingRepo.findNearest(eq(vector), eq(1))).thenReturn(List.of(
            new TaricEmbeddingRepository.Neighbor("9999", "Sans rapport", 1.8)
        ));

        List<SemanticClassificationService.ClassificationResult> results = service.classify("produit obscur", 1);

        assertThat(results.get(0).confidence()).isZero();
    }

    // ------------------------------------------------------------------
    // classifyFromCompanyHistory() — Phase B, V72
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Historique d'entreprise -> résout la description OFFICIELLE TARIC, pas le libellé produit historique")
    void classifyFromCompanyHistory_resolvesOfficialDescription_notRawProductText() {
        UUID companyId = UUID.randomUUID();
        float[] vector = {0.4f, 0.5f};
        when(embeddingsClient.encodeOne("iPhone 15 Pro reconditionné")).thenReturn(vector);
        when(companyEmbeddingRepo.findNearest(eq(companyId), eq(vector), eq(3))).thenReturn(List.of(
            // Le "libellé" ici est le texte produit historique de l'entreprise, pas la
            // description officielle du code -- le service doit la résoudre séparément.
            new CompanyHsEmbeddingRepository.Neighbor("8517", "iPhone 15 Pro reconditionné", 0.1)
        ));
        when(taricRateRepo.findDescriptionsByCodes(List.of("8517"))).thenReturn(List.<Object[]>of(
            new Object[]{"8517", "Appareils telephonie smartphones"}
        ));

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromCompanyHistory(companyId, "iPhone 15 Pro reconditionné", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).hsCode()).isEqualTo("8517");
        assertThat(results.get(0).description()).isEqualTo("Appareils telephonie smartphones");
    }

    @Test
    @DisplayName("Code sans correspondance TARIC officielle -> repli sur le libellé historique plutôt qu'un vide")
    void classifyFromCompanyHistory_noOfficialDescriptionFound_fallsBackToHistoricalLabel() {
        UUID companyId = UUID.randomUUID();
        float[] vector = {0.1f};
        when(embeddingsClient.encodeOne("produit interne")).thenReturn(vector);
        when(companyEmbeddingRepo.findNearest(eq(companyId), eq(vector), eq(3))).thenReturn(List.of(
            new CompanyHsEmbeddingRepository.Neighbor("0000", "produit interne custom", 0.2)
        ));
        when(taricRateRepo.findDescriptionsByCodes(List.of("0000"))).thenReturn(List.of());

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromCompanyHistory(companyId, "produit interne", 3);

        assertThat(results.get(0).description()).isEqualTo("produit interne custom");
    }

    @Test
    @DisplayName("Service d'embeddings indisponible -> aucune interrogation de l'historique d'entreprise")
    void classifyFromCompanyHistory_embeddingsUnavailable_neverQueriesRepo() {
        when(embeddingsClient.encodeOne(any())).thenReturn(null);

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromCompanyHistory(UUID.randomUUID(), "un produit", 3);

        assertThat(results).isEmpty();
        verify(companyEmbeddingRepo, never()).findNearest(any(), any(), anyInt());
    }

    // ------------------------------------------------------------------
    // indexConfirmation() — appelé depuis HsCodeSuggestionController au confirm
    // ------------------------------------------------------------------

    @Test
    @DisplayName("indexConfirmation encode puis upsert, scopé à l'entreprise donnée")
    void indexConfirmation_encodesAndUpsertsScopedToCompany() {
        UUID companyId = UUID.randomUUID();
        float[] vector = {0.7f, 0.8f};
        when(embeddingsClient.encodeOne("bottes de sécurité")).thenReturn(vector);

        service.indexConfirmation(companyId, "bottes de sécurité", "6403");

        verify(companyEmbeddingRepo).upsert(companyId, "bottes de sécurité", "6403", vector);
    }

    @Test
    @DisplayName("indexConfirmation : service d'embeddings indisponible -> aucun upsert, pas d'exception")
    void indexConfirmation_embeddingsUnavailable_skipsUpsertSilently() {
        when(embeddingsClient.encodeOne(any())).thenReturn(null);

        service.indexConfirmation(UUID.randomUUID(), "un produit", "1234");

        verify(companyEmbeddingRepo, never()).upsert(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // classifyFromExplanatoryNotes() — Phase C, V73 (NENC)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Encode la description puis cherche dans les notes NENC, confiance = 1 - distance")
    void classifyFromExplanatoryNotes_returnsNearestNeighbors() {
        float[] vector = {0.2f, 0.3f};
        when(embeddingsClient.encodeOne("chevaux sauvages")).thenReturn(vector);
        when(nencEmbeddingRepo.findNearest(eq(vector), eq(3))).thenReturn(List.of(
            new NencEmbeddingRepository.Neighbor("01012910", "Les chevaux sauvages tels que le cheval de Przewalski...", 0.1)
        ));

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromExplanatoryNotes("chevaux sauvages", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).hsCode()).isEqualTo("01012910");
        assertThat(results.get(0).confidence()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Texte NENC > 480 caractères tronqué avec ellipse -- suggested_description_N est VARCHAR(500)")
    void classifyFromExplanatoryNotes_truncatesLongText() {
        float[] vector = {0.1f};
        String longText = "x".repeat(600);
        when(embeddingsClient.encodeOne("produit")).thenReturn(vector);
        when(nencEmbeddingRepo.findNearest(eq(vector), eq(3))).thenReturn(List.of(
            new NencEmbeddingRepository.Neighbor("1234", longText, 0.2)
        ));

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromExplanatoryNotes("produit", 3);

        String description = results.get(0).description();
        assertThat(description).hasSize(480);
        assertThat(description).endsWith("…");
    }

    @Test
    @DisplayName("Texte NENC court -- pas de troncature, retourné tel quel")
    void classifyFromExplanatoryNotes_shortText_notTruncated() {
        float[] vector = {0.1f};
        when(embeddingsClient.encodeOne("orge")).thenReturn(vector);
        when(nencEmbeddingRepo.findNearest(eq(vector), eq(3))).thenReturn(List.of(
            new NencEmbeddingRepository.Neighbor("1003", "Orge", 0.05)
        ));

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromExplanatoryNotes("orge", 3);

        assertThat(results.get(0).description()).isEqualTo("Orge");
    }

    @Test
    @DisplayName("Service d'embeddings indisponible -> aucune interrogation de l'index NENC")
    void classifyFromExplanatoryNotes_embeddingsUnavailable_returnsEmptyWithoutQueryingIndex() {
        when(embeddingsClient.encodeOne(any())).thenReturn(null);

        List<SemanticClassificationService.ClassificationResult> results =
            service.classifyFromExplanatoryNotes("un produit", 3);

        assertThat(results).isEmpty();
        verify(nencEmbeddingRepo, never()).findNearest(any(), anyInt());
    }
}
