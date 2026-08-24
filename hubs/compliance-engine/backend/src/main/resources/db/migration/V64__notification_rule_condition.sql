-- Moteur de regles structure (docs/04-composants-techniques.md, Phase 3 J3-J5
-- de docs/03-plan-migration.md). filter_status/filter_carrier_id/filter_data_source
-- (V60-V62) ne peuvent exprimer qu'un ET d'egalites plates. condition_json porte
-- un arbre de condition composee (AND/OR, plusieurs operateurs) -- voir
-- RuleConditionNode/RuleConditionEvaluator. NULL = pas de condition structuree,
-- l'ancien filtrage plat reste utilise (retro-compatible, aucune regle existante
-- ne change de comportement).
ALTER TABLE notification_rules
    ADD COLUMN condition_json TEXT;
