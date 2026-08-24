package com.incokalk.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Noeud d'un arbre de condition composee pour le moteur de regles
 * (docs/04-composants-techniques.md du monorepo SaasModduleOS, "moteur de
 * regles intelligent" -- Phase 3 J3-J5 de docs/03-plan-migration.md).
 *
 * Deux formes : composite (AND/OR avec des enfants) ou feuille (un champ
 * du contexte de l'evenement compare a une valeur). Serialise en JSON dans
 * NotificationRule.conditionJson. Exemple :
 *
 * {"type":"AND","children":[
 *   {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"},
 *   {"type":"LEAF","field":"dataSource","operator":"EQ","value":"MANUAL"}
 * ]}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConditionNode {

    /** AND, OR, ou LEAF. */
    private String type;

    // ---- LEAF uniquement ----
    private String field;
    /** EQ, NEQ, CONTAINS, IN (valeur = liste separee par des virgules), GT, GTE, LT, LTE. */
    private String operator;
    private String value;

    // ---- AND / OR uniquement ----
    private List<RuleConditionNode> children;
}
