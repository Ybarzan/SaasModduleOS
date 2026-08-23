-- V30__fix_jsonb_and_repository.sql
-- 1. Convert result_json from VARCHAR to jsonb for Simulation entity
ALTER TABLE simulations ALTER COLUMN result_json TYPE jsonb USING result_json::jsonb;
