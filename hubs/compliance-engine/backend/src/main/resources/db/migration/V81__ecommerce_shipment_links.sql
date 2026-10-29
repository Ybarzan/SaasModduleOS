-- V81__ecommerce_shipment_links.sql
-- Phase 1 J4 (docs/03-plan-migration.md) : corrélation manuelle, saisie par
-- l'opérateur, entre une expédition fleet-hub (fleethub_shipment_id, UUID --
-- aucune FK possible, fleet-hub a sa propre base) et la commande e-commerce
-- d'origine (integration_id + external_order_id). Permet à
-- EventOutboxProcessor de déclencher automatiquement markOrderShipped() dès
-- que fleet-hub signale le passage en IN_TRANSIT pour cette expédition, sans
-- que l'appelant ait à résoudre externalOrderId lui-même à chaque fois.

CREATE TABLE ecommerce_shipment_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    integration_id UUID NOT NULL REFERENCES ecommerce_integrations(id) ON DELETE CASCADE,
    external_order_id VARCHAR(100) NOT NULL,
    fleethub_shipment_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_esl_order UNIQUE (integration_id, external_order_id),
    CONSTRAINT uq_esl_shipment UNIQUE (fleethub_shipment_id)
);

CREATE INDEX idx_esl_company ON ecommerce_shipment_links(company_id);
