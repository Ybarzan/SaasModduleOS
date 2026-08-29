package com.incokalk.service;

import com.incokalk.repository.TaricEmbeddingRepository;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Indexe les descriptions TARIC dans taric_embeddings au démarrage. Incrémental
 * et idempotent : ne (ré)encode que les codes absents de l'index, pas l'intégralité
 * à chaque redémarrage — à ~475 codes distincts un encodage complet serait déjà
 * rapide, mais ça reste inutile de refaire le travail déjà fait.
 * <p>
 * Si le service d'embeddings est indisponible au démarrage, l'indexation est
 * simplement sautée (log warn) — HsCodeSuggestionService continue de fonctionner
 * sans la source sémantique, exactement comme pour toute autre source absente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaricEmbeddingIngestionService {

    private final TaricRateRepository taricRepo;
    private final TaricEmbeddingRepository embeddingRepo;
    private final EmbeddingsClient embeddingsClient;

    @PostConstruct
    public void indexMissingEmbeddings() {
        if (!embeddingsClient.isHealthy()) {
            log.warn("[TARIC-EMBEDDINGS] Service d'embeddings indisponible au démarrage — indexation sautée");
            return;
        }

        Set<String> alreadyIndexed = embeddingRepo.findIndexedHsCodes();

        List<Object[]> allEntries = taricRepo.findDistinctHsCodesWithDescriptions();
        List<Object[]> missing = allEntries.stream()
            .filter(row -> !alreadyIndexed.contains((String) row[0]))
            .toList();

        if (missing.isEmpty()) {
            log.info("[TARIC-EMBEDDINGS] Index déjà à jour ({} codes)", alreadyIndexed.size());
            return;
        }

        List<String> descriptions = new ArrayList<>();
        for (Object[] row : missing) {
            descriptions.add((String) row[1]);
        }

        List<float[]> vectors = embeddingsClient.encode(descriptions);
        if (vectors.size() != missing.size()) {
            log.warn("[TARIC-EMBEDDINGS] Réponse d'encodage incomplète ({} attendus, {} reçus) — indexation abandonnée",
                missing.size(), vectors.size());
            return;
        }

        for (int i = 0; i < missing.size(); i++) {
            String hsCode = (String) missing.get(i)[0];
            String description = (String) missing.get(i)[1];
            embeddingRepo.upsert(hsCode, description, vectors.get(i));
        }

        log.info("[TARIC-EMBEDDINGS] {} nouveaux codes indexés ({} au total)",
            missing.size(), alreadyIndexed.size() + missing.size());
    }
}
