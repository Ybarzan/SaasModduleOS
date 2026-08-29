package com.incokalk.repository;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Accès à company_hs_embeddings (V72) — historique de classifications
 * confirmées PAR ENTREPRISE. Toute requête ici filtre par company_id ; c'est
 * précisément le correctif du bug de HsMlService.buildUserCorrectionModel()
 * (modèle global non scopé) que cette table existe pour ne pas reproduire.
 */
@Repository
@RequiredArgsConstructor
public class CompanyHsEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Neighbor(String hsCode, String description, double cosineDistance) {
    }

    public void upsert(UUID companyId, String productDescription, String hsCode, float[] embedding) {
        jdbcTemplate.update(
            """
            INSERT INTO company_hs_embeddings (company_id, product_description, hs_code, embedding)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (company_id, product_description) DO UPDATE
                SET hs_code = EXCLUDED.hs_code, embedding = EXCLUDED.embedding, created_at = CURRENT_TIMESTAMP
            """,
            ps -> {
                ps.setObject(1, companyId);
                ps.setString(2, productDescription);
                ps.setString(3, hsCode);
                ps.setObject(4, new PGvector(embedding));
            }
        );
    }

    /** Les N codes les plus proches, jamais en dehors de l'entreprise donnée. */
    public List<Neighbor> findNearest(UUID companyId, float[] queryEmbedding, int topN) {
        return jdbcTemplate.query(
            "SELECT hs_code, product_description, embedding <=> ? AS distance " +
            "FROM company_hs_embeddings WHERE company_id = ? ORDER BY distance ASC LIMIT ?",
            ps -> {
                ps.setObject(1, new PGvector(queryEmbedding));
                ps.setObject(2, companyId);
                ps.setInt(3, topN);
            },
            (rs, rowNum) -> new Neighbor(
                rs.getString("hs_code"),
                rs.getString("product_description"),
                rs.getDouble("distance")
            )
        );
    }
}
