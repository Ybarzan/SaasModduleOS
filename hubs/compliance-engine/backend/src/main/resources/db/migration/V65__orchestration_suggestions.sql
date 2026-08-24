-- Actions candidates + contraintes de gouvernance (docs/04-composants-techniques.md,
-- prerequis de l'executeur d'actions -- Phase 3 J6-J8 de docs/03-plan-migration.md).
--
-- Principe non negociable (docs/05-estimation-couts-risques.md, risque R1) : une
-- nouvelle categorie d'action demarre toujours en mode "suggestion a valider", jamais
-- en execution automatique silencieuse. Cette migration ajoute le vocabulaire d'action
-- + gouvernance a notification_rules, et une table orchestration_suggestions qui ne
-- represente qu'une PROPOSITION -- rien n'est execute a la creation d'une ligne ici.
-- L'execution reelle (appel a ErpProvider.exportOrders, etc.) est un chantier separe,
-- volontairement pas fait dans cette passe.

ALTER TABLE notification_rules
    ADD COLUMN action_type VARCHAR(50),
    ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN max_budget_amount NUMERIC(15,2),
    ADD COLUMN allowed_carrier_ids TEXT;

CREATE TABLE orchestration_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    rule_id UUID NOT NULL REFERENCES notification_rules(id),
    shipment_id UUID,
    action_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    context_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    decided_at TIMESTAMP,
    decided_by_user_id UUID,
    decision_note VARCHAR(1000)
);

CREATE INDEX idx_orchestration_suggestions_company_status ON orchestration_suggestions (company_id, status);
