-- Recherche sémantique locale pour la classification HS code (Phase A).
-- Le modèle paraphrase-multilingual-MiniLM-L12-v2 produit des vecteurs à 384
-- dimensions -- voir hubs/compliance-engine/embeddings-service/app.py.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE taric_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hs_code VARCHAR(12) NOT NULL,
    description VARCHAR(500) NOT NULL,
    embedding vector(384) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 475 codes distincts aujourd'hui : un scan exact via l'operateur "<=>" est
-- instantane a cette echelle. Un index approximatif (ivfflat/hnsw) n'apporterait
-- rien tant que cette table reste a quelques milliers de lignes -- a reconsiderer
-- seulement si son volume change d'ordre de grandeur (ex: Phase B, historique
-- par entreprise).
CREATE UNIQUE INDEX idx_taric_embeddings_hs_code ON taric_embeddings(hs_code);
