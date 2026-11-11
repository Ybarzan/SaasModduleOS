package com.fleethub.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catégories de produits comptés lors d'une collecte médicale (pharmacie,
 * laboratoire, établissement de santé). On compte les produits par catégorie,
 * sans identifier chaque tube : aucune donnée patient ne transite par Fleet Hub.
 */
public enum MedicalProduct {
    SANG("Sang"),
    PCR("PCR / écouvillons"),
    URINE("Urines"),
    AUTRE("Autres");

    private final String label;

    MedicalProduct(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Somme des quantités. */
    public static int total(Map<MedicalProduct, Integer> counts) {
        return counts.values().stream().mapToInt(v -> v != null ? v : 0).sum();
    }

    /** Ajoute {@code counts} à {@code into} (catégorie par catégorie). */
    public static void addAll(Map<MedicalProduct, Integer> into, Map<MedicalProduct, Integer> counts) {
        counts.forEach((k, v) -> into.merge(k, v != null ? v : 0, Integer::sum));
    }

    /** Représentation stable « SANG=3,PCR=2 » (ordre des catégories, quantités nulles omises). */
    public static String format(Map<MedicalProduct, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        Map<MedicalProduct, Integer> sorted = new EnumMap<>(MedicalProduct.class);
        sorted.putAll(counts);
        return sorted.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /** Libellé lisible « Sang 3 · PCR / écouvillons 2 ». */
    public static String describe(Map<MedicalProduct, Integer> counts) {
        Map<MedicalProduct, Integer> sorted = new EnumMap<>(MedicalProduct.class);
        sorted.putAll(counts);
        String s = sorted.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(e -> e.getKey().getLabel() + " " + e.getValue())
                .collect(Collectors.joining(" · "));
        return s.isEmpty() ? "aucun produit" : s;
    }
}
