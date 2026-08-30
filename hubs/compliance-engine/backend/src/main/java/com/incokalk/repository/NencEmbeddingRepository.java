package com.incokalk.repository;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Accès à nenc_embeddings (V73) — notes explicatives de la nomenclature
 * combinée (NENC, UE). Même forme exacte que TaricEmbeddingRepository (V71) :
 * table globale, requêtes natives (l'opérateur pgvector "<=>" n'a pas
 * d'équivalent JPQL).
 */
@Repository
@RequiredArgsConstructor
public class NencEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Neighbor(String cnCode, String explanatoryText, double cosineDistance) {
    }

    public Set<String> findIndexedCodes() {
        List<String> codes = jdbcTemplate.queryForList("SELECT cn_code FROM nenc_embeddings", String.class);
        return Set.copyOf(codes);
    }

    public void upsert(String cnCode, String explanatoryText, float[] embedding) {
        jdbcTemplate.update(
            """
            INSERT INTO nenc_embeddings (cn_code, explanatory_text, embedding)
            VALUES (?, ?, ?)
            ON CONFLICT (cn_code) DO UPDATE
                SET explanatory_text = EXCLUDED.explanatory_text, embedding = EXCLUDED.embedding
            """,
            ps -> {
                ps.setString(1, cnCode);
                ps.setString(2, explanatoryText);
                ps.setObject(3, new PGvector(embedding));
            }
        );
    }

    public List<Neighbor> findNearest(float[] queryEmbedding, int topN) {
        return jdbcTemplate.query(
            "SELECT cn_code, explanatory_text, embedding <=> ? AS distance " +
            "FROM nenc_embeddings ORDER BY distance ASC LIMIT ?",
            ps -> {
                ps.setObject(1, new PGvector(queryEmbedding));
                ps.setInt(2, topN);
            },
            (rs, rowNum) -> new Neighbor(
                rs.getString("cn_code"),
                rs.getString("explanatory_text"),
                rs.getDouble("distance")
            )
        );
    }
}
