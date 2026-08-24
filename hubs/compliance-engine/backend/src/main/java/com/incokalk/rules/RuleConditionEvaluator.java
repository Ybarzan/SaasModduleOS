package com.incokalk.rules;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Evalue un RuleConditionNode contre le contexte (templateData) d'un
 * evenement. Remplace le if-chain plat de NotificationService.matchesFilters
 * par une vraie evaluation d'arbre AND/OR -- voir docs/04-composants-techniques.md.
 *
 * Un champ absent du contexte ne fait jamais matcher la feuille qui le
 * reference (coherent avec le comportement deja etabli pour filterDataSource :
 * un evenement qui ne porte pas l'information ne peut pas satisfaire une
 * condition dessus, plutot que de matcher par defaut).
 */
@Component
public class RuleConditionEvaluator {

    private static final Set<String> LEAF_OPERATORS = Set.of("EQ", "NEQ", "CONTAINS", "IN", "GT", "GTE", "LT", "LTE");

    /**
     * Valide la structure d'un arbre (types connus, feuilles completes, operateurs
     * reconnus, composites non vides) sans dependre d'un contexte d'evaluation --
     * contrairement a evaluate(), qui court-circuite des qu'un champ de feuille est
     * absent du contexte et ne verifierait donc jamais l'operateur avec un contexte
     * vide. Utilise a la creation/modification d'une regle (NotificationService).
     */
    public void validateStructure(RuleConditionNode node) {
        if (node == null) return;
        if (node.getType() == null) {
            throw new IllegalArgumentException("Noeud de condition sans type");
        }
        switch (node.getType()) {
            case "AND", "OR" -> {
                if (node.getChildren() == null || node.getChildren().isEmpty()) {
                    throw new IllegalArgumentException(node.getType() + " sans enfants");
                }
                node.getChildren().forEach(this::validateStructure);
            }
            case "LEAF" -> {
                if (node.getField() == null || node.getOperator() == null) {
                    throw new IllegalArgumentException("Feuille de condition incomplete (field/operator manquant)");
                }
                if (!LEAF_OPERATORS.contains(node.getOperator())) {
                    throw new IllegalArgumentException("Operateur inconnu: " + node.getOperator());
                }
            }
            default -> throw new IllegalArgumentException("Type de noeud inconnu: " + node.getType());
        }
    }

    public boolean evaluate(RuleConditionNode node, Map<String, String> context) {
        if (node == null) return true;
        if (node.getType() == null) {
            throw new IllegalArgumentException("Noeud de condition sans type");
        }
        return switch (node.getType()) {
            case "AND" -> children(node).stream().allMatch(c -> evaluate(c, context));
            case "OR" -> children(node).stream().anyMatch(c -> evaluate(c, context));
            case "LEAF" -> evaluateLeaf(node, context);
            default -> throw new IllegalArgumentException("Type de noeud inconnu: " + node.getType());
        };
    }

    private java.util.List<RuleConditionNode> children(RuleConditionNode node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            throw new IllegalArgumentException(node.getType() + " sans enfants");
        }
        return node.getChildren();
    }

    private boolean evaluateLeaf(RuleConditionNode node, Map<String, String> context) {
        if (node.getField() == null || node.getOperator() == null) {
            throw new IllegalArgumentException("Feuille de condition incomplete (field/operator manquant)");
        }
        String actual = context == null ? null : context.get(node.getField());
        if (actual == null) return false;
        String expected = node.getValue();

        return switch (node.getOperator()) {
            case "EQ" -> actual.equals(expected);
            case "NEQ" -> !actual.equals(expected);
            case "CONTAINS" -> expected != null && actual.contains(expected);
            case "IN" -> expected != null && Arrays.stream(expected.split(","))
                    .map(String::trim)
                    .anyMatch(actual::equals);
            case "GT", "GTE", "LT", "LTE" -> compareNumeric(actual, expected, node.getOperator());
            default -> throw new IllegalArgumentException("Operateur inconnu: " + node.getOperator());
        };
    }

    private boolean compareNumeric(String actual, String expected, String operator) {
        try {
            double a = Double.parseDouble(actual);
            double e = Double.parseDouble(expected);
            return switch (operator) {
                case "GT" -> a > e;
                case "GTE" -> a >= e;
                case "LT" -> a < e;
                case "LTE" -> a <= e;
                default -> false;
            };
        } catch (NumberFormatException | NullPointerException ex) {
            return false;
        }
    }
}
