package com.incokalk.repository;

import com.incokalk.model.TaricEmbedding;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Accès à la table taric_embeddings (V71). Toutes les requêtes sont natives —
 * l'opérateur de distance cosinus de pgvector ("<=>") n'existe pas en JPQL.
 */
@Repository
@RequiredArgsConstructor
public class TaricEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Neighbor(String hsCode, String description, double cosineDistance) {
    }

    /** Codes HS déjà indexés — sert à l'ingestion incrémentale (ne ré-encoder que le manquant). */
    public Set<String> findIndexedHsCodes() {
        List<String> codes = jdbcTemplate.queryForList("SELECT hs_code FROM taric_embeddings", String.class);
        return Set.copyOf(codes);
    }

    public void upsert(String hsCode, String description, float[] embedding) {
        jdbcTemplate.update(
            """
            INSERT INTO taric_embeddings (hs_code, description, embedding)
            VALUES (?, ?, ?)
            ON CONFLICT (hs_code) DO UPDATE
                SET description = EXCLUDED.description, embedding = EXCLUDED.embedding
            """,
            ps -> {
                ps.setString(1, hsCode);
                ps.setString(2, description);
                ps.setObject(3, toPgVector(embedding));
            }
        );
    }

    /** Les N codes HS dont l'embedding est le plus proche (au sens cosinus) du vecteur donné. */
    public List<Neighbor> findNearest(float[] queryEmbedding, int topN) {
        PGvector queryVector = toPgVector(queryEmbedding);
        return jdbcTemplate.query(
            "SELECT hs_code, description, embedding <=> ? AS distance " +
            "FROM taric_embeddings ORDER BY distance ASC LIMIT ?",
            ps -> {
                ps.setObject(1, queryVector);
                ps.setInt(2, topN);
            },
            (rs, rowNum) -> new Neighbor(
                rs.getString("hs_code"),
                rs.getString("description"),
                rs.getDouble("distance")
            )
        );
    }

    private PGvector toPgVector(float[] values) {
        return new PGvector(values);
    }
}
