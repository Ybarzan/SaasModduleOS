package com.incokalk.service;

import com.incokalk.repository.CompanyHsEmbeddingRepository;
import com.incokalk.repository.TaricEmbeddingRepository;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classification par similarité sémantique (embeddings locaux) — voir
 * embeddings-service/. Comble le point faible du TF-IDF de
 * TaricClassificationService : une correspondance exacte de tokens ne
 * rapproche jamais "smartphone" de "téléphone intelligent", un embedding le
 * fait naturellement.
 * <p>
 * Deux sources distinctes dans le blend de HsCodeSuggestionService :
 * {@link #classify} sur la nomenclature TARIC (globale, publique) et
 * {@link #classifyFromCompanyHistory} sur les classifications déjà
 * confirmées par l'entreprise elle-même (scopée company_id dès sa
 * conception — voir company_hs_embeddings, V72). Si le service d'embeddings
 * est indisponible, ces deux sources contribuent simplement zéro résultat,
 * jamais une erreur pour l'utilisateur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticClassificationService {

    private final EmbeddingsClient embeddingsClient;
    private final TaricEmbeddingRepository embeddingRepo;
    private final CompanyHsEmbeddingRepository companyEmbeddingRepo;
    private final TaricRateRepository taricRateRepo;

    public record ClassificationResult(String hsCode, String description, double confidence) {
    }

    public List<ClassificationResult> classify(String productDescription, int topN) {
        float[] queryEmbedding = embeddingsClient.encodeOne(productDescription);
        if (queryEmbedding == null) {
            return List.of();
        }

        return embeddingRepo.findNearest(queryEmbedding, topN).stream()
            .map(n -> new ClassificationResult(n.hsCode(), n.description(), toConfidence(n.cosineDistance())))
            .toList();
    }

    public List<ClassificationResult> classifyFromCompanyHistory(UUID companyId, String productDescription, int topN) {
        float[] queryEmbedding = embeddingsClient.encodeOne(productDescription);
        if (queryEmbedding == null) {
            return List.of();
        }

        List<CompanyHsEmbeddingRepository.Neighbor> neighbors =
            companyEmbeddingRepo.findNearest(companyId, queryEmbedding, topN);
        if (neighbors.isEmpty()) {
            return List.of();
        }

        // Neighbor.description() est le libellé produit historique de l'entreprise
        // (ex: "iPhone 15 Pro reconditionné"), pas la description officielle du code
        // SH -- on résout la vraie description TARIC pour rester cohérent avec ce que
        // classify() renvoie, plutôt que d'exposer un texte produit à la place.
        Map<String, String> officialDescriptions = resolveOfficialDescriptions(
            neighbors.stream().map(CompanyHsEmbeddingRepository.Neighbor::hsCode).toList()
        );

        return neighbors.stream()
            .map(n -> new ClassificationResult(
                n.hsCode(),
                officialDescriptions.getOrDefault(n.hsCode(), n.description()),
                toConfidence(n.cosineDistance())
            ))
            .toList();
    }

    /**
     * Indexe une classification confirmée dans l'historique sémantique de
     * l'entreprise — appelé depuis HsCodeSuggestionService.confirmSelection().
     * C'est ce qui rend cette source réellement "dynamique" : contrairement à
     * TaricEmbeddingIngestionService (indexation au démarrage), chaque
     * confirmation enrichit l'index tout de suite, sans redémarrage.
     */
    public void indexConfirmation(UUID companyId, String productDescription, String selectedCode) {
        float[] embedding = embeddingsClient.encodeOne(productDescription);
        if (embedding == null) {
            log.warn("[COMPANY-HS-EMBEDDINGS] Service d'embeddings indisponible, confirmation non indexée pour l'entreprise {}", companyId);
            return;
        }
        companyEmbeddingRepo.upsert(companyId, productDescription, selectedCode, embedding);
    }

    private Map<String, String> resolveOfficialDescriptions(List<String> hsCodes) {
        Map<String, String> result = new HashMap<>();
        for (Object[] row : taricRateRepo.findDescriptionsByCodes(hsCodes)) {
            result.put((String) row[0], (String) row[1]);
        }
        return result;
    }

    private static double toConfidence(double cosineDistance) {
        // Vecteurs normalises cote service Python : la distance cosinus de
        // pgvector reste dans [0, 2], 1 - distance donne une confiance dans
        // [-1, 1], bornee a [0, 1] par securite.
        return Math.max(0.0, Math.min(1.0, 1.0 - cosineDistance));
    }
}
