package com.incokalk.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RuleConditionEvaluator — Tests unitaires")
class RuleConditionEvaluatorTest {

    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator();

    private RuleConditionNode leaf(String field, String op, String value) {
        return RuleConditionNode.builder().type("LEAF").field(field).operator(op).value(value).build();
    }

    // ── evaluate: noeud absent ────────────────────────────────────────

    @Test
    @DisplayName("noeud null -> matche toujours (pas de condition = pas de filtre)")
    void evaluate_nullNode_alwaysMatches() {
        assertThat(evaluator.evaluate(null, Map.of())).isTrue();
    }

    // ── evaluate: operateurs sur une feuille ────────────────────────────

    @Test
    @DisplayName("EQ : valeur identique -> vrai, différente -> faux")
    void evaluate_eq() {
        RuleConditionNode node = leaf("status", "EQ", "DELIVERED");
        assertThat(evaluator.evaluate(node, Map.of("status", "DELIVERED"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("status", "BOOKED"))).isFalse();
    }

    @Test
    @DisplayName("NEQ : inverse de EQ")
    void evaluate_neq() {
        RuleConditionNode node = leaf("status", "NEQ", "DELIVERED");
        assertThat(evaluator.evaluate(node, Map.of("status", "BOOKED"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("status", "DELIVERED"))).isFalse();
    }

    @Test
    @DisplayName("CONTAINS : sous-chaîne")
    void evaluate_contains() {
        RuleConditionNode node = leaf("message", "CONTAINS", "retard");
        assertThat(evaluator.evaluate(node, Map.of("message", "Le camion a du retard"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("message", "Tout va bien"))).isFalse();
    }

    @Test
    @DisplayName("IN : liste séparée par des virgules, avec espaces tolérés")
    void evaluate_in() {
        RuleConditionNode node = leaf("carrier", "IN", "DHL, MSC ,CMA_CGM");
        assertThat(evaluator.evaluate(node, Map.of("carrier", "MSC"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("carrier", "GEODIS"))).isFalse();
    }

    @Test
    @DisplayName("GT/GTE/LT/LTE : comparaison numérique")
    void evaluate_numericComparisons() {
        assertThat(evaluator.evaluate(leaf("confidence", "GT", "70"), Map.of("confidence", "82"))).isTrue();
        assertThat(evaluator.evaluate(leaf("confidence", "GT", "90"), Map.of("confidence", "82"))).isFalse();
        assertThat(evaluator.evaluate(leaf("confidence", "GTE", "82"), Map.of("confidence", "82"))).isTrue();
        assertThat(evaluator.evaluate(leaf("delayDays", "LT", "5"), Map.of("delayDays", "3"))).isTrue();
        assertThat(evaluator.evaluate(leaf("delayDays", "LTE", "3"), Map.of("delayDays", "3"))).isTrue();
    }

    @Test
    @DisplayName("comparaison numérique sur une valeur non numérique -> faux, pas d'exception")
    void evaluate_numericComparison_nonNumeric_returnsFalse() {
        assertThat(evaluator.evaluate(leaf("confidence", "GT", "70"), Map.of("confidence", "haute"))).isFalse();
    }

    @Test
    @DisplayName("champ absent du contexte -> ne matche jamais, quel que soit l'opérateur")
    void evaluate_missingField_neverMatches() {
        assertThat(evaluator.evaluate(leaf("dataSource", "EQ", "LIVE"), Map.of())).isFalse();
        assertThat(evaluator.evaluate(leaf("dataSource", "NEQ", "LIVE"), Map.of())).isFalse();
    }

    // ── evaluate: AND / OR ───────────────────────────────────────────

    @Test
    @DisplayName("AND : vrai seulement si toutes les branches matchent")
    void evaluate_and() {
        RuleConditionNode node = RuleConditionNode.builder().type("AND").children(List.of(
                leaf("newStatus", "EQ", "IN_TRANSIT"),
                leaf("dataSource", "EQ", "LIVE")
        )).build();

        assertThat(evaluator.evaluate(node, Map.of("newStatus", "IN_TRANSIT", "dataSource", "LIVE"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("newStatus", "IN_TRANSIT", "dataSource", "MANUAL"))).isFalse();
    }

    @Test
    @DisplayName("OR : vrai si au moins une branche matche")
    void evaluate_or() {
        RuleConditionNode node = RuleConditionNode.builder().type("OR").children(List.of(
                leaf("newStatus", "EQ", "DELIVERED"),
                leaf("newStatus", "EQ", "CANCELLED")
        )).build();

        assertThat(evaluator.evaluate(node, Map.of("newStatus", "DELIVERED"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("newStatus", "IN_TRANSIT"))).isFalse();
    }

    @Test
    @DisplayName("imbrication AND(OR(...), LEAF) sur plusieurs niveaux")
    void evaluate_nestedAndOr() {
        RuleConditionNode node = RuleConditionNode.builder().type("AND").children(List.of(
                RuleConditionNode.builder().type("OR").children(List.of(
                        leaf("newStatus", "EQ", "DELAYED"),
                        leaf("newStatus", "EQ", "IN_TRANSIT")
                )).build(),
                leaf("dataSource", "EQ", "LIVE")
        )).build();

        assertThat(evaluator.evaluate(node, Map.of("newStatus", "IN_TRANSIT", "dataSource", "LIVE"))).isTrue();
        assertThat(evaluator.evaluate(node, Map.of("newStatus", "BOOKED", "dataSource", "LIVE"))).isFalse();
        assertThat(evaluator.evaluate(node, Map.of("newStatus", "IN_TRANSIT", "dataSource", "MANUAL"))).isFalse();
    }

    // ── evaluate: erreurs ────────────────────────────────────────────

    @Test
    @DisplayName("type inconnu -> exception")
    void evaluate_unknownType_throws() {
        RuleConditionNode node = RuleConditionNode.builder().type("XOR").build();
        assertThatThrownBy(() -> evaluator.evaluate(node, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AND/OR sans enfants -> exception")
    void evaluate_andWithoutChildren_throws() {
        RuleConditionNode node = RuleConditionNode.builder().type("AND").build();
        assertThatThrownBy(() -> evaluator.evaluate(node, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("feuille sans field/operator -> exception")
    void evaluate_incompleteLeaf_throws() {
        RuleConditionNode node = RuleConditionNode.builder().type("LEAF").build();
        assertThatThrownBy(() -> evaluator.evaluate(node, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("opérateur inconnu -> exception")
    void evaluate_unknownOperator_throws() {
        RuleConditionNode node = leaf("status", "STARTS_WITH", "DEL");
        assertThatThrownBy(() -> evaluator.evaluate(node, Map.of("status", "DELIVERED")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── validateStructure ────────────────────────────────────────────

    @Test
    @DisplayName("validateStructure : arbre valide -> ne lève rien")
    void validateStructure_validTree_doesNotThrow() {
        RuleConditionNode node = RuleConditionNode.builder().type("AND").children(List.of(
                leaf("newStatus", "EQ", "IN_TRANSIT")
        )).build();
        evaluator.validateStructure(node);
    }

    @Test
    @DisplayName("validateStructure : opérateur inconnu détecté même sans contexte d'évaluation")
    void validateStructure_unknownOperator_detectedWithoutContext() {
        // Piège evite : evaluate() avec un contexte vide court-circuiterait sur
        // "champ absent" avant meme de lire l'operateur -- validateStructure ne
        // doit pas dependre d'un contexte pour detecter ce genre d'erreur.
        RuleConditionNode node = leaf("status", "STARTS_WITH", "DEL");
        assertThatThrownBy(() -> evaluator.validateStructure(node)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateStructure : noeud null -> ne lève rien")
    void validateStructure_null_doesNotThrow() {
        evaluator.validateStructure(null);
    }
}
