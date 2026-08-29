package com.incokalk.model;

import java.time.LocalDateTime;

/**
 * Une ligne de la table taric_embeddings (V71) : un code HS + sa description +
 * le vecteur d'embedding correspondant (colonne pgvector native, jamais mappée
 * en JPA — voir TaricEmbeddingRepository, requêtes natives uniquement).
 * <p>
 * Simple porteur de données, pas une entité JPA : chaque opération sur cette
 * table passe par du SQL natif (l'opérateur pgvector "<=>" n'a pas
 * d'équivalent JPQL), donc rien à gagner à la faire gérer par Hibernate.
 */
public record TaricEmbedding(String hsCode, String description, LocalDateTime createdAt) {
}
