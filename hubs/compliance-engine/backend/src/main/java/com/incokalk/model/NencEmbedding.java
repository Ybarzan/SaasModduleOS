package com.incokalk.model;

import java.time.LocalDateTime;

/**
 * Une ligne de la table nenc_embeddings (V73) : un code de nomenclature
 * combinée + son texte explicatif officiel (NENC, UE) + le vecteur
 * d'embedding correspondant. Source et licence : voir
 * src/main/resources/data/SOURCES.md.
 * <p>
 * Simple porteur de données, pas une entité JPA — même raison que
 * TaricEmbedding : toute opération passe par du SQL natif (opérateur
 * pgvector "<=>" sans équivalent JPQL).
 */
public record NencEmbedding(String cnCode, String explanatoryText, LocalDateTime createdAt) {
}
