package com.incokalk.service;

import com.incokalk.repository.TaricEmbeddingRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticClassificationService")
class SemanticClassificationServiceTest {

    @Mock
    private EmbeddingsClient embeddingsClient;

    @Mock
    private TaricEmbeddingRepository embeddingRepo;

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
}
