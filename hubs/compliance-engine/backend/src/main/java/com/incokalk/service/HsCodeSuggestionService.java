package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.HsCodeSuggestion;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.HsCodeSuggestionRepository;
import com.incokalk.service.ml.HsMlService;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HsCodeSuggestionService {

    private final HsCodeSuggestionRepository suggestionRepo;
    private final CompanyRepository companyRepo;
    private final TaricClassificationService taricClassification;
    private final HsMlService hsMlService;

    public record HsSuggestion(String code, String description, double confidence, String source) {}

    // hsMlService.recordCorrection() alimente un modele GLOBAL, partage par toutes les
    // entreprises (pas de scoping par companyId). Un code invalide/non-numerique saisi
    // par une seule entreprise polluerait donc les suggestions de tous les autres locataires.
    private static final Pattern HS_CODE_PATTERN = Pattern.compile("^\\d{4,10}$");

    private static final Map<String, List<HsSuggestion>> KEYWORD_HS_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_HS_MAP.put("vêtements", List.of(
            new HsSuggestion("6101", "Vêtements extérieurs tricotés ou crochétés, hommes", 0.8, "keyword"),
            new HsSuggestion("6201", "Vêtements extérieurs non tricotés, hommes", 0.8, "keyword"),
            new HsSuggestion("6110", "Pull-over, cardigans, gilets tricotés", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("habillement", List.of(
            new HsSuggestion("6101", "Vêtements extérieurs tricotés ou crochétés, hommes", 0.8, "keyword"),
            new HsSuggestion("6201", "Vêtements extérieurs non tricotés, hommes", 0.8, "keyword"),
            new HsSuggestion("6204", "Ensembles femme, jupes, pantalons", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("textile", List.of(
            new HsSuggestion("6101", "Vêtements extérieurs tricotés, hommes", 0.6, "keyword"),
            new HsSuggestion("6201", "Vêtements extérieurs non tricotés, hommes", 0.6, "keyword"),
            new HsSuggestion("5208", "Tissus de coton", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("tissu", List.of(
            new HsSuggestion("5208", "Tissus de coton", 0.8, "keyword"),
            new HsSuggestion("5407", "Tissus de filaments synthétiques", 0.7, "keyword"),
            new HsSuggestion("5516", "Tissus de fibres artificielles", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("électronique", List.of(
            new HsSuggestion("8471", "Machines automatiques de traitement de données", 0.8, "keyword"),
            new HsSuggestion("8517", "Appareils de télécommunication", 0.7, "keyword"),
            new HsSuggestion("8528", "Moniteurs, projecteurs, équipements de visualisation", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("informatique", List.of(
            new HsSuggestion("8471", "Machines automatiques de traitement de données", 0.8, "keyword"),
            new HsSuggestion("8473", "Parties et accessoires de machines de traitement", 0.7, "keyword"),
            new HsSuggestion("8517", "Appareils de télécommunication", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("ordinateur", List.of(
            new HsSuggestion("8471", "Machines automatiques de traitement de données", 0.9, "keyword"),
            new HsSuggestion("8473", "Parties et accessoires de machines de traitement", 0.7, "keyword"),
            new HsSuggestion("8528", "Moniteurs, écrans", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("téléphone", List.of(
            new HsSuggestion("8517", "Appareils de télécommunication (téléphones)", 0.9, "keyword"),
            new HsSuggestion("8518", "Microphones, haut-parleurs, amplificateurs", 0.5, "keyword"),
            new HsSuggestion("8504", "Transformateurs, convertisseurs statiques", 0.4, "keyword")
        ));
        KEYWORD_HS_MAP.put("voiture", List.of(
            new HsSuggestion("8703", "Voitures automobiles et autres véhicules", 0.9, "keyword"),
            new HsSuggestion("8708", "Parties et accessoires pour véhicules", 0.7, "keyword"),
            new HsSuggestion("8711", "Motocyclettes", 0.4, "keyword")
        ));
        KEYWORD_HS_MAP.put("automobile", List.of(
            new HsSuggestion("8703", "Voitures automobiles et autres véhicules", 0.9, "keyword"),
            new HsSuggestion("8708", "Parties et accessoires pour véhicules", 0.7, "keyword"),
            new HsSuggestion("8507", "Accumulateurs électriques", 0.4, "keyword")
        ));
        KEYWORD_HS_MAP.put("véhicule", List.of(
            new HsSuggestion("8703", "Voitures automobiles et autres véhicules", 0.8, "keyword"),
            new HsSuggestion("8708", "Parties et accessoires pour véhicules", 0.7, "keyword"),
            new HsSuggestion("8711", "Motocyclettes", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("jouet", List.of(
            new HsSuggestion("9503", "Travaux manuels, modèles, jeux, jouets", 0.9, "keyword"),
            new HsSuggestion("9504", "Jeux de société, jeux vidéo", 0.7, "keyword"),
            new HsSuggestion("9505", "Articles de fêtes, articles de carnival", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("alimentaire", List.of(
            new HsSuggestion("1905", "Pains, pâtisseries, gâteaux", 0.8, "keyword"),
            new HsSuggestion("2202", "Boissons non alcoolisées", 0.7, "keyword"),
            new HsSuggestion("2106", "Autres préparations alimentaires", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("nourriture", List.of(
            new HsSuggestion("1905", "Pains, pâtisseries, gâteaux", 0.8, "keyword"),
            new HsSuggestion("2202", "Boissons non alcoolisées", 0.7, "keyword"),
            new HsSuggestion("2106", "Autres préparations alimentaires", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("boisson", List.of(
            new HsSuggestion("2202", "Boissons non alcoolisées eau gazeuse", 0.9, "keyword"),
            new HsSuggestion("2203", "Bière de malt", 0.7, "keyword"),
            new HsSuggestion("2204", "Vins de raisins frais", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("meuble", List.of(
            new HsSuggestion("9403", "Autres mobiliers et leurs parties", 0.9, "keyword"),
            new HsSuggestion("9401", "Sièges et leurs parties", 0.8, "keyword"),
            new HsSuggestion("9404", "Matelas, accessoires de literie", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("mobiliers", List.of(
            new HsSuggestion("9403", "Autres mobiliers et leurs parties", 0.9, "keyword"),
            new HsSuggestion("9401", "Sièges et leurs parties", 0.8, "keyword"),
            new HsSuggestion("9405", "Luminaires et éclairage", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("chaussures", List.of(
            new HsSuggestion("6403", "Chaussures à semelles de caoutchouc", 0.9, "keyword"),
            new HsSuggestion("6402", "Chaussures à semelles de plastique", 0.8, "keyword"),
            new HsSuggestion("6404", "Chaussures à semelles textiles", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("bottes", List.of(
            new HsSuggestion("6403", "Chaussures à semelles de caoutchouc (bottes)", 0.9, "keyword"),
            new HsSuggestion("6402", "Chaussures à semelles de plastique", 0.7, "keyword"),
            new HsSuggestion("6404", "Chaussures à semelles textiles", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("bijoux", List.of(
            new HsSuggestion("7113", "Articles de bijouterie en métal précieux", 0.9, "keyword"),
            new HsSuggestion("7116", "Articles en perles, pierres précieuses", 0.7, "keyword"),
            new HsSuggestion("7117", "Bijoux fantaisie", 0.8, "keyword")
        ));
        KEYWORD_HS_MAP.put("or", List.of(
            new HsSuggestion("7113", "Articles de bijouterie en or", 0.9, "keyword"),
            new HsSuggestion("7108", "Or en lingots, poudre", 0.7, "keyword"),
            new HsSuggestion("7117", "Bijoux fantaisie", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("argent", List.of(
            new HsSuggestion("7113", "Articles de bijouterie en argent", 0.8, "keyword"),
            new HsSuggestion("7106", "Argent en lingots, poudre", 0.7, "keyword"),
            new HsSuggestion("7117", "Bijoux fantaisie", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("cosmétique", List.of(
            new HsSuggestion("3303", "Parfums et eaux de toilette", 0.9, "keyword"),
            new HsSuggestion("3304", "Produits de maquillage, soins de la peau", 0.9, "keyword"),
            new HsSuggestion("3305", "Produits pour les cheveux", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("parfum", List.of(
            new HsSuggestion("3303", "Parfums et eaux de toilette", 0.95, "keyword"),
            new HsSuggestion("3304", "Produits de maquillage", 0.5, "keyword"),
            new HsSuggestion("3305", "Produits pour les cheveux", 0.4, "keyword")
        ));
        KEYWORD_HS_MAP.put("médicament", List.of(
            new HsSuggestion("3004", "Médicaments à usage thérapeutique", 0.95, "keyword"),
            new HsSuggestion("3003", "Médicaments en doses, non mis en forme final", 0.8, "keyword"),
            new HsSuggestion("3006", "Produits pharmaceutiques divers", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("pharmaceutique", List.of(
            new HsSuggestion("3004", "Médicaments à usage thérapeutique", 0.9, "keyword"),
            new HsSuggestion("3003", "Médicaments en doses", 0.8, "keyword"),
            new HsSuggestion("3006", "Produits pharmaceutiques divers", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("machine", List.of(
            new HsSuggestion("8401", "Réacteurs nucléaires, machines et appareils", 0.8, "keyword"),
            new HsSuggestion("8409", "Parties pour machines à moteur à piston", 0.7, "keyword"),
            new HsSuggestion("8418", "Réfrigérateurs, congélateurs", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("moteur", List.of(
            new HsSuggestion("8407", "Moteurs à piston à allumage par bougie", 0.9, "keyword"),
            new HsSuggestion("8409", "Parties pour machines à moteur à piston", 0.8, "keyword"),
            new HsSuggestion("8501", "Moteurs et dynamos électriques", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("bois", List.of(
            new HsSuggestion("4421", "Autres articles en bois", 0.8, "keyword"),
            new HsSuggestion("4407", "Bois sciés, fléché de plus de 6mm", 0.7, "keyword"),
            new HsSuggestion("4411", "Panneaux de fibres de bois", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("menuiserie", List.of(
            new HsSuggestion("4421", "Autres articles en bois (menuiserie)", 0.9, "keyword"),
            new HsSuggestion("4418", "Menuiserie de bâtiments, parquets", 0.8, "keyword"),
            new HsSuggestion("9403", "Autres mobiliers", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("verre", List.of(
            new HsSuggestion("7013", "Articles de verrerie de table, déco", 0.9, "keyword"),
            new HsSuggestion("7007", "Verre de sécurité (trempé, feuilleté)", 0.7, "keyword"),
            new HsSuggestion("7005", "Verre plat nonWorked", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("cristal", List.of(
            new HsSuggestion("7013", "Articles de cristal, verrerie de table", 0.9, "keyword"),
            new HsSuggestion("7018", "Articles de verre pour parure, bijoux", 0.7, "keyword"),
            new HsSuggestion("7010", "Flacons, bouteilles en verre", 0.5, "keyword")
        ));
        KEYWORD_HS_MAP.put("papier", List.of(
            new HsSuggestion("4819", "Cartonnage, boîtes en papier/carton", 0.8, "keyword"),
            new HsSuggestion("4818", "Papier de toilette, mouchoirs", 0.7, "keyword"),
            new HsSuggestion("4810", "Papier recouvert en pâte", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("carton", List.of(
            new HsSuggestion("4819", "Cartonnage, boîtes en papier/carton", 0.9, "keyword"),
            new HsSuggestion("4818", "Papier de toilette, mouchoirs", 0.5, "keyword"),
            new HsSuggestion("4820", "Registres, classeurs", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("caoutchouc", List.of(
            new HsSuggestion("4015", "Gants en caoutchouc", 0.8, "keyword"),
            new HsSuggestion("3926", "Articles divers en matières plastiques", 0.7, "keyword"),
            new HsSuggestion("4016", "Articles en caoutchouc vulcanisé", 0.7, "keyword")
        ));
        KEYWORD_HS_MAP.put("plastique", List.of(
            new HsSuggestion("3926", "Articles divers en matières plastiques", 0.9, "keyword"),
            new HsSuggestion("3917", "Tubes, tuyaux en matières plastiques", 0.7, "keyword"),
            new HsSuggestion("3920", "Feuilles en matières plastiques", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("métal", List.of(
            new HsSuggestion("7326", "Articles divers en fer ou acier", 0.8, "keyword"),
            new HsSuggestion("7210", "Tôles bandes en fer ou acier laminé", 0.7, "keyword"),
            new HsSuggestion("7616", "Articles divers en aluminium", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("acier", List.of(
            new HsSuggestion("7210", "Tôles bandes en fer ou acier laminé revêtu", 0.9, "keyword"),
            new HsSuggestion("7326", "Articles divers en fer ou acier", 0.8, "keyword"),
            new HsSuggestion("7318", "Vis, boulons, écrous en fer ou acier", 0.6, "keyword")
        ));
        KEYWORD_HS_MAP.put("fer", List.of(
            new HsSuggestion("7326", "Articles divers en fer ou acier", 0.8, "keyword"),
            new HsSuggestion("7210", "Tôles bandes en fer ou acier laminé", 0.7, "keyword"),
            new HsSuggestion("7318", "Vis, boulons, écrous en fer ou acier", 0.6, "keyword")
        ));
    }

    @Transactional
    public HsCodeSuggestion suggest(String productDescription) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        List<HsSuggestion> results;

        // 1. Get ML model predictions (learns from user corrections)
        List<HsMlService.HsPrediction> mlPredictions = hsMlService.predict(productDescription, 3);

        // 2. Get TARIC-based TF-IDF classification
        List<TaricClassificationService.ClassificationResult> taricResults =
            taricClassification.classify(productDescription, 3);

        // 3. Blend ML + TARIC results with ML having higher weight if model is trained
        Map<String, HsSuggestion> blended = new LinkedHashMap<>();

        boolean modelTrained = hsMlService.getTotalCorrections() > 0;

        for (HsMlService.HsPrediction ml : mlPredictions) {
            String source = modelTrained ? "ml" : "keyword";
            double confidence = modelTrained ? ml.confidence() : ml.confidence() * 0.5;
            blended.put(ml.code(), new HsSuggestion(ml.code(), ml.description(), confidence, source));
        }

        for (TaricClassificationService.ClassificationResult t : taricResults) {
            String code = t.hsCode();
            double tConfidence = t.confidence() * (modelTrained ? 0.4 : 1.0);
            if (blended.containsKey(code)) {
                HsSuggestion existing = blended.get(code);
                double combined = (existing.confidence() + tConfidence) / 2;
                blended.put(code, new HsSuggestion(code, existing.description(), combined,
                    existing.source() + "+taric"));
            } else if (tConfidence >= 0.15) {
                blended.put(code, new HsSuggestion(code, t.description(), tConfidence, "taric"));
            }
        }

        if (!blended.isEmpty() && blended.values().stream().anyMatch(s -> s.confidence() > 0.1)) {
            results = blended.values().stream()
                .sorted(Comparator.comparingDouble(HsSuggestion::confidence).reversed())
                .limit(3)
                .collect(Collectors.toList());
        } else {
            // 4. Fallback to keyword map
            String normalized = productDescription.toLowerCase(Locale.FRENCH).trim();
            Map<String, Double> codeScores = new HashMap<>();
            Map<String, String> codeDescriptions = new HashMap<>();

            for (Map.Entry<String, List<HsSuggestion>> entry : KEYWORD_HS_MAP.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    for (HsSuggestion suggestion : entry.getValue()) {
                        codeScores.merge(suggestion.code(), suggestion.confidence(), Double::sum);
                        codeDescriptions.putIfAbsent(suggestion.code(), suggestion.description());
                    }
                }
            }

            if (codeScores.isEmpty()) {
                codeScores.put("4819", 0.3);
                codeDescriptions.put("4819", "Cartonnage — code generique par defaut");
                codeScores.put("3926", 0.25);
                codeDescriptions.put("3926", "Articles divers en matieres plastiques");
                codeScores.put("7326", 0.2);
                codeDescriptions.put("7326", "Articles divers en fer ou acier");
            }

            double maxScore = codeScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

            results = codeScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(e -> new HsSuggestion(
                    e.getKey(),
                    codeDescriptions.get(e.getKey()),
                    Math.min(e.getValue() / maxScore, 1.0),
                    "keyword"
                ))
                .collect(Collectors.toList());
        }

        HsCodeSuggestion record = HsCodeSuggestion.builder()
            .company(company)
            .productDescription(productDescription)
            .build();

        if (results.size() > 0) {
            record.setSuggestedCode1(results.get(0).code());
            record.setSuggestedDescription1(results.get(0).description());
            record.setConfidence1(BigDecimal.valueOf(results.get(0).confidence()).setScale(2, RoundingMode.HALF_UP));
        }
        if (results.size() > 1) {
            record.setSuggestedCode2(results.get(1).code());
            record.setSuggestedDescription2(results.get(1).description());
            record.setConfidence2(BigDecimal.valueOf(results.get(1).confidence()).setScale(2, RoundingMode.HALF_UP));
        }
        if (results.size() > 2) {
            record.setSuggestedCode3(results.get(2).code());
            record.setSuggestedDescription3(results.get(2).description());
            record.setConfidence3(BigDecimal.valueOf(results.get(2).confidence()).setScale(2, RoundingMode.HALF_UP));
        }

        suggestionRepo.save(record);

        log.info("HS suggestions pour '{}': {} resultats (top: {}, source: {})",
            productDescription, results.size(),
            results.isEmpty() ? "none" : results.get(0).code(),
            results.isEmpty() ? "none" : results.get(0).source());

        return record;
    }

    public List<HsCodeSuggestion> getHistory() {
        UUID companyId = TenantContext.get();
        return suggestionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public HsCodeSuggestion confirmSelection(UUID suggestionId, String selectedCode) {
        if (selectedCode == null || !HS_CODE_PATTERN.matcher(selectedCode).matches()) {
            throw new IllegalArgumentException("Code HS invalide: " + selectedCode);
        }

        UUID companyId = TenantContext.get();
        HsCodeSuggestion suggestion = suggestionRepo.findById(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion introuvable"));

        if (!suggestion.getCompany().getId().equals(companyId)) {
            throw new IllegalArgumentException("Suggestion introuvable");
        }

        suggestion.setUserSelection(selectedCode);
        return suggestionRepo.save(suggestion);
    }
}
