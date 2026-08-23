package com.incokalk.service;

import com.incokalk.repository.TaricRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaricClassificationService {

    private final TaricRateRepository taricRepo;

    private record TaricEntry(String hsCode, String description, String[] tokens, Map<String, Integer> tokenFreq) {}

    private List<TaricEntry> taricEntries = Collections.emptyList();
    private Map<String, Double> idf = Collections.emptyMap();

    @PostConstruct
    public void loadTaricData() {
        List<Object[]> raw = taricRepo.findDistinctHsCodesWithDescriptions();
        List<TaricEntry> entries = new ArrayList<>();
        Map<String, Integer> docFreq = new HashMap<>();
        int totalDocs = 0;

        for (Object[] row : raw) {
            String code = (String) row[0];
            String desc = (String) row[1];
            if (code == null || desc == null) continue;
            String normalized = normalize(desc);
            String[] tokens = tokenize(normalized);
            Map<String, Integer> freq = new HashMap<>();
            for (String t : tokens) {
                freq.merge(t, 1, Integer::sum);
            }
            Set<String> uniqueTokens = new HashSet<>(freq.keySet());
            for (String t : uniqueTokens) {
                docFreq.merge(t, 1, Integer::sum);
            }
            entries.add(new TaricEntry(code, desc, tokens, freq));
            totalDocs++;
        }

        this.taricEntries = entries;
        this.idf = new HashMap<>();
        for (Map.Entry<String, Integer> e : docFreq.entrySet()) {
            this.idf.put(e.getKey(), Math.log((double) totalDocs / (1 + e.getValue())));
        }

        log.info("[TARIC-CLASSIF] Chargé {} descriptions, {} tokens uniques", entries.size(), idf.size());
    }

    public List<ClassificationResult> classify(String productDescription, int topN) {
        String normalized = normalize(productDescription);
        String[] queryTokens = tokenize(normalized);
        if (queryTokens.length == 0) return Collections.emptyList();

        Map<String, Integer> queryFreq = new HashMap<>();
        for (String t : queryTokens) {
            queryFreq.merge(t, 1, Integer::sum);
        }

        double[] queryTfIdf = new double[queryTokens.length];
        for (int i = 0; i < queryTokens.length; i++) {
            double tf = (double) queryFreq.get(queryTokens[i]) / queryTokens.length;
            double idfVal = idf.getOrDefault(queryTokens[i], 0.0);
            queryTfIdf[i] = tf * idfVal;
        }

        double queryNorm = 0;
        for (double v : queryTfIdf) queryNorm += v * v;
        queryNorm = Math.sqrt(queryNorm);
        if (queryNorm == 0) return Collections.emptyList();

        Map<String, double[]> codeScores = new HashMap<>();

        for (TaricEntry entry : taricEntries) {
            double[] entryTfIdf = new double[entry.tokens().length];
            for (int i = 0; i < entry.tokens().length; i++) {
                double tf = (double) entry.tokenFreq().getOrDefault(entry.tokens()[i], 0) / entry.tokens().length;
                double idfVal = idf.getOrDefault(entry.tokens()[i], 0.0);
                entryTfIdf[i] = tf * idfVal;
            }

            double entryNorm = 0;
            for (double v : entryTfIdf) entryNorm += v * v;
            entryNorm = Math.sqrt(entryNorm);
            if (entryNorm == 0) continue;

            double dotProduct = 0;
            for (int i = 0; i < queryTokens.length; i++) {
                for (int j = 0; j < entry.tokens().length; j++) {
                    if (queryTokens[i].equals(entry.tokens()[j])) {
                        dotProduct += queryTfIdf[i] * entryTfIdf[j];
                        break;
                    }
                }
            }

            double cosine = dotProduct / (queryNorm * entryNorm);

            codeScores.merge(entry.hsCode(), new double[]{cosine, 0}, (a, b) -> new double[]{Math.max(a[0], cosine), 0});
        }

        return codeScores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
            .limit(topN)
            .map(e -> {
                double score = e.getValue()[0];
                double confidence = Math.min(score * 1.2, 1.0);
                String desc = findBestDescriptionForCode(e.getKey());
                return new ClassificationResult(e.getKey(), desc, confidence);
            })
            .toList();
    }

    private String findBestDescriptionForCode(String hsCode) {
        for (TaricEntry entry : taricEntries) {
            if (entry.hsCode().equals(hsCode)) {
                return entry.description();
            }
        }
        return "";
    }

    private String normalize(String text) {
        String lower = text.toLowerCase(Locale.FRENCH);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccents.replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    private String[] tokenize(String normalized) {
        String[] words = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String w : words) {
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens.toArray(new String[0]);
    }

    public record ClassificationResult(String hsCode, String description, double confidence) {}

    private static final Set<String> STOP_WORDS = new java.util.HashSet<>(java.util.List.of(
        "le", "la", "les", "de", "des", "du", "un", "une", "et", "ou", "en",
        "pour", "par", "sur", "avec", "sans", "dans", "pas", "plus", "ne",
        "que", "qui", "est", "sont", "a", "au", "aux", "ce", "cette",
        "son", "sa", "ses", "tout", "tous", "toute", "toutes",
        "autre", "autres", "meme", "memes", "fait", "etre", "avoir",
        "comme", "aussi", "bien", "tres", "trop", "peu", "beaucoup",
        "ainsi", "car", "mais", "donc", "si", "non", "oui",
        "dont", "ici", "l", "d", "qu", "n"
    ));
}
