-- Found via a full schema diff (Hibernate ddl-auto=update against a scratch DB
-- vs. the real Flyway-migrated schema) after V69 only fixed the first gap
-- Hibernate's validator happened to hit first. Same root cause as V69: these
-- columns exist in the JPA entities but were never migrated, invisible in local
-- dev because the "local" profile uses ddl-auto=create-drop (schema built
-- straight from entities, bypassing Flyway comparison entirely).
ALTER TABLE client_invoices
    ADD COLUMN client_id UUID REFERENCES client_users(id);

ALTER TABLE deb_declarations
    ADD COLUMN hs_code8 VARCHAR(8);

ALTER TABLE ics2_declarations
    ADD COLUMN hs_code6 VARCHAR(6);

ALTER TABLE tracking_events
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Simulation.goodsValue/totalBuyerCost are Java `double` (no columnDefinition
-- override), so Hibernate expects double precision -- found via a second,
-- type-aware pass of the same schema diff (missing-column diff alone didn't
-- catch this, only Hibernate's own schema-validation error did).
ALTER TABLE simulations
    ALTER COLUMN goods_value TYPE DOUBLE PRECISION USING goods_value::DOUBLE PRECISION,
    ALTER COLUMN total_buyer_cost TYPE DOUBLE PRECISION USING total_buyer_cost::DOUBLE PRECISION;
