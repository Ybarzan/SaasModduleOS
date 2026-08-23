package com.incokalk.service.ml;

import com.incokalk.model.HsCodeSuggestion;
import com.incokalk.repository.HsCodeSuggestionRepository;
import com.incokalk.repository.TaricRateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HsMlService {

    private final HsCodeSuggestionRepository suggestionRepo;
    private final TaricRateRepository taricRepo;

    private Map<String, Map<String, Integer>> keywordCodeMatrix = new HashMap<>();
    private Map<String, Integer> userCorrectionCount = new HashMap<>();
    private int totalCorrections = 0;

    private static final Set<String> STOP_WORDS = Set.of(
        "le", "la", "les", "de", "des", "du", "un", "une", "et", "ou", "en",
        "pour", "par", "sur", "avec", "sans", "dans", "pas", "plus", "ne",
        "que", "qui", "est", "sont", "a", "au", "aux", "ce", "cette",
        "son", "sa", "ses", "tout", "tous", "toute", "toutes",
        "autre", "autres", "meme", "memes", "fait", "etre", "avoir",
        "comme", "aussi", "bien", "tres", "trop", "peu", "beaucoup",
        "ainsi", "car", "mais", "donc", "si", "non", "oui",
        "dont", "ici", "l", "d", "qu", "n", "the", "an", "of",
        "in", "to", "for", "with", "on", "at", "by", "from", "as", "is",
        "it", "its", "are", "was", "were", "been", "be", "this", "that",
        "these", "those", "not", "no", "or", "if", "so", "about", "into",
        "through", "during", "before", "after", "above", "below", "between",
        "out", "off", "over", "under", "again", "further", "then", "once"
    );

    @PostConstruct
    public void initialize() {
        buildKeywordCodeMatrix();
        buildUserCorrectionModel();
        log.info("[HS-ML] Initialise: {} keywords, {} corrections",
            keywordCodeMatrix.size(), totalCorrections);
    }

    private void buildKeywordCodeMatrix() {
        List<Object[]> taricData = taricRepo.findDistinctHsCodesWithDescriptions();
        for (Object[] row : taricData) {
            String code = (String) row[0];
            String desc = (String) row[1];
            if (code == null || desc == null) continue;

            String[] tokens = tokenize(normalize(desc));
            for (String token : tokens) {
                if (token.length() < 2) continue;
                keywordCodeMatrix
                    .computeIfAbsent(token, k -> new HashMap<>())
                    .merge(code, 1, Integer::sum);
            }
        }
    }

    private void buildUserCorrectionModel() {
        List<HsCodeSuggestion> confirmed = suggestionRepo.findAll().stream()
            .filter(s -> s.getUserSelection() != null && !s.getUserSelection().isBlank())
            .toList();

        for (HsCodeSuggestion suggestion : confirmed) {
            String selectedCode = suggestion.getUserSelection();
            String desc = suggestion.getProductDescription();
            if (desc == null) continue;

            String[] tokens = tokenize(normalize(desc));
            for (String token : tokens) {
                if (token.length() < 2) continue;
                String key = token + ":" + selectedCode;
                userCorrectionCount.merge(key, 1, Integer::sum);
                totalCorrections++;
            }
        }
    }

    public List<HsPrediction> predict(String productDescription, int topN) {
        String normalized = normalize(productDescription);
        String[] queryTokens = tokenize(normalized);

        if (queryTokens.length == 0) return Collections.emptyList();

        Map<String, Double> codeScores = new HashMap<>();

        for (String token : queryTokens) {
            Map<String, Integer> codeFreq = keywordCodeMatrix.get(token);
            if (codeFreq == null) continue;

            for (Map.Entry<String, Integer> entry : codeFreq.entrySet()) {
                String code = entry.getKey();
                double tfidf = (double) entry.getValue() / keywordCodeMatrix.size();
                double correctionBoost = getCorrectionBoost(token, code);
                double score = tfidf + correctionBoost;
                codeScores.merge(code, score, Double::sum);
            }
        }

        if (codeScores.isEmpty()) return Collections.emptyList();

        double maxScore = codeScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        Map<String, String> descriptions = loadCodeDescriptions(
            codeScores.keySet().stream().sorted(
                (a, b) -> Double.compare(codeScores.get(b), codeScores.get(a))
            ).limit(topN).collect(Collectors.toList())
        );

        return codeScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topN)
            .map(e -> {
                String code = e.getKey();
                double rawScore = e.getValue();
                double confidence = Math.min((rawScore / maxScore) * 0.95 + 0.05, 1.0);
                String desc = descriptions.getOrDefault(code, findDescriptionFromTaric(code));
                return new HsPrediction(code, desc, confidence, "ml");
            })
            .toList();
    }

    private double getCorrectionBoost(String token, String code) {
        String key = token + ":" + code;
        Integer corrections = userCorrectionCount.get(key);
        if (corrections == null || totalCorrections == 0) return 0.0;
        return (double) corrections / totalCorrections * 0.3;
    }

    private Map<String, String> loadCodeDescriptions(List<String> codes) {
        if (codes.isEmpty()) return Collections.emptyMap();
        List<Object[]> data = taricRepo.findDescriptionsByCodes(codes);
        Map<String, String> map = new HashMap<>();
        for (Object[] row : data) {
            map.put((String) row[0], (String) row[1]);
        }
        return map;
    }

    private String findDescriptionFromTaric(String hsCode) {
        List<Object[]> results = taricRepo.findDescriptionsByCodes(List.of(hsCode));
        if (!results.isEmpty()) {
            return (String) results.get(0)[1];
        }
        return "";
    }

    public void recordCorrection(String productDescription, String selectedCode) {
        if (productDescription == null || selectedCode == null) return;

        String[] tokens = tokenize(normalize(productDescription));
        for (String token : tokens) {
            if (token.length() < 2) continue;
            String key = token + ":" + selectedCode;
            userCorrectionCount.merge(key, 1, Integer::sum);
            totalCorrections++;
        }
        log.info("[HS-ML] Correction enregistree: '{}' -> {}", productDescription, selectedCode);
    }

    public int getTotalCorrections() {
        return totalCorrections;
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "keywordsInMatrix", keywordCodeMatrix.size(),
            "totalCorrections", totalCorrections,
            "modelTrained", totalCorrections > 0
        );
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.FRENCH);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccents.replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    public static String[] tokenize(String normalized) {
        String[] words = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String w : words) {
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens.toArray(new String[0]);
    }

    public record HsPrediction(String code, String description, double confidence, String source) {}
}
