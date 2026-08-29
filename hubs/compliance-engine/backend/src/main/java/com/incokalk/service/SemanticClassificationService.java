package com.incokalk.service;

import com.incokalk.repository.TaricEmbeddingRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Classification par similarité sémantique (embeddings locaux) sur les
 * descriptions TARIC — voir embeddings-service/. Comble le point faible du
 * TF-IDF de TaricClassificationService : une correspondance exacte de tokens
 * ne rapproche jamais "smartphone" de "téléphone intelligent", un embedding le
 * fait naturellement.
 * <p>
 * Une seule source parmi d'autres dans le blend de HsCodeSuggestionService —
 * si le service d'embeddings est indisponible, cette source contribue
 * simplement zéro résultat, jamais une erreur pour l'utilisateur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticClassificationService {

    private final EmbeddingsClient embeddingsClient;
    private final TaricEmbeddingRepository embeddingRepo;

    public record ClassificationResult(String hsCode, String description, double confidence) {
    }

    public List<ClassificationResult> classify(String productDescription, int topN) {
        float[] queryEmbedding = embeddingsClient.encodeOne(productDescription);
        if (queryEmbedding == null) {
            return List.of();
        }

        return embeddingRepo.findNearest(queryEmbedding, topN).stream()
            .map(n -> new ClassificationResult(
                n.hsCode(),
                n.description(),
                // Les vecteurs sont normalises cote service Python : la distance
                // cosinus de pgvector reste dans [0, 2], 1 - distance donne une
                // confiance dans [-1, 1], bornee a [0, 1] par securite.
                Math.max(0.0, Math.min(1.0, 1.0 - n.cosineDistance()))
            ))
            .toList();
    }
}
