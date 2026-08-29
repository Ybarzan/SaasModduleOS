-- Recherche sémantique locale pour la classification HS code (Phase B).
-- Historique PAR ENTREPRISE, contrairement à taric_embeddings (V71, données
-- publiques partagées) et contrairement au modèle global de HsMlService
-- (bug connu, non corrigé ici : buildUserCorrectionModel() apprend sur
-- toutes les entreprises sans filtrer par company_id -- voir le commentaire
-- dans HsCodeSuggestionService.java). Cette table est scopee des sa creation
-- pour ne pas reproduire ce probleme.
CREATE TABLE company_hs_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    product_description VARCHAR(500) NOT NULL,
    hs_code VARCHAR(12) NOT NULL,
    embedding vector(384) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Une entreprise peut reconfirmer un code different pour le meme libelle
-- (elle change d'avis) : la derniere confirmation fait foi, pas d'historique
-- de versions ici.
CREATE UNIQUE INDEX idx_company_hs_embeddings_desc ON company_hs_embeddings(company_id, product_description);
CREATE INDEX idx_company_hs_embeddings_company ON company_hs_embeddings(company_id);
