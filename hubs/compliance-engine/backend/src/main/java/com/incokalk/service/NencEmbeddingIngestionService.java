package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.repository.NencEmbeddingRepository;
import com.incokalk.service.ml.EmbeddingsClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Indexe les notes explicatives de la nomenclature combinée (NENC, ~2935
 * codes) dans nenc_embeddings au démarrage. Même principe exact que
 * TaricEmbeddingIngestionService (V71) : incrémental et idempotent, ne
 * (ré)encode que les codes absents de l'index. Contrairement à TARIC, la
 * source n'est pas une table déjà en base mais une ressource JSON bundlée
 * (data/nenc-cn-explanatory-notes.json) -- voir SOURCES.md pour la
 * provenance et la licence.
 * <p>
 * Encodé par lots (250 textes/appel) plutôt qu'en un seul appel pour ~2935
 * entrées : progression visible dans les logs, et un lot en échec ne perd
 * pas le travail déjà fait par les précédents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NencEmbeddingIngestionService {

    private static final int BATCH_SIZE = 250;
    private static final String RESOURCE_PATH = "data/nenc-cn-explanatory-notes.json";

    private final NencEmbeddingRepository embeddingRepo;
    private final EmbeddingsClient embeddingsClient;
    private final ObjectMapper objectMapper;

    private record NencEntry(String code, String text) {
    }

    @PostConstruct
    public void indexMissingEmbeddings() {
        if (!embeddingsClient.isHealthy()) {
            log.warn("[NENC-EMBEDDINGS] Service d'embeddings indisponible au démarrage — indexation sautée");
            return;
        }

        List<NencEntry> allEntries;
        try {
            allEntries = List.of(objectMapper.readValue(
                new ClassPathResource(RESOURCE_PATH).getInputStream(), NencEntry[].class));
        } catch (Exception e) {
            log.error("[NENC-EMBEDDINGS] Impossible de lire {}: {}", RESOURCE_PATH, e.getMessage());
            return;
        }

        Set<String> alreadyIndexed = embeddingRepo.findIndexedCodes();
        List<NencEntry> missing = allEntries.stream()
            .filter(e -> !alreadyIndexed.contains(e.code()))
            .toList();

        if (missing.isEmpty()) {
            log.info("[NENC-EMBEDDINGS] Index déjà à jour ({} codes)", alreadyIndexed.size());
            return;
        }

        int indexed = 0;
        for (int start = 0; start < missing.size(); start += BATCH_SIZE) {
            List<NencEntry> batch = missing.subList(start, Math.min(start + BATCH_SIZE, missing.size()));
            List<String> texts = batch.stream().map(NencEntry::text).toList();

            List<float[]> vectors = embeddingsClient.encode(texts);
            if (vectors.size() != batch.size()) {
                log.warn("[NENC-EMBEDDINGS] Lot {}: réponse incomplète ({} attendus, {} reçus) — lot abandonné",
                    start / BATCH_SIZE, batch.size(), vectors.size());
                continue;
            }

            for (int i = 0; i < batch.size(); i++) {
                embeddingRepo.upsert(batch.get(i).code(), batch.get(i).text(), vectors.get(i));
            }
            indexed += batch.size();
            log.info("[NENC-EMBEDDINGS] Lot {}: {} codes indexés ({}/{})",
                start / BATCH_SIZE, batch.size(), indexed, missing.size());
        }

        log.info("[NENC-EMBEDDINGS] {} nouveaux codes indexés ({} au total)",
            indexed, alreadyIndexed.size() + indexed);
    }
}
