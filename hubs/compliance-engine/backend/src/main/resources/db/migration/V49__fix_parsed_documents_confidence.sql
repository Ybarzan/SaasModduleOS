-- V49__fix_parsed_documents_confidence.sql
-- Fix: parsed_documents.confidence DECIMAL(4,3) too small for parser confidence >= 10

ALTER TABLE parsed_documents
    ALTER COLUMN confidence TYPE DECIMAL(6,3);
