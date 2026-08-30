-- Recherche sémantique locale pour la classification HS code (Phase C).
-- Notes explicatives de la nomenclature combinée (NENC) de l'UE -- source et
-- licence documentees dans src/main/resources/data/SOURCES.md. Table globale
-- comme taric_embeddings (V71) : texte legal public, pas de scoping tenant.
CREATE TABLE nenc_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cn_code VARCHAR(12) NOT NULL,
    explanatory_text TEXT NOT NULL,
    embedding vector(384) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ~2935 codes distincts attendus -- toujours trop peu pour justifier un index
-- approximatif (voir la meme decision pour taric_embeddings en V71).
CREATE UNIQUE INDEX idx_nenc_embeddings_code ON nenc_embeddings(cn_code);
