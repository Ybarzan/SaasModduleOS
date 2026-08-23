-- V32: Fix accent-stripped search_text using PostgreSQL unaccent()
-- V24/V29 translate() had a length mismatch (21 src vs 23 repl), so accents weren't stripped.

CREATE EXTENSION IF NOT EXISTS unaccent;

UPDATE taric_rates SET search_text = lower(unaccent(description));
