-- Dernier morceau de la Phase 3 J6-J8 (docs/03-plan-migration.md) : l'executeur
-- reel qui consomme une suggestion APPROVED. Le resultat de cette execution
-- (succes détaillé ou raison d'echec/de blocage par la gouvernance) est distinct
-- de decision_note (qui capture la note humaine au moment de la decision) --
-- docs/04-composants-techniques.md demande de journaliser decision ET resultat
-- separement.

ALTER TABLE orchestration_suggestions
    ADD COLUMN execution_result VARCHAR(1000);
